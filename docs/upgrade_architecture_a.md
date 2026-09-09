# GEO 1.2 Round1 架构审查（Grok Agent A）

- 角色：GEO Implementation Reviewer
- 范围：仅 `C:\Users\SUN\Desktop\GEO`
- 对照：用户需求 §0–§120；设计 `docs/upgrade_1.2_design.md`（2026-09-05，**DESIGN，非实现批准**）
- 基线代码（只读）：`Entities.kt` / `GeoDao.kt` / `GeoDatabase.kt` / `LedgerRepository.kt` / `schemas/3.json` 及现有测试源
- 本轮性质：**设计审查**。未实现功能不记为缺陷。未执行任何运行时测试、connected 测试或 assemble。
- 主代码写入者：Codex。本文件为唯一允许写入物。

## 设计门禁

**REVISE**

Critical = 1；Major = 8；Minor = 7。按 §106，存在 Critical/Major 时不得进入大规模 UI。先修订设计并经 Codex 落成数据层契约后再实现。

## 基线（只读确认）

| 项 | 现状 |
|---|---|
| 路径 | `C:\Users\SUN\Desktop\GEO` |
| Git | `main`，origin `https://github.com/kimo423/GEO.git`；工作区非完全干净（`app/build.gradle.kts` 已加 Robolectric 依赖；与设计文“clean”不完全一致，不记设计缺陷） |
| 应用 | `com.geo.ledger`，1.1.3 / versionCode 5 |
| Room | version 3，`exportSchema=true`，已有 1.json/2.json/3.json；`addMigrations(MIGRATION_1_2, MIGRATION_2_3)`；**无** `fallbackToDestructiveMigration` |
| 交易 | Long PK `id`；无 UUID / 无 `is_deleted`；`deleteById` 物理删除 |
| 选项 | Person/Category `is_active` + 可空 `active_name_key` 唯一；名称校验仅为 trim，区分 ASCII 大小写，无 NFC |
| 余额 | `transactionDate ASC, createdAtMillis ASC, id ASC`；Long cents；`Math.addExact` |
| 附件/审计 | 不存在 |
| 更新 | `AppUpdateGate()` 启动即拉 `version.json` + 比较 versionCode（与 §72/§74 目标相反；设计已要求拆除，属实现门，不记缺陷） |
| 备份 XML | 仅 exclude database；`allowBackup=false` |
| 图标 | `GEO-icon-original.png` 存在 |
| ADB | 设计已声明无设备 |

现有测试源已钉死 v3（`BackupAndPermissionManifestTest`、`ActiveOptionNameKeyTest`、migration instrumented 仅 1→2→3）。升级时必须**修订断言到 v4**，不能为“保留旧测试”而停在 v3。

## 设计已对齐且应保留

下列方向符合需求，修订时不要推翻：

- 显式 `MIGRATION_3_4`，目标 app 1.2.0/code 6、DB 4，导出 `4.json`，保留 1→2→3→4，禁止 destructive fallback（§4、§5、§95）。
- 每行独立 `UUID.randomUUID()`，之后永不重算；不按日期/金额/人员推断身份（§5）。
- 逻辑删除 + 正常观察/计算器排除已删；备份/导出读全量；删除与 DELETE 事件同一 Room 事务（§6、§29、§89）。
- Blob / 关系拆表；历史关系 `isActive=false` 保留；本版不做激进 GC（§8、§21、§22）。
- 流式复制、真实 byte 计数、`10*1024*1024` / `30*1024*1024` / 10 个；不信任 SIZE metadata；不把文件读进 RAM（§7、§10）。
- 先晋升已校验文件，再 DB 提交：回滚最多孤儿文件，从不出现“库有引用、盘上无文件”（§16、§63）。
- Audit 仅 insert/read；用户不可改/删/清空；全量替换是 dataset restore，不生成海量 DELETE（§23–§24、§36、§62）。
- Before 在 DB 事务内读取；无变化不写 EDIT；连续编辑连续事件；tombstone 恢复用 IMPORT EDIT 显式 `isDeleted`（§27–§28、§60）。
- 配置/数据两种模式语义与示例 A,B / B,C 一致；导入 ID 不覆盖本地身份（§40、§58、§101）。
- ZIP 先整包落到 staging、枚举不碰 live DB；Zip Slip、压缩比、硬顶；失败保持正式库（§63–§65、§102）。
- 仓库 Mutex + Room 事务；导出先在锁内拍 DB 快照，blob 不可变后再流式写 ZIP（§87）。
- 去掉启动自动检查；仅设置页打 GitHub origin Releases；沿用 `kimo423/GEO`，不另造仓库（§72–§74）。

---

## Critical

### C1. 全量替换/导入“先晋升到 live `blobs/`”会与本地文件撞车，成功导入后磁盘内容可与清单哈希不一致

- 需求：§16、§22、§51、§57、§63 步骤 23、§102、§118（Import Failure 不污染；SHA-256 正确）
- 设计原文：晋升到 `filesDir/blobs/<random UUID>`；“never overwrite a previous blob file”；“Promote all files first, then one Room transaction”；“Do not overwrite old blob UUID storage collisions; reuse only after content validation”；全量替换仍保留旧物理孤儿。
- 问题：未规定 **portable `blobUuid` ≠ 磁盘 `internalStorageKey`**。若实现把导入文件写到 `blobs/<blobUuid>`：
  1. 本地已有同路径文件（旧 blob 或全量替换即将废弃的孤儿）；
  2. 包内同 UUID、**不同内容**（恶意包、两机偶然碰撞、上次失败残留）；
  3. 设计禁止覆盖 → 跳过写入；
  4. Room 事务仍写入包内 sha256/size；
  5. 导入报告成功，打开附件却是旧字节。这是静默坏账凭证，比导入失败更糟。
- 全量替换尤其危险：DB 行将被清空，但磁盘文件先于事务存在，**无法用“与本地 DB metadata 比较”抓住冲突**。
- 补救（必须写进设计，作为实现契约）：
  1. `internalStorageKey` **永远是新的随机文件名**，禁止用 `blobUuid` 当路径。
  2. 导入晋升目标为 `filesDir/staging/<importSession>/blobs/<newKey>`，**禁止**直接写 live `blobs/<existing>`。
  3. 若 live 已有相同 `internalStorageKey`：换新 key，永不覆盖。
  4. 仅当本地 **DB 仍引用** 同一 `blobUuid` 且 **磁盘哈希与包内完全一致** 时才复用；哈希不一致 → **整次导入失败**，不得“跳过写入当成功”。
  5. 全量替换不复用旧磁盘文件：旧 key 全部当孤儿留下；新行只用本次 staging key。
  6. DB 事务成功后再把 staging blob **link/rename** 进 `blobs/`；事务失败则删本次 staging，live DB 与其引用文件不变。
  7. 提交后抽查：每个新引用的 `internalStorageKey` 存在，size/sha256 与行一致。

---

## Major

### M1. `MIGRATION_3_4` 缺少可执行的 SQL 顺序，存在“全表同一 UUID”或迁移失败后被诱导 destructive 的路径

- 需求：§4、§5、§95
- SQLite 不能一步 `ADD COLUMN transaction_uuid TEXT NOT NULL UNIQUE` 并为每行填不同值。若 `DEFAULT` 同一 UUID 再立刻建唯一索引，多行库直接失败；若失败后有人加 `fallbackToDestructiveMigration()`，则 §4 被击穿。
- 补救：设计写死顺序：
  1. `ALTER TABLE transactions ADD COLUMN transaction_uuid TEXT;`（可空）
  2. `ALTER ... is_deleted INTEGER NOT NULL DEFAULT 0;`
  3. `ALTER ... deleted_at_millis INTEGER;`（可空）
  4. Cursor 逐行 `java.util.UUID.randomUUID()`，写成规范小写；禁止 `hex(randomblob(16))` 同一表达式更新全表。
  5. `SELECT COUNT(*) = COUNT(DISTINCT transaction_uuid)` 且无 NULL；失败则抛错让 Room **回滚迁移**。
  6. 再建 `UNIQUE INDEX index_transactions_transaction_uuid`。
  7. 若 Room `4.json` 要求 NOT NULL：用新表拷贝（保留 `id`、日期、cents、snapshot、`client_op_key`、全部索引），`INSERT ... SELECT` 后 `DROP`/`RENAME`，禁止重置 AUTOINCREMENT 打乱 id 次序。
  8. `addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`；测试覆盖 1→4 与 3→4 两链；禁止 destructive。

### M2. UUID 未规定规范形式；SQLite UNIQUE 大小写敏感，同记录替换会认成两条

- 需求：§5、§58、§63（valid canonical UUIDs）
- 设计只说“canonical UUIDs”，未定义存储/比较式。`A` 与 `a` 可并存，same-record 匹配失败，甚至把本应替换的 B 当成 C 忽略。
- 补救：全库、导出、导入统一 RFC 4122 **小写** `8-4-4-4-12`；写入前 normalize；非法即拒绝包；比较只在规范化后进行。

### M3. 未拆“活跃账本 / 全量备份”DAO，也未关闭用户路径物理删除与 `ON DELETE CASCADE`

- 需求：§6、§29、§36、§89、§90
- 现状：`observeAllOrdered`/`getAllOrdered` 无过滤；`deleteTransaction` → `deleteById`。设计说“calculator inputs exclude deleted”，但未列出 API。实现若继续把全表喂给 `LedgerCalculator`，已删记录会进入余额/溢出校验（账错）。若保留 `deleteById` 或 FK `CASCADE`，一次误调用即拆掉 UUID、附件关系与审计。
- 补救：
  - `observeActiveOrdered` / `getActiveOrdered`：`is_deleted=0`，仅此进入观察管道与 `validateCandidateHistory`。
  - `getAllIncludingDeleted`：仅导出/备份/导入。
  - 编辑 `getById`：`isDeleted==true` 拒绝更新；详情不按“可编辑活账”打开。
  - 用户删除 = `is_deleted=1` + `deleted_at_millis` + 一条 DELETE（after=null）；已删再删不写第二事件。
  - **禁止** 用户路径 `DELETE FROM transactions`。物理清空只允许全量替换事务内、按子表→父表顺序。
  - 新表 FK **只许 RESTRICT/NO ACTION，禁止 CASCADE**。Room `@ForeignKey` + 约束开启。

### M4. 同记录替换的附件/审计/Blob 合并算法未写成失败闭合规则

- 需求：§58–§61、§101
- 设计给了 A 保留、B 替换、C 忽略，以及 “needed matching history/blobs”，但未定义行级操作。缺口会导致：插入 C（违反严格同记录）、C 的 audit 无父行、attachmentUuid 抢占 A 的关系、历史附件行缺失导致审计打不开。
- 补救（同记录提交算法）：
  1. 匹配集 = 规范化 `transactionUuid` 交集；只处理该集。
  2. **C 及仅被 C 引用的 blob/attachment/event 全部忽略**，不晋升、不插入。
  3. B 的**当前**附件集合以导入为准：本地原当前行改 `isActive=false`（若仍被本地或导入审计引用则留行）；不得物理删 blob。
  4. 导入 B 的当前+历史附件行 upsert：`attachmentUuid` 已属于**其他**本地交易 → 整次失败；blobUuid 本地已存在但哈希不同 → 整次失败（见 C1）。
  5. `eventUuid` 去重：内容规范相等则跳过；不等则失败。需要的缺失事件只附加，不改已有事件。
  6. 业务字段变化才插一条 `source=IMPORT` 的 EDIT；Before=提交瞬间本地事务内状态（含 `isDeleted` 与有序当前附件），After=导入规范化状态；tombstone 往返走 EDIT 而非 DELETE。
  7. 无变化：不写 EDIT，但仍可按 5 补缺失且不冲突的历史事件。
  8. 两种导入模式都把 `expensePersonId`/`expenseCategoryId` **按规范化 snapshot 名映射到本机**；不明则 id=null 并保留 snapshot 文本。禁止写入来源数字 ID。
  9. 提交前对**合并后活跃集**做余额/溢出校验；失败则整个事务回滚。
  10. 预览后、提交前重做匹配与 eventUuid 冲突检查（设计已有，保留）。

### M5. FK RESTRICT 下全量替换未规定删除/插入顺序

- 需求：§57、§62
- `audit_events` / `transaction_attachments` 对 `transactionUuid` RESTRICT。先删 `transactions` 会失败或逼人关 FK。关 FK 期间崩溃可留下半套库。
- 补救：单 Room 事务内：
  1. 删 `audit_events`
  2. 删 `transaction_attachments`
  3. 删 `transactions` 与 `attachment_blobs`（仅 metadata）
  4. 插入 blobs → transactions（按 M6 顺序，新 id）→ attachments → audit
  - 不生成 DELETE 事件；不动 person/category。磁盘旧 blob 按 C1 当孤儿，不在此步 rm。

### M6. 未规定 `transactions.json` 稳定顺序；全量替换按导出序重建 id 会改变并列余额

- 需求：§44、§54、§89、§90、§100
- 余额并列键是 `id`。设计“rebuild IDs in export order”但未规定数组顺序。若 JSON 无序，同秒两笔恢复后 `balanceAfter` 会变。
- 补救：导出数组严格 `transaction_date ASC, created_at_millis ASC, id ASC`。全量替换按该数组插入，使新 id 单调且并列次序与导出一致。同记录模式保留本地 id。

### M7. 已删除交易的 ZIP 目录语义未定，易把墓碑附件放进 `current/`

- 需求：§45、§50、§67
- 逻辑删除后关系行仍可能 `isActive=true`。设计未说导出分类。解压方会以为附件仍是当前凭证。
- 补救：`isDeleted==true` 的附件不得进 `current/`；用 `history_only/`（或明确的 `deleted/`，manifest 标明）。无当前且无审计引用则不建目录。活交易：`current/`=当前 active，`history_only/`=仅审计引用。同一 blob 只写一次，`archivePath` 共享。

### M8. 包内“当前行”与最新审计快照可以互相矛盾仍被接受

- 需求：§25、§44、§55、§63
- 可出现：最新事件 DELETE 但 `isDeleted=false`；或 afterSnapshot 附件/金额与 `transactions.json` 当前行不同。恢复后审计不可信。
- 补救：校验每条交易：若最新事件为 DELETE，则 `isDeleted=true` 且当前附件为空或仅历史；否则当前业务字段+有序当前附件必须与最新 afterSnapshot 一致（比较规则与 no-op 相同）。失败则拒绝导入。

---

## Minor

1. **§40/§41 配置事务顺序**：完全替换须先把全部 active 置 inactive（`active_name_key=NULL`）再 reuse/insert，避免唯一约束失败；同名替换还须更新 `sortOrder` 与展示名，不新增 unmatched。NFC+trim+ASCII 小写仅用于匹配，**禁止**回写已有交易 snapshot 字符串。包内或本地匹配歧义 → 整次失败。
2. **`client_op_key` 不可移植**（§54 未列入）：不导出、不导入；同记录保留本地；全量替换写 null。禁止当 `transactionUuid` 用。
3. **编辑页预览 vs FileProvider 只暴露 `blobs/`**（§13–§14、§19）：未提交附件在 `staging/`。须对当前 session staging 发临时读授权，或选择后即晋升到**新** storageKey（取消则孤儿，符合保守 GC）。禁止把用户 URI 当权威内容。
4. **备份规则**（§9、§22）：现 XML 只 exclude database。须同时 exclude `files/blobs` 与 `files/staging`（cloud-backup 与 device-transfer），避免只迁发票不迁账本。
5. **审计快照字段表**（§25）：写明含 `attachmentUuid`、`blobUuid`、`originalFileName`、`mimeType`、`sizeBytes`、`sha256`、`sortOrder`；比较用有序附件身份，排除 `updatedAtMillis`/`clientOpKey`。无变化则整行不 UPDATE（避免只改 `updatedAt` 而无 EDIT）。
6. **目录名金额格式**（§46、§98）：cents → 固定两位小数，不用 locale；sanitize 后 UUID8（冲突则加长 UUID）必须保留。
7. **其余格式边**：未知 ZIP 额外条目策略（建议忽略非关键、拒绝关键 JSON 重复）；`isDeleted` 与 `deletedAtMillis` 同真同假；活跃附件 `(transactionUuid, sortOrder)` 唯一；启动可清 `staging/` 但**禁止**清 `blobs/`；可用空间按 archive+expanded+余量保守拒绝。

§26 当时余额：设计省略正确（不可靠则不写）。§73“沿用更新配置”按沿用 origin、改 Releases+SemVer 理解，与 §72/§74 一致。

---

## 实现门禁（不是设计缺陷）

实现后必须证明；本轮**未跑**：

1. `MIGRATION_3_4` + 导出 `4.json`；1→4 与 3→4 fixture：收入/支出/选项、金额日期 snapshot 不变、每行唯一 UUID、`is_deleted=0`、余额一致；无 destructive。
2. 逻辑删除后 Home/Bills/期间/期末余额均不含已删；`validateCandidateHistory` 只用活跃行。
3. 创建生成 UUID；编辑/删除/导出/导入不变；规范化唯一。
4. 附件 A1–A11、编辑 A,B→A,C、删除后历史附件仍可打开（§91–§93）。
5. 审计连续 100→200→300 两条 EDIT；无变化 0 事件；DELETE 一条（§94）。
6. geocfg 完全替换 / 同名不新增（§96）；geodata 目录、UUID8、同名文件不覆盖、往返（§97–§100）。
7. 同记录 A,B + B,C（§101）；损坏包/Zip Slip 后正式库字节级不变（§102）。
8. 导入失败、取消预览、提交前崩溃：正式 DB 与**被引用**文件不变（孤儿 staging 可接受）。
9. 拆除 `AppUpdateGate`/`ViewModel.init` 启动联网；设置页 SemVer；HTTPS 仅 GitHub origin；权限仍仅 INTERNET。
10. 修订钉死 v3 的单测，而非为保绿停在 DB 3。
11. Mutex、busy、取消必释放并清 session staging。
12. 有设备再做 §111 UI；无设备如实报告。Robolectric 若采用须真跑 Room 迁移，不能只加依赖。
13. 保留全部记账规则、图标、cents、snapshot 策略；`docs/data_format.md` 与 README 在实现期补，不阻塞本轮设计修订。

旧 `docs/grok_*.md` / 1.1.x 报告**不是**本版证据。

## 审查方法

只读设计、需求 §0–§120、数据层与相关测试源、schema 3、manifest/backup XML、更新入口。未扫描 `.gradle/`/`build/`。未改代码、未 commit/push/reset、未开新仓库、未出网。未声称测试已执行。
