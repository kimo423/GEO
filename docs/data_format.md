# GEO portable data formats — version 1

Implementation target: GEO 1.2.0/code 6, Room 4. Formats are independent of Room
schema versions. Unknown formatVersion is rejected, not guessed. Required fields
have strict types; duplicate JSON keys, fractional integers, overflow and trailing
content are rejected. Extra JSON fields are ignored for compatible metadata growth.
UTF-8, UTC Instant export timestamps, integer Long cents (never float).

## .geocfg

Plain JSON. `format=GEO_CONFIG`, `formatVersion=1`, `exportedAt`, appVersionName,
appVersionCode, `persons`, `expenseCategories`. Each option: id (origin-local hint),
name (trimmed, 1–40 characters), sortOrder (nonnegative integer), isActive (boolean).
Export includes current active options. Numeric IDs never replace local identities.

Name matching is same type only, NFC + trim + ASCII case folding. Display uses the
imported name. Duplicate normalized imported names or ambiguous ACTIVE local names
cause rejection. Inactive historical duplicates are ignored for matching.

- Full replace: deactivate current options, reuse matching current identities or
  insert imported options; active result follows package. Preserve historical
  rows/snapshots. No transaction/audit is altered.
- Same-name replace: change only already-existing matching active options, retain
  local unmatched, IGNORE imported unmatched. Never merge-and-add.

## .geodata

ZIP container, not Base64 JSON. Required exact root filenames:

```
manifest.json
transactions.json
audit_logs.json
attachments/2026-09-05_支出_128.00_耗材_a31f82c4/current/发票.pdf
attachments/2026-09-05_支出_128.00_耗材_a31f82c4/history_only/旧发票.pdf
```

Directories need not have explicit ZIP entries. No attachment current/history
references => no folder. Folder is readable information only; NEVER an identity.
Folder: ISO date, 收入/支出, cents rendered as integer decimal with exactly two
digits, optional category/person for expense or source for income, UUID first 8.
Invalid Windows/control/path characters become `_`; readable label <=32 chars,
trailing dots/spaces removed, UUID suffix retained. UUID8 collisions extend UUID.
Original filenames are preserved in manifest; sanitized archive filenames are
collision-suffixed, reserved basenames prefixed. Emoji/Chinese allowed.

`current/`: active attachments of active transaction. `history_only/`: references
only in audit, including deletion snapshots. Blob UUID saved ONCE per archive;
multiple attachment/current/history references may share archivePath. If a blob
is shared across transactions its first owner folder is canonical. No duplicated
bytes solely for before/after. Blob ZIP entries use STORE, JSON DEFLATE, so valid
highly compressible attachments do not trip import compression-ratio heuristics.

### manifest.json

format=GEO_DATA, formatVersion=1, exportedAt, appVersionName/appVersionCode,
transactionCount (including tombstones), auditEventCount, attachmentCount (unique
attachment identities including history), transactionUuids (ordered index),
transactionsSha256, auditLogsSha256, currentSnapshotHashes (UUID -> SHA-256 of
canonical TransactionSnapshot JSON UTF-8), attachments array.

Every attachment index item: transactionUuid, attachmentUuid, blobUuid,
originalFileName, mimeType, sizeBytes, sha256 (64 lowercase hex), sortOrder (one
snapshot order; authoritative order lives in each snapshot), archivePath.
Internal filesystem keys/absolute paths never exported. Hashes detect corruption,
NOT malicious authorship: anyone modifying all JSON/hashes can create a package.

### transactions.json

Array sorted transactionDate ASC, createdAtMillis ASC, original local id ASC,
including logically deleted transactions. Each object:

| Field | Meaning |
|---|---|
| transactionUuid | permanent canonical lowercase UUID, never a numeric local id |
| type | INCOME or EXPENSE |
| amountCents | positive Long cents |
| transactionDate | LocalDate epoch-day |
| createdAtMillis / updatedAtMillis | recorded timestamp and last mutation, epoch ms |
| isDeleted / deletedAtMillis | tombstone boolean, nullable deletion epoch ms |
| expensePersonId / expenseCategoryId | origin-local nullable hints, remapped on import |
| expensePersonSnapshot / expenseCategorySnapshot | historical exact names, retained independently |
| incomeSource / note | nullable source <=120 chars, note <=1000 chars |
| attachments | ordered current AttachmentRef array, empty for tombstones |

AttachmentRef: attachmentUuid, blobUuid, originalFileName, mimeType, sizeBytes,
sha256, sortOrder contiguous 0..n-1. Limits apply to EACH snapshot independently:
0–10 entries; each <=10,485,760 bytes; sum <=31,457,280 bytes. Historical union may
contain more than ten blobs. Same attachmentUuid always keeps owner/content/name;
order may differ across snapshots. Source client_op_key is intentionally absent.

### audit_logs.json

Array: eventUuid, transactionUuid, eventType EDIT/DELETE, source USER/IMPORT,
occurredAtMillis, schemaVersion=1, beforeSnapshot (full above object), afterSnapshot
(full object for EDIT; null for DELETE). Append-only through normal application
operations; no individual edit/delete UI or DAO. Full dataset restore may replace
history as a whole. No-op saves add no event. Delete captures before and deactivates
current relations in one DB transaction. Removed historical blobs are retained.

Each EDIT is displayed as ONE outer UI card containing before -> after. Changes
compare normalized business fields and ordered attachment identities, excluding
updated timestamp and device-local option IDs. History union across devices can
branch or have skewed clocks; latest wall-clock event is NOT required to equal
current state. This is application audit history, not cryptographic nonrepudiation.

## Restore modes and atomicity

Full replace: after strong confirmation replace transactions, attachment metadata
and audit in one Room transaction. Do NOT replace person/category settings; do NOT
generate mass DELETE events. New local IDs follow exported array order, preserving
tie ordering of running balances; UUIDs/timestamps remain. client_op_key=null.

Same-record: intersect imported and current local transactionUuid at commit. Local
A,B + imported B,C => A stays, B replaced, C and its exclusive children ignored.
Keep local numeric id and idempotency key; imported timestamps/business state win.
Replace B's current attachment set while preserving old history/blobs. Union audit
events by eventUuid; unequal canonical payload for same eventUuid aborts import.
Changed B gets IMPORT EDIT before/after; unchanged B gets no extra event.

Portable option IDs never attach to unrelated local options. Map by same-type
ACTIVE normalized snapshot name; no match => local id null, snapshot stays intact.
Ambiguous active match => fail. Both modes validate merged Long balance/aggregate
overflow before commit. Deleted records are excluded from ledger and summaries.

Validation stages entire input privately and verifies ALL structure, references,
UUIDs, fields, limits, actual streamed bytes, SHA256, JSON hashes and ZIP safety
before displaying summary. No live Room writes during parsing. On apply, recheck
local identity/event conflicts; promote files to NEW random internalStorageKeys
BEFORE the single DB transaction. Portable blobUuid is NOT a disk filename.
Never overwrite an existing file. On failure/cancellation clean only this operation's
new keys proven unreferenced after querying actual committed DB state. Process death
may leave harmless orphans, never justify deleting live/historical blobs. Full
replacement retains old orphan bytes conservatively; no automatic blob GC in 1.2.

FKs stay enabled RESTRICT. Full restore clear order: audit -> relations ->
transactions -> blobs; insertion: blobs -> transactions -> relations -> audit.

## ZIP and resource limits

- Compressed input <=1 GiB, expanded <=2 GiB, <=100,000 entries.
- Each root JSON <=32 MiB, <=20,000 transactions, <=50,000 events; config <=2 MiB.
- JSON depth <=48 / <=500,000 nodes; bounded buffers, no attachment loaded in RAM.
- Enumeration checks paths, exact and case-fold duplicates, method, declared
  totals/storage before extraction. Extract only root JSON and manifest whitelist.
- Reject absolute/drive/backslash/control/dot/dot-dot paths, extra files, redundant
  directories, dangerous canonical escape, missing entries, unsupported formats,
  encrypted/unsupported ZIP streams, excessive ratio (>200 above 1 MiB).
- Do not materialize symlinks or user-named filesystem paths: copy verified entry
  bytes into generated private filenames. Real size checked during streaming.
- Corrupt metadata/hash/content or UUID ownership conflicts fail before mutation.

Future versions must preserve version-1 readers/tests or add explicit migrations.
Do not silently reinterpret identities or add merge semantics to matching mode.

## Local option compatibility and validation follow-up

New UI/repository option writes use the same NFC+trim+ASCII-case-fold comparison
as imports. Existing Room exact-name index keys and historical display values are
preserved. Legacy ambiguous active names must be renamed/deactivated by the user;
imports reject them safely rather than silently merging data. Both import formats
reject malformed UTF-8. Replacing a validated preview closes its prior staging root.
Attachment error labels use the requested Chinese MB wording; byte limits remain
binary MiB as specified above, not decimal MB.
