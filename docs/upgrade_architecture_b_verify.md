# GEO 1.2 设计修订核验（Grok Agent B follow-up）

- 角色：GEO Adversarial Bug Hunter（Agent B 对自身 C1–C2、H1–H8 的独立复核）
- 范围：仅 `C:\Users\SUN\Desktop\GEO`
- 对照：`docs/upgrade_architecture_b.md` 的 **C1–C2、H1–H8**；`docs/upgrade_1.2_design.md` **Revision 2**（文首 binding contracts，声明 supersede 后文简写）与 **Revision 3**（B 审查裁决）
- 本轮不做：不重跑用户需求全文；不广扫 Gradle/`build/`/实现是否落地；不把 Robolectric/测试计划当证据；不改代码、不 commit/push、不出网；不新开攻击面
- 独立证据：逐条用 Rev2/Rev3 合同原文对照原缺陷机理，不采信「This resolves B-C1」等自评句

## 门禁

**APPROVE**

剩余 Critical = 0；High = 0。原 C1–C2、H1–H8 均关闭。Medium/Low 不在本轮复开范围。

三条不得写回设计的错误补救，本核验明确拒绝：

1. **禁止**「必须整表重建才能过 Room `4.json`」。`ALTER` 只要 SQL `DEFAULT` 与 `@ColumnInfo(defaultValue)` / 导出 schema **一致**，就是合法路径；不得假定默认值失配。
2. **禁止**「捕获到取消就无条件删除本次 `storageKey` 列表」。取消若发生在 **DB 提交成功之后**，这些文件已被引用，删除会造成库有引用、盘上无文件。
3. **不得**用压缩比启发式误拒 GEO **自己**导出的全 0 附件。blob 条目 STORE + 预计算 CRC32 关闭该误杀；字节上限仍是权威。

---

## 处置总表

| ID | 原缺陷 | 处置 | 说明 |
|---|---|---|---|
| C1 | 迁移无法同时保证每行唯一 UUID 与 Room schema 身份 | **关闭（拒绝整表重建）** | `NOT NULL DEFAULT ''` / `DEFAULT 0` 与 ColumnInfo 对齐；逐行 bound UPDATE；断言后再 UNIQUE；失败回滚。匹配时不必重建表 |
| C2 | FK RESTRICT 下全量替换无子表擦除序 | **关闭** | 单事务 clear audit→relations→transactions→blobs，再 insert blobs→transactions→relations→events；不关 FK、不动选项 |
| H1 | 软删与 `client_op_key` UNIQUE / 幂等未定义 | **关闭** | 键不可移植；全量新行 NULL、同记录保留本地键；墓碑重放返回原 id、不复活、不写事件；显式编辑墓碑失败 |
| H2 | 「按导出序重建 id」未钉数组序，账本序可翻 | **关闭** | 导出严格 date/created/id ASC（含墓碑）；全量按数组序重建 id；保留导入 `createdAtMillis`；同记录保留本地 id |
| H3 | 10/10MiB/30MiB 未限定为当前/各快照集合 | **关闭** | 限额按当前 active 与**每一**历史快照独立，从不对历史并集；多选超限整次拒绝且不部分写库 |
| H4 | 「忽略 C」仍可能导入 C 的 audit/blob | **关闭** | 提交时 UUID 交集；C 及其独占事件/附件/blob 视为不存在 |
| H5 | attachmentUuid/blob 内容/所有权冲突规则不全 | **关闭** | 身份字段不可变；内容/所有权不一致整次回滚；新文件新随机 key，禁止覆盖旧盘文件 |
| H6 | 快照选项映射未限 active，历史同名变歧义 | **关闭** | 只匹配同类型 **active** 规范化名；忽略 inactive；0 条 null 且保留原文；>1 条整包失败 |
| H7 | ZIP 未白名单解压；`archivePath` 可打到保留 JSON | **关闭** | 先枚举后解压；三根 JSON + `attachments/` 白名单；大小写折叠重复名拒绝；STORE/DEFLATE；自有 blob 用 STORE |
| H8 | SIZE 撒谎/无 EOF；失败 promote 无会话清单 | **关闭（拒绝提交后无条件删文件）** | 超限立即停；metadata 不得独拒合法字节；只清 **库中确认无引用** 的本次 key；提交后取消必须保留已引用新文件 |

---

## C1 — 关闭（拒绝「必须重建表」）

**原机理：** 未给出 ADD / 回填 / UNIQUE / NOT NULL 顺序。无 DEFAULT 的 `NOT NULL` 会炸已有行；`DEFAULT ''` 立刻 UNIQUE 会撞空串；DEFAULT 若与 `4.json` 不一致会 `Migration didn't properly handle`。原补救要求整表 `CREATE/INSERT/DROP/RENAME`，并去掉 UUID DEFAULT。

**Rev2 §2 合同（独立核对）：**

- `ADD transaction_uuid TEXT NOT NULL DEFAULT ''`
- `ADD is_deleted INTEGER NOT NULL DEFAULT 0`
- `ADD deleted_at_millis INTEGER`
- 按 id 游标、每行独立 `UUID.randomUUID().toString()` **bound UPDATE**
- 断言非空且 `COUNT(*) = COUNT(DISTINCT transaction_uuid)`，**然后** UNIQUE INDEX
- 「Room column defaultValue="''" agrees with SQL, but every entity factory supplies a new UUID」
- 保留现有 id/索引/AUTOINCREMENT；**no table recreation needed**
- 失败 throw/rollback；测 3→4 与 1→4

**Rev3：** 「Table rebuild is not required when actual SQL and exported defaults MATCH; runtime migration will verify it.」

原 C1 把「DEFAULT 残留」等同于「必然与 4.json 失配」。该等式不成立：Room 身份哈希包含 `defaultValue`；SQL `DEFAULT` 与 `@ColumnInfo(defaultValue)` / `4.json` **写成同一字面量** 即通过。SQLite `ALTER ADD COLUMN … NOT NULL DEFAULT` 对已有行填默认值，**不需要**拷表。UNIQUE 建在回填与断言之后，不会在空串上失败。实体工厂始终写入新 UUID，空串 DEFAULT 只服务 schema 身份，不作为运行时身份源。

**原补救不可用：** 强制重建会无谓丢掉 `sqlite_sequence` / 现网索引名的对齐面，且不是关闭本缺陷的必要条件。本核验 **不** 要求写回表重建。实现门是迁移测试证明 SQL 与导出 schema 一致，不是设计层再假定失配。

1→4 走既有 1→2→3→4 链，覆盖原「夹具含 v2」要求。未单列 2→4 不构成剩余 Critical。

---

## C2 — 关闭

**原机理：** 子表 FK RESTRICT；若先 `DELETE transactions`（或先清仍被 relation 引用的 blob），立即失败，全量替换不可用；关 FK 则中途死亡可留下无父行。

**Rev2 §6：** 单事务 「clear audit, relations, transactions, blobs; insert blobs, transactions, relations, events. Do not change options/FK enforcement.」

对照 FK：`audit_events` / `transaction_attachments` 引用交易；relation 同时引用 blob；blob **不** 引用交易。故：

1. 先清 audit、relations → 交易与 blob 均无子行
2. 再清 transactions → 合法
3. 再清 blobs → 合法

原补救顺序是 audit→relations→blobs→transactions，同样 FK 安全。Rev2 把交易放在 blob 之前清，因为 relation 已删、blob 无指向交易的 FK，**两种顺序都闭合**。插入 blobs→transactions→relations→events 满足父先于子。选项表不进该事务。晋升仍在提交前（Rev2 §1）。失败回滚 + H8 只收无引用新文件；旧被引用文件不动。

---

## H1 — 关闭

**原机理：** 全表 UNIQUE 的 `client_op_key` 在软删后仍占键；重放可能拒更、撞 UNIQUE 或把包内键导入到另一行。需求中 `transactions.json` 无该字段。

**Rev2 §6：** 「client_op_key never portable; full restore null, matching restore retains local.」

**Rev3 B-H1：** 创建幂等重放即使墓碑也返回原 ID，不复活、不写新事件；显式编辑墓碑失败；可移植数据永不携带该键。

与原最小修复一致：键留在本地行上；`getByClientOpKey` 含已删；新建必须新键；禁止把包内键写进同记录/全量行。不引入 Room 难以表达的部分唯一索引。

---

## H2 — 关闭

**原机理：** 计算器第三键才是 `id`。未冻结 `transactions.json` 数组序时，「按导出序重建 id」或按 UUID 排列都会让并列 `(date, createdAt)` 的余额翻转。

**Rev2 §6：** 「Export transaction array strictly date ASC, created ASC, id ASC including tombstones; restore sequential IDs in array order. Same-record preserves local IDs.」

**Rev3 B-H2：** 钉死稳定导出数组；保留导入的 `createdAtMillis`（记录时间是业务字段，全量必须还原时间戳）；同记录保留本地 id；合并结果溢出则拒绝。禁止一边宣称已用导入业务态覆盖、一边偷偷留着本地 `createdAt`。

全量：数组已按 `(date, created, 旧 id)` 排好，新 id 按该序单调，三个排序键的相对序与现网一致，且不靠改时间戳调序。同记录：本地 id 不动，避免与仍留在库里的其它行抢第三键；`createdAtMillis` 随业务态来自导入后做溢出校验。原「同记录连 createdAt 也必须留本地」不是关闭账本序缺陷的条件，Rev3 的取舍自洽。

---

## H3 — 关闭

**原机理：** 限额若计入 `isActive=0` 或把多版快照并成一个 10/30，则 A,B→A,C 或「历史 10 + 当前 10」被误拒。

**Rev3 B-H3：** 限额作用于 **当前集合** 与 **每一** 历史快照，**从不** 对历史版本并集。多选超限拒绝整次选择并给精确反馈；不得部分改库。

比原「收下能放下的前 10 个」更严，仍失败闭合（不部分写库），属合法裁决而非残留 High。流式累计与单文件/合计上限由 H8 合同承接。后文「Validate all current AND historical snapshot attachment limits」按 Rev3 读作「各自校验」，不得读成并集。

---

## H4 — 关闭

**原机理：** 本地 A,B + 包 B,C，C 应忽略；若仍 INSERT C 的 audit/blob，RESTRICT 会让整次同记录失败，或污染无父审计。

**Rev2 §5：** 「intersect transaction UUIDs at commit; ignore unmatched transaction and its exclusive events/attachments/blobs。」

C 的 JSON/文件当不存在。B 的事件并集与冲突整次回滚由同一条 + H5 覆盖。提交前重匹配仍在后文，失败模式是整次 rollback。

---

## H5 — 关闭

**原缺口：** `attachmentUuid` 改绑 blob；全量把新字节写进仍被引用的旧 `internalStorageKey`；两笔账抢同一 `attachmentUuid`。

**Rev2 §1 + §5 + 导入安全后文：**

- 可移植 `blobUuid` ≠ 本地 `internalStorageKey`；导入文件一律新随机 key，提交前晋升
- 禁止静默跳过/覆盖；全量只用新 key
- 已有 `attachmentUuid` 必须保留 owner/blob/原名/size/hash/MIME；不同身份内容是损坏不是 upsert
- `blobUuid` 内容不同或 `eventUuid` 规范载荷不同 → 整次回滚
- 同记录复用须元数据 **且** 本地实字节一致
- 包内身份唯一；冲突所有权拒绝

进程死在提交前最多留下无引用新文件，**不会**改写仍被当前行引用的旧发票字节。`internalStorageKey` 从不从包读取。

---

## H6 — 关闭

**原机理：** 现网允许 inactive 与 active 同名。数据导入若对全表按名匹配，会歧义置 null 或绑到 inactive。

**Rev3 B-H6：** 只对同类型 **active** 本地名映射；忽略 inactive 重复；0 条 → 本地 id null 且 **原样保留** snapshot；规范化 active >1 → abort。导入数字 id 不覆盖。

Rev2 §6 「snapshot text NEVER lost or regenerated」与配置节「No rewriting historical snapshots」一起禁止用现名重算历史。无操作比较走库内已存 snapshot/id（审计节），不构成本条残留。

---

## H7 — 关闭（含自有全 0 附件）

**原机理：** 未规定只解压白名单；第二条 `manifest.json` / `archivePath=manifest.json` / Zip Slip 可打到已读 JSON；先落盘再读清单可写爆；加密/symlink 方法空洞。另：压缩比 >200 可能误伤全 0 的 10MiB 自有导出（原 Low，与本条同一 ZIP 合同）。

**Rev3 B-H7：**

- 解压前只枚举
- 精确名白名单：三个根 JSON + 清单附件路径且 **严格** `attachments/` 下
- canonical 与 **大小写折叠** 重复名拒绝
- 不解 symlink（产出皆随机文件名）；仅 STORE/DEFLATE；不支持/加密失败
- 目录条目只允许被引用附件的规范父路径，不当身份
- 多余条目失败

**Rev3 压缩比裁决：** 「write blob ZIP entries STORED with precomputed CRC32; JSON DEFLATED. Include zero-filled 10MiB attachment export/import regression。」STORE 条目比约为 1，自有全 0 附件不会触发 >200 启发式；该启发式不得替代字节上限，也不得拒绝 GEO 自己的合法包。

后文「entries <=100000」仍是硬顶，但多余条目直接失败且先枚举后解压，关闭「先写十万垃圾再读 manifest」。`archivePath` 不得指向三根 JSON 或逃逸出 `attachments/`。

---

## H8 — 关闭（拒绝「取消一律删本次新文件」）

**原机理：** (a) `SIZE=100` 实为 10MiB+1 时若等 EOF 才比大小，磁盘被拖死；(b) `SIZE=11MiB` 实为 1KB 时仅凭 metadata 会拒合法文件；(c) 已 promote 到 `blobs/` 后 DB 失败若只清 staging，可捕获失败会堆积孤儿；(d) 原补救写「取消/回滚就删本次最终路径列表」，未区分 **提交后才看到的取消**。

**Rev3 B-H8：**

- 流循环在实际字节 `> min(单文件上限, 剩余合计配额)` 时立即停，不求 EOF
- metadata 单独不得拒绝合法字节
- 跟踪每次操作的新 `storageKey`
- catch/cancel 清理 **仅** 这些在共享锁 + `NonCancellable` 下 **已证明库中无引用** 的 key
- 「cancellation observed AFTER successful DB commit: referenced files must survive」
- 进程崩溃可留孤儿；可捕获回滚不得累积孤儿
- 禁止扫删 live `blobs/`

**Rev2 §1 / 生命周期：** 先 flush/sync 晋升再提交；晋升失败则不提交。

原 H8「取消就删列表」在下列窗口是错的：晋升成功 → Room 提交成功 → 协程随后才收到 cancel。此时列表内文件 **已被引用**。无条件 `delete` 制造 dangling live references，比孤儿更糟。正确合同是：与仓库同一把锁、在不可取消上下文里查引用；已引用则保留；未引用才删本次 key。启动只清 staging，永不扫 `blobs/`。

SIZE 过大仅可跳过预复制或 UI 提示，最终以流式为准——与「metadata 不得独拒」一致。

---

## 不在本轮重开

- 原 Medium M1–M8、Low：Rev3 已吸收（审计时间+`eventUuid` 平局键、快照内连续 `sortOrder`、金额两位小数字符、ACTION_VIEW 捕获、活跃/墓碑读路径、未知字段忽略 + `LocalDate`、配置歧义显式错误、Mutex/`NonCancellable`/TOCTOU、导出先私有 staging 再 SAF、启动只清过期 staging）。本文件不对它们做 Critical/High 门禁。
- 设计文 Robolectric 已跑成功、工作区是否干净、代码是否已按 Rev2/3 落地：实现审查事项，不是本设计门禁。

## 实现时必须跟文首合同（提醒，非新缺陷）

1. `ALTER` 的 `DEFAULT` 必须与 `@ColumnInfo(defaultValue)` 和 `4.json` **字面一致**；不要为「怕失配」擅自重建表，也不要漏写 defaultValue。
2. 全量擦除顺序保持父无子后再删；不要 `PRAGMA foreign_keys=OFF`。
3. 取消/失败清理前必须在锁内证明无引用；**提交后取消不得删新文件**。
4. 导出 blob 用 STORE；不要让压缩比误杀自有全 0 附件。
5. 后文简写与文首冲突时，以 Revision 2/3 为准。
