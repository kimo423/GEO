# GEO 1.2 upgrade: architecture review baseline

Status: DESIGN, not implementation approval. 2026-09-05.

## Revision 2: binding implementation contracts after A review

These concrete rules supersede any ambiguous shorthand below. Review findings are
adjudicated, not blindly accepted (a suggested DB-before-file rename is unsafe).

1. Portable blobUuid and local internalStorageKey are DISTINCT. Every imported
   file receives a NEW random storageKey unrelated to its blobUuid, promoted before
   DB commit. Existing target files are NEVER silently skipped or overwritten.
   Full replace uses only newly staged keys. Same-record may reuse a DB-referenced
   blobUuid only if metadata AND actual local bytes match; otherwise reject whole
   import. Failed promotion -> no DB commit. DB-before-rename is prohibited because
   process death would leave dangling live references. This resolves A-C1 without
   adopting its unsafe remedy step 6. A-C1's hypothesized use of blobUuid as path
   was not intended by the original random-UUID key design.
2. Migration SQL order: ADD transaction_uuid TEXT NOT NULL DEFAULT ''; ADD
   is_deleted INTEGER NOT NULL DEFAULT 0; ADD deleted_at_millis INTEGER; cursor
   over IDs assigns a separate UUID.randomUUID().toString() using bound UPDATE;
   assert no blank, count equals distinct count; then create UNIQUE UUID index.
   Room column defaultValue="''" agrees with SQL, but every entity factory supplies
   a new UUID. Keep all existing IDs/indexes/autoincrement; no table recreation
   needed. Migration failures throw/rollback. Tests cover 3->4 and 1->4.
3. UUID validation is exactly UUID.fromString(s).toString()==s; lowercase canonical
   8-4-4-4-12 only, reject noncanonical input (never alias identities).
4. Existing observeAllOrdered/getAllOrdered become active-only SQL. Add named
   getAllIncludingDeleted for restore/export. Remove user deleteById entirely.
   Keep getById for internal snapshot reading but repository getTransaction/edit
   rejects tombstones; delete already deleted is no-op. Calculator also filters
   tombstones defensively. All new FKs RESTRICT, never cascade.
5. Same-record: intersect transaction UUIDs at commit; ignore unmatched transaction
   and its exclusive events/attachments/blobs. Before snapshot from commit-time DB.
   Deactivate previous current relations; preserve all immutable reference metadata.
   Existing attachmentUuid must retain owner, blob, original name, size/hash/MIME;
   different identity content is corruption (not upsert). New references insert.
   Imported current order/state applied separately. Existing blobUuid differing
   content or same eventUuid differing canonical event payload -> entire rollback.
   Union immutable historical events; generate IMPORT EDIT only on business change;
   no-op may still import missing history. Revalidate merged active ledger overflow.
6. Full replacement metadata order inside ONE transaction: clear audit, relations,
   transactions, blobs; insert blobs, transactions, relations, events. Do not change
   options/FK enforcement. Export transaction array strictly date ASC, created ASC,
   id ASC including tombstones; restore sequential IDs in array order. Same-record
   preserves local IDs. client_op_key never portable; full restore null, matching
   restore retains local. Option IDs remapped by unambiguous normalized snapshot,
   missing becomes null, snapshot text NEVER lost or regenerated.
7. Deleted transactions have no current attachment references; delete captures
   before then deactivates their relations. Their audit attachments export only
   history_only. Active transactions use current or history_only as appropriate.
   Attachment sort order is snapshot-specific; unique current order validated in
   repository (historical snapshots can use different order).
8. A-M8 chronological last-event equality is NOT a valid invariant: same-record
   unions two devices' event histories and wall clocks may skew; an imported
   event can be later-dated than the new local IMPORT event. Enforcing last by
   occurredAt would reject our own legitimate roundtrip. Validate each event type,
   UUID ownership, before/after fields and attachment integrity; never infer a
   cryptographic linear chain from timestamps. transactions.json is authoritative
   current state. Add manifest currentSnapshotHashes index keyed transactionUuid
   to bind current records to manifest integrity. Document that audit is immutable
   application history, not proof of clock order or authentication of external ZIP.
9. Config full replace deactivates all active first then reuse/insert; same-name
   updates display/sort/active flags only for existing normalized matches; refuse
   ambiguous names. No rewriting historical snapshots. Unknown archive entries
   rejected. Safe folder amounts are locale-independent integer cents with two
   decimals. FileProvider only blobs; selected files may be promoted with random
   key for editor preview, cancellation can leave harmless unreferenced blobs.
   Android backup exclusions cover database, blobs and staging for both transports.

Host Robolectric Room smoke test has now executed successfully; device tests are
still separate. Worktree is no longer clean because upgrade work is in progress.

### Revision 3: B review adjudication

- B-C1/C2: revision2 points 2/6 give exact migration defaults matching Room
  @ColumnInfo(defaultValue), plus FK-safe wipe order. Table rebuild is not required
  when actual SQL and exported defaults MATCH; runtime migration will verify it.
- B-H1: creation idempotency replay returns existing ID even if tombstoned, without
  resurrection or new event; explicit edit of tombstone fails. Portable data never
  carries client_op_key. Full restore clears keys, matching keeps local keys.
- B-H2: stable export array fixed; preserve imported createdAtMillis (it is the
  recorded-time business field, user requires full restore of timestamps), matching
  retains local id. Reject overflow of merged result. Do not silently keep local
  createdAt while claiming imported business state has replaced it.
- B-H3: limits apply per CURRENT set and EACH historical snapshot independently,
  never the union of historical versions. Multi-select exceeding quota rejects
  the entire selection with precise feedback; do not partially change DB.
- B-H4/H5: revision2 point 5 specifies matching closure and immutable identity checks.
- B-H6: option mapping only same-type active local names. Ignore inactive duplicates;
  0 match -> null local ID/preserve exact snapshot; >1 normalized active -> abort.
- B-H7: enumerate ZIP before extraction; exact-name whitelist three root JSON files
  plus manifest attachment paths strictly under attachments/. Canonical and
  case-fold duplicate entry names rejected for cross-platform safety. No symlink
  extraction (all output is generated random filenames); only STORE/DEFLATE;
  unsupported/encrypted ZIPs fail. Folder entries allowed only canonical parents
  of referenced attachments and never trusted as identities. Extra entries fail.
- B-H8: stream loop stops as soon as actual bytes > min(single cap,remaining total),
  metadata alone never rejects valid bytes. Track per-operation new storageKeys;
  on catch/cancel cleanup only these keys proven unreferenced in DB under shared
  lock with NonCancellable context. This also handles cancellation observed AFTER
  successful DB commit: referenced files must survive! Process crash may leave
  orphans, but caught rollback does not accumulate them. No sweeping live blobs.
- B-M1/M2/M4/M6/M7/M8: stable audit time+UUID tie key, contiguous snapshot sort order,
  integer two-decimal names, separate active/tombstone reads, strict required fields
  with unknown fields ignored, LocalDate validation plus app picker bounds, explicit
  normalized-name ambiguity error, lock/cancel/TOCTOU contracts as above.
- B-M3: launch ACTION_VIEW directly and catch ActivityNotFoundException (no package
  pre-query means queries permission is not needed); decoded images capped to
  2048px per dimension / 4 million pixels using bounds pass + sampling on IO.
- B-M5: export to complete private staging ZIP first, then copy to SAF; failed SAF
  copy reports failure and attempts DocumentsContract.deleteDocument of just-created
  destination, without deleting any other selected document. Startup cleans only
  stale staging session files, never active session or blobs. No background network.
- Compression-ratio rejection must not reject GEO's OWN valid exports: write blob
  ZIP entries STORED with precomputed CRC32; JSON DEFLATED. Include zero-filled
  10MiB attachment export/import regression. Sanitize Windows reserved basenames.

All these are implementation contracts, not claimed verification passes.

## Baseline and scope

Only project: `C:\Users\SUN\Desktop\GEO`. Existing origin is
https://github.com/kimo423/GEO.git. Starting branch main, clean. App 1.1.3/code 5,
applicationId com.geo.ledger; Room version 3, exported schemas 1,2,3. Preserve
original GEO-icon-original.png, all ledger calculation and option snapshot rules.
Target app 1.2.0/code 6, DB 4. No push/release. Existing dist is OLD until rebuild.
ADB currently has no device. Instrumentation execution is not yet available.
Primary code writer: Codex. Grok A/B independently review, not implement.

## Schema and migration

MIGRATION_3_4 adds non-null transaction_uuid (unique index), is_deleted default 0,
deleted_at_millis nullable. Existing rows receive individual java UUID.randomUUID()
inside migration, never regenerate later; retain IDs, dates, cents, snapshots,
client operation keys and ordering. Keep explicit 1->2->3->4 chain and export 4.json.
No destructive fallback. DAO normal observation and calculator inputs exclude
deleted rows. Backup DAO reads all rows. Delete is update+DELETE event atomically.

New tables: attachment_blobs(blobUuid PK, sha256, sizeBytes, mimeType,
internalStorageKey UNIQUE, createdAtMillis); transaction_attachments(attachmentUuid
PK, transactionUuid FK RESTRICT, blobUuid FK RESTRICT, originalFileName,
sortOrder, isActive, createdAtMillis, removedAtMillis); audit_events(eventUuid PK,
transactionUuid FK RESTRICT, eventType EDIT/DELETE, source USER/IMPORT,
occurredAtMillis, schemaVersion=1, beforeSnapshotJson, afterSnapshotJson nullable).
Audit DAO has insert/read only, no individual update/delete API. Dataset restore
may clear all inside one transaction; this is explicitly not user history editing.
Snapshots contain full portable business state and ordered attachment identities,
blob UUID/hash/size/MIME/name. They never depend on mutable option names or paths.
Database immutability is application-level, not cryptographic tamper evidence.

## Blob lifecycle and transaction boundaries

SAF sources -> bounded streaming copy to filesDir/staging/<session UUID>/ ->
sha256 and actual-byte validation -> durable filesDir/blobs/<random UUID>.
Limits: <=10 attachments, each <=10485760 bytes, sum <=31457280 bytes. Check
metadata only as hint, enforce real byte count while copying. Never hold file in
RAM. IO and hashes on Dispatchers.IO. No URI is retained as authoritative content.
File identities are generated internally; user names display only. FileProvider
exposes only blobs/ with temporary read grants; save uses CreateDocument. Image
preview uses bounded decoding; PDF/other uses ACTION_VIEW with no-handler feedback.

Promote new blobs before DB commit (flush/sync each file); never overwrite a
previous blob file. DB transaction inserts blob metadata, relations and transaction
and event together. Rollback leaves at most unreferenced files, never missing
referenced files. A process crash before commit can leave harmless orphans. Do not
auto-delete blobs in this release: retention is intentionally conservative. Full
replacement also retains old physical orphans until a later explicit verified GC.
Clean session staging on cancellation/error; exclude backup/staging from Android
backup. Never clean blobs during startup or delete a user-selected external file.

Serialize repository mutations with a shared Mutex, and use Room transactions.
Export reads a consistent DB snapshot under the same lock before writing files;
blob files are immutable so streaming can safely continue afterward. Recheck
same-record matching and audit conflicts at commit, not only preview. Busy UI
prevents repeat actions; cancellation must release busy state and cleanup staging.

## Audit and edits

Before snapshot is read within DB transaction, not from stale UI. Compare normalized
business fields + ordered attachment references; exclude updatedAt/client operation
key from no-op comparison. An unchanged save writes no EDIT. Each changed save
inserts one before/after EDIT alongside update. Keep inactive historical relations
and all audit-referenced blobs. DELETE has before and null after, no repeated event
when already deleted. Updates reject deleted records. UUID never changes. Import
state transitions including tombstone restore use source IMPORT EDIT with explicit
isDeleted before/after, preventing silent loss of a delete state.

## Configuration

.geocfg UTF8 JSON GEO_CONFIG/formatVersion=1, export metadata and persons/categories
id (origin-local hint only), name, sortOrder, isActive. Normalize name NFC+trim+
ASCII lowercase for matching only, same type only. Reject duplicate normalized
names in package (and ambiguous local matches). Full replace deactivates old active
options and reuses matching local names or inserts imported active options; never
delete historical dependencies. Same-name replaces matching active local names
only, ignores imported unmatched, preserves local unmatched. Imported IDs do not
overwrite local identities. Validate names/order/limits before mutation.

## GEO_DATA format 1

.geodata ZIP: manifest.json, transactions.json, audit_logs.json and optional
attachments/<YYYY-MM-DD_收入|支出_amount_label_UUID8>/current|history_only/name.ext.
Only transactions with current or audit-referenced attachments get folders.
Sanitize invalid/control characters, trailing dots/spaces, limit readable stem;
UUID8 retained. Resolve even UUID8 prefix collisions by extra UUID characters.
Collision-safe attachment filenames keep extension and original name in manifest.
One blob is written once, all references use the same archivePath; first owner
folder canonicalizes cross-transaction shared blobs. No local absolute paths.

Manifest: format/version/exportedAt/app versions/counts, transaction identity index,
attachment identity index with transactionUuid/attachmentUuid/blobUuid/name/MIME/
actual size/hash/archivePath, SHA256 of transaction/audit JSON. Transactions include
all active/deleted records, full stable UUID state and current attachments. Local
numeric option IDs are never blindly reused on another device; map by normalized
snapshot name if unambiguous, otherwise null with preserved snapshot. Original
snapshot identity metadata remains portable. Export contains entire audit history.

## Import safety

Stage whole input to bounded disk file; ZIP enumeration/extraction never touches
live DB. Hard caps: archive <=1 GiB, total expanded <=2 GiB, entries <=100000,
each JSON <=32 MiB, each attachment <=10 MiB; reject excessive compression ratios
(>200 after 1 MiB), duplicate names, directories/paths with absolute, drive, dot or
dot-dot components, backslashes, NUL, suspicious canonical escape, unsupported
entries. These limits are explicit format implementation limits, not hidden claims
of unlimited storage. Check available storage conservatively; actual IO failures
abort safely. Streaming counts enforce actual sizes independent of ZIP headers.

Require exactly one of each critical JSON, known format/version, strict typed
fields, valid canonical UUIDs, unique transaction/attachment/event identities,
valid enum/date/positive cents/deletion structure, snapshot limits and identities,
complete blob references. Duplicate event UUID with differing content is corruption,
also when colliding with local history. Verify actual bytes, size, SHA256 and JSON
hashes. Reject same blob UUID with inconsistent metadata, orphan manifest entries,
and conflicting attachment ownership. Validate all current AND historical snapshot
attachment limits. Validate active full-history balance/aggregate overflow before
commit. Full replace preserves exported relative ordering (rebuild IDs in export
order); same-record keeps local IDs and revalidates merged balance.

Only after full validation show summary and mode. Full replacement has strong
confirmation and atomically replaces transaction/blob/relation/event metadata,
not person/category settings; no generated delete events. Same-record uses UUID
only: local A,B plus imported B,C -> A retained, B replaced, C ignored. Import
needed matching history/blobs, eventUuid dedup (content must match), generate
IMPORT EDIT only if business content changed. Old local history retained. Promote
all files first, then one Room transaction, so any failed import keeps previous DB
and referenced files untouched. Do not overwrite old blob UUID storage collisions;
reuse only after content validation. Cancel preview discards staging.

## UI and network

Preserve smooth navigation (no dual screen fading/expensive work on main). Shared
warm-white/graphite/silver/blue-gray tokens, restrained teal/red amounts. Attachments
editor after note, count/total, preview/remove; paperclip/count Home/Bills. Detail
sections amount/info/note/attachments/actions. Audit one event = one outer card,
before -> after within card, changed chips and historical attachment actions.
Settings groups accounting/data/about. SAF import validates before mode choice;
progress and failure/success feedback, mutation back guard.

Remove startup auto-check. Only Settings manual action calls existing GitHub origin
Releases latest stable API with timeout/HTTP/error handling; parse strict SemVer
(optional v, numeric components, prerelease rejected). Prefer APK asset else release
page; HTTPS GitHub URLs only, browser launch failure feedback. Existing INTERNET
permission only, no analytics/upload/storage/install permissions.

## Verification plan

Keep all existing tests. Add pure JVM tests for limits, naming/path validation,
audit/no-op comparison, formats, SemVer, corruption and archive roundtrip. Add
Room migration/integration tests (v3 fixtures with incomes/expenses/options, unique
UUIDs, exact totals), edit continuity, attachments A,B->A,C, delete history, config
strict match and data A,B/B,C, rollback/conflicts. Try Robolectric to execute Room
tests without device if supported; otherwise report unexecuted gates honestly.
Run lint/test/assembleDebug and compile androidTests. Run connected UI if device
available. Architecture A/B -> fix design -> core/UI -> A/B round2 -> fixes/tests ->
assemble -> A/B final fresh review. No old report is evidence for this release.
