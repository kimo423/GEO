# GEO 1.2 设计修订核验（Grok Agent A follow-up）

- 角色：GEO Implementation Reviewer（Agent A 对自身 C1/M1–M8 的独立复核）
- 范围：仅 `C:\Users\SUN\Desktop\GEO`
- 对照：`docs/upgrade_architecture_a.md` 的 **C1、M1–M8**；`docs/upgrade_1.2_design.md` **Revision 2**（文首“binding implementation contracts”，声明 supersede 后文简写）
- 本轮不做：不重跑需求 §0–§120；不扫描 Gradle/`build/`；不读实现是否已落地；不把设计文中的 Robolectric/运行时声明当证据；不改代码、不 commit/push、不出网
- 独立证据：逐条用 Revision 2 合同原文对照原缺陷机理，不采信“This resolves A-C1”等自评句

## 门禁

**APPROVE**

剩余 Critical = 0；Major = 0。原 C1/M1–M8 均关闭。Minor 与实现门禁不在本轮复开范围。

两条不得写回设计的错误补救，本核验明确拒绝：

1. **禁止**“先 DB 提交、再把 staging 晋升到 live `blobs/`”。进程死在提交后、rename 前会造成 **库有引用、盘上无文件**。Revision 2 禁止 DB-before-rename，正确。
2. **不得**把“按 `occurredAt` 的最后一条审计 == 当前行”当作导入不变量。同记录是历史并集，墙钟可偏；后导入的本地 IMPORT 事件时间戳可以早于包内事件。Revision 2 拒绝该等式，理由成立。

---

## 处置总表

| ID | 原缺陷 | 处置 | 说明 |
|---|---|---|---|
| C1 | 导入晋升撞 live 路径，跳过写入仍写库哈希 | **关闭** | 可移植 `blobUuid` ≠ 磁盘 `internalStorageKey`；新文件新随机 key；先晋升后提交；禁止静默跳过/覆盖；全量替换只用新 key；同记录复用须元数据+实字节一致否则整次失败。**不采纳**原补救第 6 步（提交后再 rename） |
| M1 | 迁移 SQL 顺序不可执行 / 全表同一 UUID | **关闭** | `NOT NULL DEFAULT ''` → 逐行 bound UPDATE 独立 UUID → 断言非空且 count=distinct → 再 UNIQUE INDEX；失败抛错回滚；测 3→4 与 1→4；不重建表、不 destructive |
| M2 | UUID 大小写可并存，同记录匹配失败 | **关闭** | `UUID.fromString(s).toString()==s`，仅小写 `8-4-4-4-12`，非法拒绝、不别名 |
| M3 | 未拆活跃/全量 DAO；用户物理删；CASCADE | **关闭** | 原 `observeAllOrdered`/`getAllOrdered` 改为活跃 SQL；新增 `getAllIncludingDeleted`；去掉用户 `deleteById`；墓碑拒绝编辑；新 FK 仅 RESTRICT |
| M4 | 同记录附件/审计/Blob 合并非失败闭合 | **关闭** | 交集匹配、C 及独占对象忽略、身份字段不可变、冲突整次回滚、事件并集、仅业务变化写 IMPORT EDIT、合并后溢出校验 |
| M5 | FK RESTRICT 下全量替换删插顺序 | **关闭** | 单事务：清 audit→relations→transactions→blobs，再插 blobs→transactions→relations→events；不关 FK、不动选项 |
| M6 | `transactions.json` 无稳定序，恢复后并列余额变 | **关闭** | 导出严格 date ASC, created ASC, id ASC（含墓碑）；全量按数组序重建 id；同记录保留本地 id |
| M7 | 墓碑附件可能进 `current/` | **关闭** | 删除先拍 before 再停用当前关系；已删无当前引用；审计附件只进 `history_only/` |
| M8 | 要求“时间序最后事件 == 当前行” | **关闭（拒绝原不变量）** | 墙钟偏差 + 同记录并集使该等式非法；`transactions.json` 为当前权威；逐条校验事件字段/归属/附件；manifest 绑定当前记录完整性 |

---

## C1 — 关闭（正确拒绝“提交后再晋升”）

**原机理**：未规定 `blobUuid` 与 `internalStorageKey` 分离。若实现把导入文件写到 `blobs/<blobUuid>`，live 已有同路径且内容不同时，禁止覆盖 → 跳过写入 → 事务仍写入包内 sha256/size → 报告成功、打开却是旧字节。全量替换尤其无法用“对照本地 DB”抓住冲突。

**Revision 2 §1 合同（独立核对，非自评）**：

- “Portable blobUuid and local internalStorageKey are DISTINCT.”
- “Every imported file receives a NEW random storageKey unrelated to its blobUuid, **promoted before DB commit**.”
- “Existing target files are NEVER silently skipped or overwritten.”
- “Full replace uses only newly staged keys.”
- “Same-record may reuse a DB-referenced blobUuid only if metadata AND actual local bytes match; otherwise reject whole import.”
- “Failed promotion -> no DB commit.”
- “DB-before-rename is prohibited because process death would leave dangling live references.”

这关闭了原 C1 的失败模式：新文件不会对准已有 live 路径；冲突不得跳过；哈希不一致不得当成功；全量替换不复用旧盘文件。

**原补救第 6 步不可用**：先成功提交 DB、再把 staging `link/rename` 进 `blobs/`。崩溃窗口是 **dangling live references**（比孤儿文件更糟）。本核验 **不** 要求改回该顺序。崩溃安全模型必须保持：

1. 校验后把新字节晋升到 live `blobs/<new random key>`（或等价：先 staging 再 **提交前** 晋升到新 key）；
2. 再 Room 提交；
3. 失败/崩溃最多留下无引用孤儿，**禁止**留下有引用无文件。

后文 “Do not overwrite old blob UUID storage collisions; reuse only after content validation” 属被 supersede 的简写；实现以文首 Revision 2 为准，不得理解成“跳过写入仍提交”。

**不构成剩余 C/M**：未写“冲突则换新 key 重试”——新随机 key 碰撞极稀，且“不得静默跳过 + 晋升失败则不提交”已失败闭合。提交后抽查存在性属实现门，不是未关闭的设计洞。

---

## M1 — 关闭

原风险：一步 `ADD ... NOT NULL UNIQUE` 或 `DEFAULT` 同一 UUID 再立刻唯一索引，多行失败；失败后诱导 destructive。

Revision 2 §2 可执行顺序：

1. `ADD transaction_uuid TEXT NOT NULL DEFAULT ''`
2. `ADD is_deleted INTEGER NOT NULL DEFAULT 0`
3. `ADD deleted_at_millis INTEGER`
4. 按 id 游标、`UUID.randomUUID().toString()` **bound UPDATE**（禁止整表同一表达式）
5. 断言无 blank，且 `COUNT(*) = COUNT(DISTINCT transaction_uuid)`
6. 再建 UNIQUE INDEX
7. 失败 throw，Room 回滚迁移；不重建表、保留 id/索引/AUTOINCREMENT
8. 测试 3→4 与 1→4

`NOT NULL DEFAULT ''` 再填唯一值，避免原“可空列 vs 4.json NOT NULL 必须拷表”分叉，且不会在建唯一索引前用同一 UUID。与后文“Keep explicit 1->2->3->4 chain… No destructive fallback”一致。

---

## M2 — 关闭

Revision 2 §3：`UUID.fromString(s).toString()==s`；仅小写规范 `8-4-4-4-12`；非规范拒绝；“never alias identities”。大写/非规范包不会被规范化成另一身份。写入侧 `UUID.randomUUID().toString()` 已是该形式。

---

## M3 — 关闭

Revision 2 §4：

- 现有 `observeAllOrdered` / `getAllOrdered` **改为活跃 SQL**（`is_deleted=0`）
- 新增 `getAllIncludingDeleted` 仅恢复/导出
- 删除用户路径 `deleteById`
- `getById` 可作内部快照；仓库 get/edit 拒绝墓碑；已删再删 no-op
- 计算器防御性过滤墓碑
- 新 FK 全部 RESTRICT，禁止 CASCADE

原“必须改名 observeActiveOrdered”不是缺陷关闭条件；语义已钉死为活跃集。全量替换中的物理清空由 M5 单事务顺序覆盖，不是用户路径。

---

## M4 — 关闭

对照原 10 步闭合规则，Revision 2 §5 + §6 选项映射：

| 原规则 | 修订合同 |
|---|---|
| 只处理规范化 UUID 交集 | “intersect transaction UUIDs at commit” |
| C 及独占 blob/附件/事件忽略 | “ignore unmatched transaction and its exclusive events/attachments/blobs” |
| B 当前关系停用，不物理删 blob | “Deactivate previous current relations; preserve all immutable reference metadata” |
| attachmentUuid 换主/换内容 = 失败 | “must retain owner, blob, original name, size/hash/MIME; different identity content is corruption (not upsert)” |
| blobUuid 哈希不同或 eventUuid 载荷不同 = 整次回滚 | 原文同等 |
| 缺失事件只附加，不改已有 | “Union immutable historical events” |
| 业务变化才 IMPORT EDIT；Before=提交瞬间库 | “Before snapshot from commit-time DB”；“IMPORT EDIT only on business change” |
| 无变化可不写 EDIT，仍可补历史 | “no-op may still import missing history” |
| 选项按规范化 snapshot 映射，禁止来源数字 ID | §6 “Option IDs remapped… snapshot text NEVER lost or regenerated” |
| 合并后活跃集溢出校验 | “Revalidate merged active ledger overflow” |

预览后提交前重匹配：后文仍保留 “Recheck same-record matching and audit conflicts at commit”。失败模式是整次 rollback，不是部分写入。

---

## M5 — 关闭

Revision 2 §6：单事务内 “clear audit, relations, transactions, blobs; insert blobs, transactions, relations, events. Do not change options/FK enforcement.” 与原补救顺序一致，且明确不关 FK。磁盘旧 blob 按 C1 当孤儿，不在元数据事务里 rm。

---

## M6 — 关闭

Revision 2 §6：“Export transaction array strictly date ASC, created ASC, id ASC including tombstones; restore sequential IDs in array order. Same-record preserves local IDs.” 并列键 `id` 在全量替换下由该数组序重建，墓碑也参与次序。`client_op_key` 不可移植（全量 null、同记录保留）一并钉死，避免误当身份键。

---

## M7 — 关闭

原缺口：逻辑删除后关系仍可能 `isActive=true`，导出进 `current/`。

Revision 2 §7：删除先捕获 before，再停用当前关系；“Deleted transactions have no current attachment references”；其审计附件 **只** 导出 `history_only`；活交易按 current / history_only 分流。v4 附件表为新建，无历史脏数据要迁。同一 blob 只写一次、共享 `archivePath` 的后文规则仍在。

---

## M8 — 关闭（拒绝按时间“最后事件相等”）

原补救要求：若时间序最新事件为 DELETE，则当前必须已删；否则当前业务字段+当前附件必须等于该事件 afterSnapshot。

该不变量在本设计下不成立，理由独立成立，不是文过饰非：

1. **同记录是并集**：本地历史 ∪ 导入历史，不是单一设备的线性链。
2. **墙钟可偏**：包内 `occurredAtMillis` 可以晚于本次提交写入的 IMPORT EDIT。若以时间最大值当“最后事件”，合法往返会被拒绝。
3. 设计没有因果指针（无 prevEventUuid），时间戳不能当密码学线性证明。

Revision 2 §8 的替代合同足够关闭原“恢复后审计不可信”中 **可执行且合法** 的部分：

- 当前权威是 `transactions.json`，不是“时间最后一条 afterSnapshot”
- 逐条校验事件类型、UUID 归属、before/after、附件完整性
- `eventUuid` 冲突（同 ID 不同规范载荷）仍整包失败（后文 Import safety）
- 新增 manifest `currentSnapshotHashes`（按 `transactionUuid`）绑定当前记录与清单完整性
- 审计定义为不可变应用历史，不是外部分包的时钟序或认证证明

因此原 M8 **作为导入拒绝条件** 不得写回。包内“DELETE 事件与当前 isDeleted=false 并存”在并集/偏钟后可以是合法历史，不能按时间最大值裁决。

`currentSnapshotHashes` 的具体哈希载荷未展开，属实现期与 `data_format.md` 补全，不把 M8 重新打成 Major：整份 `transactions.json` 的 SHA-256 已在清单中，当前权威已经有完整性绑定。

---

## 不在本轮重开

- 原 Minor 1–7：Revision 2 §9 已吸收大部分（配置先全量 deactivate、backup 排除 blobs/staging、FileProvider、金额两位小数、未知条目拒绝等）。本文件不对其逐条门禁。
- 设计文 “Host Robolectric Room smoke test has now executed successfully”：**不是**本核验证据。
- 工作区是否干净、代码是否已按 Revision 2 落地：实现审查事项，不是本设计门禁。

## 实现时必须跟文首合同（提醒，非新缺陷）

1. 晋升 **先于** DB 提交；永远不要提交后再 rename 进 live。
2. 不要用 `blobUuid` 当路径；不要在碰撞时跳过写入。
3. 不要实现“按 occurredAt 最后事件必须等于当前行”。
4. 后文简写与文首冲突时，以 Revision 2 为准。
