# GEO 1.2 Round1 设计对抗审查（Grok Agent B）

- 角色：GEO Adversarial Bug Hunter
- 对象：`docs/upgrade_1.2_design.md`（Status: DESIGN，2026-09-05）
- 对照：用户需求全文（已滤空行）、现网 `Entities.kt` / `GeoDao.kt` / `GeoDatabase.kt` v3、`LedgerRepository.kt`、`LedgerCalculator.kt`、`OptionSnapshotPolicy`、`SaveIdempotency`、相关 JVM/instrumented 测试
- 范围：只审设计。当前 v3 尚未落地 UUID/软删/附件/审计/导入导出，一律标为 **未实现**，不当缺陷
- 测试门禁：设计里的 Verification plan、现有 v3 测试绿，都 **不是** 1.2 通过证据
- 结论：**REVISE**（Critical=2 High=8，必须先改设计再写核心代码）

现网锚点（用于反例，不是要求本轮改代码）：`TransactionEntity` PK 仍是 `id`，`client_op_key` 全表 UNIQUE；`observeAllOrdered`/`getAllOrdered` 无删除过滤；`deleteTransaction` 物理删除；计算器排序键是 `(transactionDate, createdAtMillis, id)`；人员/分类可 inactive 与 active 同名并存（见 `deactivateThenReAddUsesNewIdAndAllowsRepeatedCycles`）；`AppUpdateViewModel.init` 仍自动联网——设计已要求改为手动，属未实现。

---

## 非缺陷（未实现 / 计划门禁）

| 项 | 说明 |
| --- | --- |
| 表结构/DAO/仓库仍是 v3 | 设计目标 DB 4，代码未写不算设计错 |
| 物理删除、无附件、无审计 | 设计改为逻辑删除+独立 blob/relation/audit |
| 无 `4.json`、无 MIGRATION_3_4 | 计划产物 |
| Verification plan 里的 JVM/Room/Robolectric | 尚未执行，不能当 PASS |
| ADB 无设备、instrumentation 未跑 | 设计已诚实声明 |
| 本版不做激进 GC、审计非密码学防篡改 | 与需求一致，保留 |

---

## Critical

### C1. MIGRATION_3_4 无法同时保证「每行唯一 canonical UUID」和 Room `4.json` 身份一致

**攻击面：** migration UUID validity。

**设计原文：** “adds non-null transaction_uuid (unique index)… Existing rows receive individual java UUID.randomUUID() inside migration”。未给出列添加/回填/UNIQUE/NOT NULL 的顺序，也未要求 **整表重建** 以匹配导出 schema。

**反例：**

1. `ALTER TABLE transactions ADD COLUMN transaction_uuid TEXT NOT NULL`（无 DEFAULT）→ 已有行迁移直接失败，1.1.3 用户升级炸库。
2. `ADD COLUMN … NOT NULL DEFAULT ''` 再 `CREATE UNIQUE INDEX` → 第二行起 UNIQUE 失败；或先回填再留着 DEFAULT。Room `4.json` 的 `notNull` / `defaultValue` 与真实 SQLite 表不一致 → `Migration didn't properly handle …`，同样升级失败。
3. `ADD COLUMN` 可空 + 回填 + UNIQUE INDEX，但实体是非空 `String` → 身份哈希失败。
4. 用同一 `UUID.randomUUID()` 做列 DEFAULT 再 UPDATE 漏行 → 重复 UUID，后续 UNIQUE/导入校验失败。
5. 重建新表时漏掉现有 `index_transactions_client_op_key` 或五个查询索引 → schema 不匹配或幂等键丢失。

SQLite 不能在一条 SQL 里为每行生成 Java UUID；Room 迁移又必须在同一事务里得到 **恰好** 与 `4.json` 相同的 NOT NULL、无多余 DEFAULT、索引名。

**最小修复：** 把 MIGRATION_3_4 写成强制表重建算法，并写进设计：

1. `CREATE TABLE transactions_new (… 全 v4 列，transaction_uuid TEXT NOT NULL，is_deleted INTEGER NOT NULL，deleted_at_millis 可空，无 UUID DEFAULT)`。
2. 按现网 `id` 游标逐行 `INSERT`，每行 `UUID.randomUUID().toString()`（RFC 4122 小写 8-4-4-4-12），禁止空串/大写/无连字符。
3. 断言回填后 `COUNT(DISTINCT transaction_uuid)=COUNT(*)`。
4. `DROP` 旧表，`RENAME`，按 Room 命名重建全部旧索引 + `index_transactions_transaction_uuid` UNIQUE。
5. 再 `CREATE` `attachment_blobs` / `transaction_attachments` / `audit_events`（FK 指向已有 UNIQUE 的 `transaction_uuid`）。
6. `is_deleted`/`deleted_at_millis` 用同一重建，避免 `ALTER … DEFAULT 0` 残留与 `4.json` 不符。
7. 测试：v1→v4、v2→v4、v3→v4 夹具（多笔收支+人员分类），UUID 全合法且互异，金额/日期/snapshot/`client_op_key`/`id` 不变，`is_deleted=0`。计划门禁未跑不算过。

### C2. 完全替换在 `FK RESTRICT` 下没有子表删除顺序，合法包无法原子替换

**攻击面：** full-replace atomicity。

**设计：** 子表 `transactionUuid`/`blobUuid` 均为 FK RESTRICT；“atomically replaces transaction/blob/relation/event metadata”；“Dataset restore may clear all inside one transaction”。没有 wipe 顺序，也禁止为旧账生成 DELETE 事件。

**反例：** 实现若在单一 Room 事务里先 `DELETE FROM transactions`（或先清 blob），RESTRICT 立即失败，事务回滚。用户看到「替换失败」，本地未变——功能上等于完全替换不可用。若为绕过而 `PRAGMA foreign_keys=OFF`，中途进程死亡可留下无父行的附件/审计，破坏「失败不污染」。

**最小修复：** 规定唯一 wipe+insert 顺序（外键保持 ON，禁止关 FK）：

1. 会话内 promote 新文件（新 `internalStorageKey`，禁止覆盖旧文件）。
2. 同一 Room 事务：`DELETE audit_events` → `DELETE transaction_attachments` → `DELETE attachment_blobs` → `DELETE transactions`（或不碰 sqlite_sequence 以外的配置表）。
3. 再按导出顺序 INSERT blob → transaction → relation → event。
4. 失败则事务回滚；用会话清单删除 **本次** 新文件。旧 DB 与旧被引用文件保持不变。
5. 人员/分类表禁止出现在这条事务里。

---

## High

### H1. 逻辑删除与现网 `client_op_key` UNIQUE / `SaveIdempotency` 未定义交互

**攻击面：** deletion/restore、same-record、cancellation。

现网：`index_transactions_client_op_key` 全表唯一；`getByClientOpKey` 无删除概念；`clientOpKeyReplayIsIdempotent` 要求同键返回同一 `id`。设计：删除改为 `is_deleted=1` 且 “Updates reject deleted records”；迁移 “retain … client operation keys”。

**反例：** 保存 `clientOpKey=K` 后删除。K 仍占 UNIQUE。

- 编辑器进程恢复后带着同一 K 再保存：查到已删行 → 按设计拒绝更新 → 用户无法保存；或
- 若查找跳过已删再 INSERT，UNIQUE 冲突；或
- 同记录导入把包里的 `client_op_key` 写到 B，与本地 A 撞键，整包失败。

用户需求 transactions.json **没有** `client_op_key`（本机幂等键，非可移植身份）。

**最小修复：**

- `client_op_key` 禁止进入 `.geodata`；同记录/完全替换都不得改写该列（完全替换新行一律 `NULL`）。
- 逻辑删除 **保留** K。`getByClientOpKey` 含已删行：若已删，幂等重放返回该 `id` 且 **不复活、不写 EDIT**。
- 正常「新建」必须新 K（现网 SavedStateHandle 已如此）。
- 禁止部分唯一索引除非 Room schema 能精确表达。

### H2. 「按导出顺序重建 id」不能保持账本序

**攻击面：** ordering。

计算器（现网测试 `testB`/`testI`）排序是 `(transactionDate, createdAtMillis, id)`。仅当 **日期与 createdAt 都相同** 时 id 才是次序。设计 “rebuild IDs in export order” 未定义数组序，也未冻结 `createdAtMillis`。

**反例：** 两笔同日、`createdAtMillis` 相同、旧 id=1 在前、id=2 在后。若 `transactions.json` 按 UUID 排列为 `[id2, id1]`，重建 id 后次序颠倒，`balanceAfterTransaction` 与期末余额相对现网语义改变。另一反例：只改 id、JSON 序与 `(date, createdAt)` 不一致时，**id 重建完全无效**，序仍跟 createdAt 走，设计承诺落空。

**最小修复：**

- 导出 `transactions.json` **必须** 按 `LedgerCalculator.stableAscendingComparator`（含已删行，用旧 id 做第三键）。
- 完全替换按该数组顺序 INSERT，新 `id` 单调递增（1..n 或等价），**不得改** `transactionDate`/`createdAtMillis` 来“调序”。
- 同记录保留本地 `id`+`createdAtMillis`；业务字段按导入覆盖后再跑一次 active 历史 `validateCandidateHistory`。

### H3. 10 / 10MiB / 30MiB 未限定为 **当前 active** 集合

**攻击面：** >10 files / >10MiB / >30MiB、removed historical blobs。

设计 “Limits: <=10 attachments…”；导入 “Validate all current AND historical snapshot attachment limits”。未写清编辑器计数是否含 `isActive=0`。

**反例：** 交易现有 active 10 个达 30MiB。用户移除 B、加入 C（历史仍引用 B）。若把 inactive B 计入 10/30，第 11 个逻辑上的「当前第 10 个」被拒，编辑需求（A,B→A,C）无法完成。若历史快照 10 个、当前 10 个合计 20 个 blob，用总和去卡 10，同样误伤。

**最小修复：**

- 三个硬顶只作用于 **某一快照或当前 active 集合** 各自：`count(active)<=10`、`each<=10485760`、`sum(active)<=31457280`。
- 历史快照各自独立校验，互不累加到当前限额。
- 编辑器每次选择后对「已有 active + 本次候选」做流式累计；多选 11 个：收下能放下的前 10 个（仍受 30MiB），拒绝其余并提示「每笔账最多添加 10 个附件」，不得部分写入 DB。

### H4. 同记录「忽略 C」仍可能导入 C 的 audit/blob，导致 FK 失败或污染

**攻击面：** same-record import and audit immutability。

设计：local A,B + import B,C → C 忽略；“Import needed matching history/blobs”；audit FK RESTRICT 到 `transactionUuid`。

**反例：** 包内 C 的 `audit_logs` / 附件索引仍被 INSERT → 无父交易，RESTRICT，**整次同记录导入失败**，B 的合法更新也被回滚。若先插 C 的 blob 再失败，留下无引用文件（见 H8）。若错误插入 C 的事件，本地出现无账单的审计，不可变历史被污染。

**最小修复：** 提交前计算 `matched = importUuid ∩ localUuid`。只导入 matched 的交易状态、其所需 blob、其 `eventUuid` 事件。C 的一切 JSON/文件当不存在。A 的本地事件一行不改。B：`eventUuid` 去重（内容必须逐字段相等，含 snapshot JSON 规范化后比较），冲突则 **整单 abort**；仅当当前业务（含 ordered active 附件、`isDeleted`）变化时插一条 IMPORT EDIT。

### H5. 身份冲突规则不完整：attachmentUuid 对本地、blob 内容、所有权

**攻击面：** UUID/hash conflict、audit immutability。

设计对 **eventUuid**「同 UUID 不同内容 = 损坏，含与本地历史碰撞」写清了；blobUuid 不一致 metadata 拒绝。未定义：

- 本地已有 `attachmentUuid=X` 指向 blob A，导入 X 指向 blob B；
- 完全替换前本地 blobUuid=X hash=A，包内 X hash=B（promote 阶段旧文件仍被引用）；
- 两笔交易抢同一个 `attachmentUuid`。

**反例：** 同记录把 X 改绑到 B，旧审计 Before 仍说 X=A，打开历史附件得到 B 的字节 → 审计不可变被破坏。完全替换若把新字节写进旧 `internalStorageKey`，DB 失败或进程死在 commit 前，**当前仍引用的发票被换成包内内容**。

**最小修复：**

- 包内：`transactionUuid`/`attachmentUuid`/`eventUuid`/`blobUuid` 各自唯一；一个 `attachmentUuid` 只能属于一个 `transactionUuid`。
- 与本地：eventUuid 不同内容 → abort（已有）。**attachmentUuid 不同 blobUuid/hash/name/mime/size → abort**（同记录不改写历史关系行）。blobUuid 不同 hash/size → abort（完全替换也 **不得覆盖** 旧文件：新 key 写入，事务里换行）。
- 仅当 blobUuid+sha256+size 完全一致才复用本地文件。
- `internalStorageKey` **永不** 从包读取。

### H6. 快照人员/分类映射未限制为 **当前 active**，会被历史同名打成「歧义」

**攻击面：** snapshot option identity。

设计：numeric id 不跨机复用；“map by normalized snapshot name if unambiguous”。现网允许 inactive「张三」与 active「张三」并存（不同 id）。配置节对 same-name 写了 active，**数据导入映射没有**。

**反例：** 本机 deactivate 张三(id=1) 再 add 张三(id=5)。导入支出 snapshot=`张三`。若对全表按名匹配 → 两条，被当成 ambiguous，id 置 null。编辑页历史 chip 与现网 `OptionSnapshotPolicy` 行为分叉；完全替换后余额/展示依赖错误 id。若误绑到 inactive id=1，保存会走「Person is inactive」。

**最小修复：** 只对 **同类型 active** 行做 NFC+trim+ASCII 小写匹配。0 条 → `id=null` 且 **原样保留 snapshot 字符串**。2 条 → 拒绝该包（与配置「ambiguous local matches」一致）。禁止用导入 numeric id 覆盖。审计 JSON 里的旧 id 视为来源机提示，UI 只信 snapshot 名。无操作比较必须用 **库内已存 snapshot/id**，禁止用现名重算（改名后点保存不得误造 EDIT）。

### H7. ZIP 未做「仅解压白名单」；`archivePath` 可指向保留 JSON / 逃逸

**攻击面：** malicious ZIP/bomb/duplicate paths。

设计有路径分量检查、重复名、压缩比、条目数。未规定 **只解压** `manifest.json`/`transactions.json`/`audit_logs.json` + 校验后的 `archivePath` 集合。

**反例：**

- 第二条 `manifest.json` 或 `MANIFEST.JSON` / `./manifest.json`：库返回首个或末个，校验与解压不一致。
- `archivePath=manifest.json` 或 `attachments/../../transactions.json`：若先解压后校验，覆盖已读 JSON。
- 10 万个无关条目先落到 staging，再读 manifest：在条目上限内仍可写爆磁盘。
- 加密条目、目录条目、symlink：只写 “unsupported” 未列方法。

**最小修复：**

1. 先 **只枚举** 不写盘：拒绝绝对路径、盘符、`\`、NUL、`.`/`..` 分量、非规范 canonical、重复名（大小写敏感精确相等）、非 STORE/DEFLATE、加密、symlink。
2. 根目录恰好三个 JSON 名（大小写精确）；禁止它们出现在附件 path。
3. 解析 manifest 后，**仅** 打开白名单条目；`archivePath` 必须是清单里的相对路径且仍通过同一套规则。
4. 流式计数：不信任 local header 未压缩大小、不按 header 预分配；单附件 >10485760 或累计展开 >2GiB 立即停。
5. 压缩比 >200 仅在已展开 >1MiB 后作为 **炸弹启发式**（可保留），但不得替代字节上限。

### H8. 源 SIZE 撒谎 / 无 EOF 流：未强制「超限立即停」；失败 promote 无会话清单

**攻击面：** source lies about size、partial IO、cancellation/process death、>10MiB/>30MiB。

设计：metadata 只是 hint、按实际字节、失败清 staging、rollback 最多无引用文件。未写：(a) 流永不 EOF；(b) `blobs/` 下已 promote 文件在 **失败/取消** 时如何删。

**反例：**

- `OpenableColumns.SIZE=100`，真实 10485761：若等到 EOF 才比大小，内存/磁盘被拖死。必须在第 10485761 字节 **立刻** 停、删临时文件、提示「单个附件不能超过 10 MB」。
- SIZE=11MiB 而真实 1KB：仅凭 metadata 拒绝 → 合法文件加不进去。预检查只能警告，**不得**单独失败。
- 导入已把 30MiB×N 写到 `filesDir/blobs/<uuid>` 后 DB 冲突：只清 staging，blobs 泄漏。用户重试 10 次可填满磁盘，下一次「存储不足」无法导入——违反失败不污染可用性。进程死亡可留孤儿（需求允许）；**可捕获的失败必须收回本次文件**。

**最小修复：**

- 复制循环：`read` 累计，`count > min(单文件限额, 剩余 30MiB 配额)` 立即 abort，不求 EOF。
- SIZE 缺/0/过小：忽略；SIZE 过大：可跳过预复制但最终仍以流式为准（或仅 UI 提示后仍流式验证）。
- 每次 mutation/导入持有 `sessionId` → 本次写入的最终 blob 路径列表。取消、校验失败、DB 回滚：**只删该列表**。启动可清 `staging/`，**禁止**扫删 `blobs/`。
- 每个最终文件 `flush` + `fd.sync()` 后再 DB commit。

---

## Medium

### M1. 附件 `sortOrder` 与审计 `occurredAtMillis` 无平局键

同毫秒连续编辑或导入事件 UI 顺序不稳定；同一交易两个 active 附件 `sortOrder` 相同则导出/展示抖动。最小修复：附件在交易内 `sortOrder` 唯一（0..n-1）；审计排序 `(occurredAtMillis DESC, eventUuid ASC)`。

### M2. 导出文件夹金额用 locale 小数

`128.00` 若走 `NumberFormat` 变成 `128,00`，Windows/ZIP 怪异。最小修复：cents → 固定 ASCII `%.2f`（点小数），再 sanitize。已删交易统一用 `history_only/`（不要 `deleted/` 双结构）。

### M3. `ACTION_VIEW` 与包可见性

现网 `queries` 只有 `https` VIEW。FileProvider `content://` PDF/其它在 API 30+ 会假「无应用」。最小修复：设计补 `queries`（`VIEW` + `content` / `application/pdf` / `image/*`）。图片解码必须有像素上限（先 `inJustDecodeBounds`）。

### M4. DAO 读路径分裂

“normal observation 排除已删；Backup 读全部”未定义 `getById`。反例：详情页仍 `getById` 到已删行，当活账编辑。最小修复：UI/计算器只用 `is_deleted=0`；导出/审计/同记录用 `IncludingDeleted`；已删拒绝 UPDATE。

### M5. 启动不清 staging；导出 SAF 半截文件

进程死在预览后 `staging/<session>` 残留。导出写到一半的 `.geodata` 被用户当备份。最小修复：启动只删 `staging/`；导出先写会话临时文件再复制到 SAF，失败尝试删目标。

### M6. format v1 未知字段与日期范围

“strict typed” 可能拒绝未来多一个字段，或放过非法 `transactionDate`。最小修复：v1 已知字段严格类型；未知字段忽略（靠 `formatVersion` 升级）；日期必须通过现网 `GeoDates.fromEpochDayOrNull`。

### M7. 配置 NFC 匹配 vs 现网大小写敏感唯一名

本机可同时 active `ABC` 与 `abc`（现网 `countActiveByName` 精确匹配）。配置 ASCII 小写会判歧义而整文件拒绝。最小修复：保持「歧义则拒绝」；文档写明；不要在本版改 `active_name_key` 语义。

### M8. Mutex 与取消

设计要求共享 Mutex。必须 `withLock`（取消时释放）。文件 IO 不得放进 Room 事务。预览到提交的 TOCTOU：提交时重跑同记录/审计冲突（设计已有，保留）。

---

## Low

- ZIP 内 Windows 保留名 `CON`/`PRN`（仅影响用户手解压，导入只信 UUID）。
- 压缩比 >200 可能误伤全 0 的 10MiB 附件；字节上限仍是权威。
- 审计可选「当时余额」设计省略，符合需求「不可靠则不展示」。
- 共享 Mutex 不包 DAO Flow：Room/WAL 事务后失效即可，不必当 Critical。

---

## 攻击清单覆盖

| 指定攻击 | 结论 |
| --- | --- |
| migration UUID validity | **C1** 算法空洞；Java randomUUID 本身合法但落不到 4.json |
| source lies about size | **H8** 小谎必须流式截断；大谎不得 metadata 独拒 |
| >10 / >10MiB / >30MiB | **H3** 必须按 active/快照；3×10MiB=30MiB 允许，+1 拒绝 |
| removed historical blobs | **H3/H5/H8** 历史引用不得覆盖/计入当前限额；失败不得扫删旧 blob |
| malicious ZIP/bomb/dup path/UUID/hash | **H5 H7** |
| partial IO failure | **H8 M5** |
| full-replace atomicity | **C2 H8** |
| same-record + audit immutability | **H1 H4 H5** |
| deletion/restore | **H1 M4**；复活只走 IMPORT EDIT（设计已有，保留） |
| snapshot option identity | **H6 M7** |
| ordering | **H2** |
| cancellation/process death | **H8 M5 M8**；commit 前崩溃留孤儿可接受 |

---

## 判定

**REVISE**

Critical=2，High=8，Medium=8，Low=3。

先把 C1–C2、H1–H8 写进设计（表重建迁移、FK 擦除序、软删与 client_op_key、账本排序键、active 限额、同记录匹配集、身份冲突、active 选项映射、ZIP 白名单、流式截断与会话 blob 清单），再让 Codex 写核心代码。本文件不是实现授权。未实现工作与测试计划绿均不构成本轮通过。
