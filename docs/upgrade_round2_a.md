# GEO 1.2 Round2 Implementation Review（Grok Agent A）

- 角色：Implementation Reviewer（实现审查，非设计批准）
- 范围：仅 `C:\Users\SUN\Desktop\GEO`
- 对照：用户需求全文（含 §107 共 49 项）；`docs/upgrade_1.2_design.md` Revision 2/3（实现契约）；`docs/data_format.md`
- 代码：app **1.2.0 / versionCode 6**，Room **4**，主写入者 Codex
- 本 Agent 只写入本文件。未改生产代码、未 commit/push/reset、未连网、未 connected/install、未并行 Gradle。
- 未把「ColumnInfo 默认值已匹配却要求重建表」或「墙钟最新 audit 必须等于当前账本」记为缺陷（Rev2/3 已否决）。

## 门禁

**IMPLEMENTATION REVISE**

Critical = 0；Major = 1；Minor = 6。

Major 为 README 交付证据与 1.2.0 事实不符（§107 文档真实性）。核心账本/附件/审计/导入替换/迁移 SQL 未发现 Critical。无真机/模拟器 UI 证据，不以 Compose 截图或 Robolectric 冒充实机。

## 本轮未当作缺陷

- `MIGRATION_3_4` 使用 `DEFAULT ''` / `DEFAULT 0`，与 `@ColumnInfo(defaultValue)` 及 `4.json` 一致；不要求 table rebuild。
- 审计按 `occurredAtMillis DESC, eventUuid DESC` 展示；同记录合并后不以墙钟最后一条必须等于 `transactions.json` 为不变量。

---

## Critical

无。

---

## Major

### M1. README「本次交付」测试/版本证据仍是 1.1.0/code 2，与当前 1.2.0/code 6 矛盾

- 需求：§107「文档真实性」；§113 README 必须反映本升级
- 证据：
  - `README.md:31` 表格已写 versionName/versionCode **1.2.0 / 6**
  - `README.md:109-111` 「Tests (this delivery)」仍写 **81 tests**、`assembleDebug` success（**1.1.0 / versionCode 2**）
  - `app/build.gradle.kts:16-17` 实际为 6 / 1.2.0
  - `app/src/test` 现有 `@Test` 远多于 81（含 Upgrade/Archive/附件/SemVer 等）
- 复现：打开 README「Tests (this delivery)」与版本表对照
- 根因：1.2 功能段已更新，交付证据段未改，沿用 1.1.0 发布说明
- 修复：把该段改为 1.2.0/6；测试数/lint/assemble 只写本轮真实结果；标明 Robolectric≠真机、connected 未跑。同步 `docs/build_status.md`（本 Agent 不改）

---

## Minor

### m1. 附件超限文案与 §15 不一致（MiB vs 指定中文 MB 句）

- `AttachmentPolicy.kt:14-16`、`AddTransactionViewModel.kt:114`：`每笔最多 10 个附件` / `10 MiB` / `30 MiB`
- 需求句：`每笔账最多添加 10 个附件`、`单个附件不能超过 10 MB`、`全部附件总大小不能超过 30 MB`
- 流式超限还混用一句 `文件超过允许大小（单附件 10 MiB，总附件 30 MiB）`（`AttachmentPolicy.kt:43`），11 个与 10MiB+1 与 30MiB+1 不易区分
- 修复：按 §15 三句映射；copy 超限按 `limit==MAX_FILE` vs remaining 分支

### m2. 编辑页附件加载失败时保存按钮仍可点，点了无反馈

- 复现：编辑账单，SavedState/DB 附件 hydrate 抛错
- `AddTransactionViewModel.kt:269-273,349-350`：`attachmentLoadFailed=true` 且 `save()` 直接 return
- `AddTransactionScreen.kt:208`：`canSave` 只与 `!attachmentBusy`，不含 `attachmentLoadFailed`
- 根因：保护原附件的禁写未接到 UI enabled
- 修复：`canSave` 加上 `!attachmentLoadFailed`，失败态保持可见

### m3. 配置 `active_name_key` 未用与匹配相同的 NFC+ASCII 折叠

- `ConfigTransfer.kt:72-75`：`activeOptionNameKey(item.isActive, item.name)` 用展示名
- 匹配用 `normalizedOptionName`（NFC+trim+ASCII 小写，`ConfigTransfer.kt:13-14`）
- 本地 `OptionNameValidator` 仍大小写敏感（`OptionNameValidator.kt:23-28`）
- 复现：同名替换把 `ABC` 写成 `abc` 后，UI 仍可再新增 `ABC`，下次配置导入会因「同名歧义」失败
- 修复：写入 `active_name_key` 与本地查重都用 `normalizedOptionName`

### m4. 纸夹 `contentDescription` 与 §17 不完全一致

- `AttachmentList.kt:47`：`有 $count 个附件` → 「有 3 个附件」
- 需求：`有3个附件`
- 修复：改成无空格模板，1 个与多个都保留合并 semantics

### m5. 启动路径无联网，但旧 `version.json` 客户端仍留在主源码

- 生产：`GeoApp.kt` 无 `AppUpdateGate`；`AppUpdateViewModel` 无 `init` 联网；设置页 `SettingsToolsViewModel.kt:85-108` 才打 GitHub Releases
- 残留：`ui/update/AppUpdateDialog.kt`、`update/AppUpdateHttp.kt` 默认 `MANIFEST_URL`（`version.json`）
- `UpgradeUiContractsTest.kt:11-13` 已断言导航未挂 Gate
- 修复：删除或移出主路径，避免以后误挂回启动

### m6. Design token 已定义但页面未使用

- `Theme.kt:15-25` `GeoSpacing` 无引用；Home/Bills/Settings 仍写死 `20.dp`/`16.dp`
- 视觉值大致接近，未在真机确认「系统性 polish」
- 修复：关键页面改用 token；实机再看字号/小屏

---

## §107 四十九项

| # | 项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 旧功能未破坏 | 源码保持；无真机回归 | 余额/查询/选项仍走原计算器；tombstone 已过滤 |
| 2 | Migration 非破坏 | 通过（契约） | `GeoDatabase.kt:55-78`；无 `fallbackToDestructiveMigration` |
| 3 | 旧行永久 UUID | 通过 | 逐行 `UUID.randomUUID()` + 非空/唯一校验后建索引 |
| 4 | 删除不破坏 Audit | 通过 | 逻辑删 + DELETE 事件同一事务 `LedgerRepository.kt:171-182` |
| 5 | 0～10 附件 | 通过 | `MAX_COUNT=10`；多选超限整批拒绝 |
| 6 | 单文件 ≤10 MiB | 通过 | `10L*1024*1024`；流式 `size<=limit` |
| 7 | 总计 ≤30 MiB | 通过 | `MAX_TOTAL`；`remaining=MAX_TOTAL-existing` |
| 8 | 复制到私有存储 | 通过 | `filesDir/blobs/<random UUID>` |
| 9 | 不依赖原 URI | 通过 | 立即 `openInputStream` 复制，不保存 content URI |
| 10 | 流式 size | 通过 | 64KiB；不信 `OpenableColumns.SIZE` |
| 11 | SHA-256 | 通过 | 流式摘要；已知向量测试 |
| 12 | 首页/Bills 纸夹 | 源码有；真机未证 | `TransactionAttachmentBadge` |
| 13 | 多附件显示数量 | 源码有 | `count>1` 才显示数字 |
| 14 | Detail 查看全部 | 源码有 | `DetailAttachments` |
| 15 | 保存到设备 | 源码有 | `CreateDocument` + 流式 copy |
| 16 | 删/换附件不破坏 Audit | 通过 | 只 deactivate；blob 不删 |
| 17 | Edit Before/After | 通过 | 事务内读 before |
| 18 | Delete 有 Before | 通过 | after=null |
| 19 | 每次 Edit 独立 Event | 通过 | 连续两次独立插入 |
| 20 | Audit Immutable | 通过 | DAO 无单条 update/delete |
| 21 | Before/After 同一 Event UI | 源码有；真机未证 | `AuditCard` 单卡 + ↓ |
| 22 | Changed Fields | 通过（略多） | 含附件增删；另含「时间/删除状态」 |
| 23 | geocfg | 通过 | `GEO_CONFIG` / v1 |
| 24 | geodata 是 ZIP | 通过 | 非 Base64 JSON |
| 25 | 有附件独立可读 Folder | 通过 | `attachments/<label>_uuid8/...` |
| 26 | 无附件不建 Folder | 通过 | 无引用不 `getOrPut` |
| 27 | Folder 含 UUID8 | 通过 | 冲突再延长 UUID |
| 28 | Folder 安全化 | 通过 | 非法字符/`CON` 等 |
| 29 | Manifest 才是映射 | 通过 | 文件夹名不参与身份 |
| 30 | 当前/历史不重复写 blob | 通过 | `blobPaths` 一 blob 一 entry |
| 31 | 导出含 Audit | 通过 | `audit_logs.json` |
| 32 | 导出真实附件 bytes | 通过 | STORE + 再哈希 |
| 33 | Config Full Replace | 通过 | 先停用再 reuse/insert |
| 34 | Config 同名不新增 unmatched | 通过 | `MATCHING_ONLY` 不 insert |
| 35 | Data Full Replace | 通过 | 清 audit→relations→tx→blobs，再按序插入 |
| 36 | Data 同记录不新增 unmatched | 通过 | 交集过滤 C |
| 37 | Data 匹配用 transactionUuid | 通过 | |
| 38 | Audit 匹配用 eventUuid | 通过 | 规范内容不等则整单失败 |
| 39 | Import 先验证后写 | 通过 | `DataArchive.read` 不碰 live DB |
| 40 | Import 失败不污染 DB | 通过 | 冲突测：行/事件/新 blob 回滚 |
| 41 | Zip Slip | 通过 | `..`/`/`/`\`/`:`/绝对/大小写重复 |
| 42 | SHA-256 导入校验 | 通过 | JSON 与 blob |
| 43 | GitHub 更新检查 | 源码有；未打真网 | 仅点击；`/releases/latest`；SemVer |
| 44 | SemVer | 通过 | `1.0.9<1.0.10`；拒 prerelease |
| 45 | INTERNET 只服务检查更新 | 通过 | 唯一 `uses-permission`；启动无 Gate |
| 46 | UI 系统性 polish | 未证实机 | token 存在但未用；Host Compose 烟测≠真机 |
| 47 | unit tests PASS | 不作为本轮最终证据 | 见验证缺口 |
| 48 | Migration tests PASS | 宿主有 1/2/3→4 源；结果非最终 | Robolectric，非手机 |
| 49 | assembleDebug PASS | 不作为本轮最终证据 | 见验证缺口 |

---

## 焦点契约（代码结论）

**真实限额：** `10` / `10485760` / `31457280`。多选超限整批回滚新文件。0 字节允许。

**私有文件：** `PrivateBlobStore.put` 随机 key；`FileProvider` 仅 `blobs/`；备份 XML 排除 database + blobs + staging。用户文件名不当路径。

**快照身份 / no-op：** `business()` 去掉 `updatedAtMillis` 与本地选项 ID；无变化不写 EDIT。创建幂等碰到墓碑只返回原 ID、不复活（`LedgerRepository.kt:91-92`）。删除已删 no-op。

**迁移默认值：** SQL `DEFAULT ''/0` 与实体/4.json 一致；逐行 UUID；UNIQUE 在填完之后。

**替换语义 / 原子：** 全量不改人员分类、不造 DELETE 海。同记录 A 留 B 换 C 丢。blobUuid≠storageKey；全量只用新 key。失败只删「DB 未引用的本次 newKeys」，提交后取消不删已引用文件。

**过期预览：** 提交时重读本地 UUID 与 eventUuid 规范冲突（`DataTransfer.kt:52-57`）。

**启动无网络：** `MainActivity`/`GeoApp`/`GeoApplication` 无更新请求。

---

## 验证缺口（单独列出，不当作已通过的实机证据）

1. **未跑 connected / 真机 / 模拟器。** 无 SAF、键盘、字体缩放、小屏、附件预览手势证据。
2. **`UpgradeRepositoryTest` / `ArchiveRoundTripTest` 经 Robolectric 跑 Room，不是手机 SQLite/文件系统。**
3. **`app/build/test-results/testDebugUnitTest` 在审查时被后续任务覆盖，目录一度只剩 `UpgradeComposeSmokeTest.xml`。** 不可当全量最终 JUnit 清单。`docs/upgrade_validation.log` 末次完整 `lintDebug` 为 BUILD SUCCESSFUL（含 `testDebugUnitTest`、`assembleDebug`），协调器可能仍在跑，不当最终门禁。
4. **`GeoDatabaseMigrationInstrumentedTest` 仍以选项/client_op_key 为主，UUID 列正确性依赖宿主测试；且 connected 未执行。**
5. **GitHub Releases 真网（空仓/403/404/最新 JSON）未测。** 远程当时声明为空。
6. **Host Compose 烟测**（`UpgradeComposeSmokeTest`）只断言设置/空历史/返回文案，且曾出现失败后转绿，有不稳定风险；截图不是实机。

---

## 主路径已对齐、不要回退的实现

- 显式 `MIGRATION_1_2/2_3/3_4`，DB 4，无 destructive fallback
- 逻辑删除 + 活跃 DAO + 计算器 `filterNot isDeleted`
- 附件/审计 FK RESTRICT；审计 append-only
- 导入：私有 staging 校验 → 新 key 晋升 → 单事务；全量/同记录语义与 `data_format.md` 一致
- Blob STORE / JSON DEFLATE；10MiB 全零回归在 `ArchiveSecurityTest`
- 检查更新仅设置页；SemVer 数值比较；无 `REQUEST_INSTALL_PACKAGES`

协调器修复 M1 后，本审查可在不重开数据层设计的前提下复检文档；UI/真机仍须另证。
