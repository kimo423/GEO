# GEO 1.2 ROUND2 Agent B — 对抗审查（独立）

审查对象：`C:\Users\SUN\Desktop\GEO` 当前源码（attachments / transfer / schema4 / LedgerRepository / 编辑器 / Settings / GitHub）。
约束：未改生产代码、未跑 Gradle、未联网、未装机。`docs/upgrade_validation.log` 显示 coordinator 侧 `testDebugUnitTest` + `lintDebug` + `assembleDebug` **BUILD SUCCESSFUL**，只能证明当时编译/单元测试通过，**不能**当作 108/102 攻击项或实机门禁已过。设计修订 2/3 是实现合同，不是验证通过。

未把「可选 JSON 字段缺失」算缺陷。下列行号按审查时源码。

---

## Critical

无。未发现已证实的：限制被绕过、ZIP 写出沙箱外、失败导入污染正式库、已引用 blob 被覆盖/误删。

---

## High

### H1 新建幂等回放丢掉当前草稿（含 A,B→A,C）

- **位置**：`LedgerRepository.kt:90-91`；`AddTransactionViewModel.kt:376-384`
- **根因**：`id == null && existing != null`（命中 `client_op_key`）立刻 `return existing.id`，不比较、不写入当前 draft。ViewModel 只要 `savedId > 0` 就发 Saved。进程死后 SavedStateHandle 常只恢复 `client_op_key`、不恢复本地 id。
- **复现**：新建并保存金额 100、附件 A,B → 在 `savedStateHandle[ARG_TRANSACTION_ID]=savedId` 写入并 checkpoint 之前杀进程 → 恢复后改成 200、附件 A,C → 再保存。UI 成功，库仍是 100/A,B，无 EDIT。逻辑演示：`docs/upgrade-b-tests/create_replay_drops_draft.py`
- **最小修复**：回放到**未删除**行时按 **edit** 继续（tombstone 仍直接返回 id、不复活）。ViewModel 在拿到 id 后必须写入 `ARG_TRANSACTION_ID`。
- **证据类型**：源码路径 + 与 `UpgradeRepositoryTest` tombstone 回放一致的 early-return；未做进程死亡仪器测试。

---

## Medium

### M1 ZIP 目录白名单用 `startsWith`，无路径边界

- **位置**：`DataArchive.kt:208-211`
- **根因**：`allowed.any { it.startsWith(e.name) }`。目录 `attach`、`a`、甚至名为 `manifest.json` 的目录都能过，因为 `attachments/...` 以它们为前缀。文件仍走精确白名单，故不是 zip-slip 写盘，但违反「多余条目失败 / 目录只能是附件规范父路径」。
- **复现**：合法 geodata 加空目录条目 `attach/`。`docs/upgrade-b-tests/zip_directory_prefix.py`
- **最小修复**：`item == dir || item.startsWith(dir.removeSuffix("/") + "/")`。

### M2 超限文案合并，且 copy 先于 validate

- **位置**：`AttachmentPolicy.kt:43`；`AddTransactionViewModel.kt:114-126`
- **根因**：流式 `limit=min(MAX_FILE, remaining)` 失败信息固定为「单附件 10 MiB，总附件 30 MiB」。11 个走 count 文案「每笔最多 10 个附件」，与需求「每笔账最多添加 10 个附件 / 单个不能超过 10 MB / 全部不能超过 30 MB」不一致。超限在 copy 已抛，`validate()` 的分项文案到不了用户。
- **复现**：第 11 个；10MiB+1；已有 29MiB 再加 2MiB。文件被拒（行为对），提示不对。
- **最小修复**：copy 区分 `limit==MAX_FILE` 与 remaining；count 用需求原文。

### M3 本机选项唯一性与导入匹配规则不一致

- **位置**：`OptionNameValidator.kt:23-28`；`GeoDao.kt:63,89`；`ConfigTransfer.kt:59-61,70-82`
- **根因**：UI/`countActiveByName` 只 trim 后精确相等，可同时存在活跃 `ABC` 与 `abc`。导入按 NFC+ASCII 折叠，随后 `unique()` 整包失败。`activeNameKey` 写入展示名而非折叠键。
- **复现**：添加 ABC 与 abc → 任何 .geocfg 均报同名歧义。同名替换本身对「张三/李四 + 张三/实验室」路径是对的（见 `ArchiveRoundTripTest`）。
- **最小修复**：活跃名唯一性与 `normalizedOptionName` 对齐。

### M4 二次 inspect 泄漏上一次 staging

- **位置**：`SettingsToolsViewModel.kt:44-53`
- **根因**：`archive.value=DataArchive.read(...)` 覆盖前不 `close()` 旧 `ValidatedArchive`。`clearPreview()` 才会关。
- **复现**：预览 geodata A 不取消，再选 B。
- **最小修复**：赋值前 `old?.close()`。

### M5 配置 UTF-8 用 REPLACE，数据包用 REPORT

- **位置**：`SettingsToolsViewModel.kt:51-52` vs `DataArchive.kt:178-179`
- **根因**：`ByteArray.toString(UTF_8)` 替换非法字节为 U+FFFD，损坏名称可进入配置。
- **复现**：.geocfg 含非法 UTF-8。
- **最小修复**：与 geodata 相同 `CodingErrorAction.REPORT`。

---

## Low

- **L1** `remaining==0` 时 0 字节文件可通过 `copy`（`AttachmentPolicy.kt:28-46`）。限制仍满足，无意义附件。
- **L2** UUID8 目录碰撞扩展名未写入 `usedFolders`（`DataArchive.kt:69-72`），极端三级碰撞才覆盖。
- **L3** 读取失败文案「无法读取附件」≠「无法读取该文件，请重新选择」。
- **L4** `apply` 在 DB commit 后若协程取消，文件清理正确（`DataTransfer.kt:105-113`），但 `withContext` 仍抛取消，UI 不当成功。Busy/BackHandler 挡住返回，仅 ViewModel `onCleared` 窗口。
- **L5** GitHub `ConnectException`/`SSLHandshakeException` 未映射为网络文案（`SettingsToolsViewModel.kt:103-104` 只捕 Timeout/UnknownHost）。

---

## 攻击项对照（已看代码，未装机）

| 项 | 结论 |
|---|---|
| 实际大小 / SIZE 元数据 | 不用 SIZE；`copy` 计数。通过 |
| 持续 0 读 | `emptyReads>100` 失败。通过 |
| 第 11 / 超限 / 同名 | 整批拒绝；内部 UUID；同名可共存。通过（文案见 M2） |
| A,B→A,C / 连续编辑 / 删除幂等 | 仓储正确；`UpgradeRepositoryTest` 覆盖。H1 是新建回放不是这条 |
| Tombstone + client_op_key | 不复活、无新事件。通过 |
| 历史 noop | `business()` 排除 updatedAt/选项 id。通过 |
| UUID/内容冲突 | 附件归属、blob 哈希、event canonical；失败回滚并清未引用 newKeys。通过 |
| ZIP bomb / 重复条目 / 根清单 / 坏 JSON | 上限、比率>1MiB、大小写重复、hash、identity。通过。多余**目录**见 M1 |
| Zip slip / 绝对路径 / `C:\` | `safePath` + canonical。通过 |
| 取消在 commit 后 | 文件侧正确；UI 见 L4 |
| 跨设备恢复 id | Room `nullif(?,0)` 写入 1..N；选项 id 只按活跃折叠名，0 命中则 null 且保留 snapshot。通过 |
| 同记录忽略 C | `targets`/`events`/`attachments` 按本地 UUID 过滤。通过 |
| 配置完全/同名 | 完全先停用再复用/插入；同名不新增。通过（本机大小写见 M3） |
| busy/导航 | Settings/Editor BackHandler + lock；检查更新走 `operation()`。代码层通过 |
| GitHub 403/404/坏 JSON/坏版本/无 APK | 有分支；无 APK 回落 `html_url`（`ReleaseVersionTest`）。**未联网** |
| 主线程 IO | 附件/导入导出/更新/解码在 `Dispatchers.IO`；Room suspend。未见主线程拷贝文件 |

---

## 未验证门禁（不得用设计或旧日志冒充）

1. 真机/模拟器：11 附件、10MiB+1、SAF、字体/小屏、busy 连点。
2. 进程死亡草稿恢复（H1）。
3. 联网：GitHub 无网/403/404/真实 latest JSON（StrictJson 能否吃完整 GitHub 对象）。
4. 连接测试 / 仪器 Migration；`upgrade_validation.log` 不含 connected。
5. 导出失败时 `deleteDocument` 是否误删用户原有 SAF 文档。
6. 本报告不证明 APK 为最终产物。

---

## 建议主 Codex 优先

1. H1 活跃行创建回放改为 edit。
2. M1 目录白名单边界。
3. M2 分项限额文案。
4. M3 本机名与导入同一套折叠。
5. M4/M5 inspect close 与严格 UTF-8。
