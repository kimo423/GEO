# Round2 adjudication and fixes — 2026-09-09

Actual independent Grok reports: upgrade_round2_a.md and upgrade_round2_b.md.
Old terminal sessions are no longer available; new focused verification will record
normal exit explicitly. Reports are not phone tests. Codex alone edits production.

| Finding | Disposition |
|---|---|
| A M1 stale README/build/known issues | Accepted; removed obsolete current claims and explicitly archived old evidence; final counts pending fresh build |
| B H1 restored active creation draft lost | Accepted; active key replay edits, identical draft no-op, tombstone cannot resurrect; real Room test A,B -> A,C |
| A m1 / B M2 limit messages | Accepted; three exact Chinese messages; stream errors distinguish per-file vs remaining total |
| A m2 attachment load failure | Accepted; already fixed during review: visible error + disabled save |
| A m3 / B M3 normalized uniqueness | Accepted for new writes; UI/repository same normalization as import; legacy DB keys/display names kept; old ambiguity rejected, not merged |
| A m4 paperclip label | Accepted; no-space Chinese semantics |
| A m5 legacy updater | Compatibility adapter retained, no navigation/startup path; documented and source-tested |
| A m6 spacing tokens | Accepted; shared Page used in Home/Bills/Add/Detail/Settings |
| B M1 ZIP prefix | Not reproduced: ZipEntry.isDirectory includes slash, Python probe omits it; 'attach/' cannot prefix 'attachments/'. Hardened to explicit parent set and added real ZIP rejection tests |
| B M4 preview leak | Accepted; replace closes previous package, clears other kind; cancellation closes unowned parsed package |
| B M5 invalid UTF-8 | Accepted; strict REPORT decoder + malformed-byte test |
| B L1 empty files | Allowed by size limits; not a safety defect |
| B L2 folder fallback | Full UUID unique by invariant; now registers fallback and asserts as defense in depth |
| B L3 unreadable message | Accepted; null/IO/security failures get required Chinese message |
| B L4 post-commit cancellation | Data/files retained; don't show UI after owner destruction. Device process interruption check pending |
| B L5 network wording | Accepted; connect/TLS failures mapped to actionable Chinese |

Deduplicated accepted Round2 issues: 11, including fixes during review; all have
code/doc changes. Architecture comments were design work, not production bugs.
Current reviewers must independently verify this adjudication.

## Round3 follow-up

Actual final Grok A and B both exited 0. A C0/Major0/Minor0; B C0/H0/M0/L1.
B identified a narrow post-commit cancellation window between clearing the preview
reference and closing its staging root. Accepted as a real low-severity cleanup bug
(not loss of committed data). Codex changed apply cleanup to NonCancellable+IO and
extended the actual Settings ViewModel test to check successful apply frees staging.
The post-fix full suite/APK and focused A/B verification supersede the initial
Round3 fingerprints, while original reports remain auditable. Cumulative accepted
implementation issues: 12; fixed: 12. Post-fix A and B both PASS with zero remaining
findings in their respective severity categories. Each run exited normally (0).
The earlier max-turns failure remains failed, not retroactively promoted to a pass.
