# GEO 1.2 Round3 终稿补记（post-fix，相对 E27F 快照）

全文审查沿用下方先前快照；本段只复检两处增量。未改生产代码、未 git/联网/Gradle/真机。

**IMPLEMENTATION PASS**。Critical=0；Major=0；Minor=0。不作 1.2 发布批准。

| 项 | 本轮值 |
|---|---|
| `sourceDigest` | `54720AEC4A3FAEEE88910589D601AD0186B70A84F67CAEED517A2158000B893E` |
| 清单 109 | 路径仍在盘上；相对 E27F 仅两文件变更（已读源）。未 shell 重算全量 SHA |
| APK SHA-256 | `712C7192258F1C6D1DCF110E4FE68C7829D6B649847CF1FDBF5E7A2C811F0CD9`（清单/`build_status`/`upgrade_delivery_report` 一致） |
| 签名 | `cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732`（协调器复核未变） |
| JUnit / 构建 | 29 suites / **127 tests / 0 failed**；`upgrade_final_validation.log`：`BUILD SUCCESSFUL in 34s`，exit 0 |

Delta：`SettingsToolsViewModel.applyImport` 成功后 `archive=null` 并以 `NonCancellable+Dispatchers.IO` 关闭 pack（B Low1）。`SettingsToolsViewModelTest` 断言成功导入清空 staging。宿主 127 测≠真机。

---

# 先前快照：GEO 1.2 Round3 最终实现审查（Grok Agent A）

- 角色：Implementation Reviewer；对照用户需求全文（含 §107 共 49 项）与当前源码
- 仅写入本文件。1.1 旧稿已在 `docs/grok_implementation_review_v1.1_archived.md`
- 未改生产代码、未 git write/push/reset、未联网、未 Gradle、未 connected/install
- 宿主 127 测与 `docs/ui-review/*.png` **不是**真机证据；adb 空；**不作最终全部门禁通过、无 1.2 推送**
- 生产源码按协调器声明已冻结。不把导航未挂兼容更新器、或保留精确遗留 `active_name_key` 当新 blocker

## 门禁

**IMPLEMENTATION PASS**（真机门禁另列）

Critical = 0；Major = 0；Minor = 0。

Round2 原 A：Major 1 + Minor 6；复检余 Minor 1（文档链）。本轮独立核实均已闭合。未重开 ColumnInfo 默认值、墙钟最新 audit、空文件限额。

## 复核指纹（只读，未重跑测试）

| 项 | 本轮读到的值 |
|---|---|
| `sourceDigest` | `E27F2F65EE3897368B6FD844E3FC3DEF1027A1AAAC24C4F316A9EAF969D0291D` |
| 清单 109 个条目 SHA-256 | 与当前文件 **0 处不符** |
| `dist/GEO-debug.apk` | 20,178,325 B；SHA-256 `829E1F5C864C55C2C5771BC1913351B469CA2A26DE07C6AF957323C04AFEF487`（与清单/`build_status`/`upgrade_delivery_report` 一致） |
| 签名 SHA-256 | `cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732`，与 `dist/GEO.apk` **同一** Android Debug 证书 |
| JUnit XML | 29 suites / **127 tests / 0 failed / 0 errors / 0 skipped**；时间戳 2026-09-09T06:07:31–53Z |
| lint | `lint-results-debug.txt`：**0 errors, 12 warnings** |
| 日志 | `docs/upgrade_final_validation.log`：`BUILD SUCCESSFUL in 38s`；含 `assembleDebug`、`testDebugUnitTest`、`lintDebug`、`compileDebugAndroidTestKotlin` |

`versionName/versionCode`：`app/build.gradle.kts:16-17` 与 README 表均为 **1.2.0 / 6**；Room **4**。

## Round2 修复是否仍成立

| ID | 结论 | 依据 |
|---|---|---|
| M1 | 已修 | README `:31` 为 1.2.0/6；Validation 指向 `build_status`/`test_report`，二者均归档 1.1 的 81 测 |
| m1 | 已修 | `AttachmentPolicy.kt:13-15` 三句与 §15 一致；`PrivateBlobStore.kt:20-21` `limit<MAX_FILE`→`TOTAL_ERROR` |
| m2 | 已修 | `AddTransactionScreen.kt:204,210`；`AddTransactionViewModel.kt:352` |
| m3 | 已修 | 新写入 NFC+trim+ASCII 折叠；索引键仍为展示名；歧义拒绝不合并 |
| m4 | 已修 | `AttachmentList.kt:47` `有${count}个附件` |
| m5 | 已修（适配器保留） | `GeoApp.kt` 无 `AppUpdateGate`；`UpgradeUiContractsTest.kt:10-13` |
| m6 | 已修（源码） | Home/Bills/Add/Detail/Settings 使用 `GeoSpacing.Page` |
| r1 | 已修 | `test_report.md:1-5` 现为 1.2 的 127；`:37` 起明确归档 1.1 |

所列 README/`build_status`/`test_report`/`known_issues`/`data_format`/`upgrade_delivery_report`/清单 **无把 1.1 证据冒充 1.2 当前门禁** 的假陈述。`dist/SHA256SUMS.txt` 仍写旧 `3004B4…` 对应 `GEO-debug.apk`，权威哈希以清单与 `build_status` 为准，不计入缺陷。

## §107 四十九项

1–11 旧功能/非破坏迁移/永久 UUID/逻辑删+Audit/0–10/10MiB/30MiB/私有 blobs/不存 URI/流式 size/SHA-256：**通过（宿主）**。`GeoDatabase.kt:55-94` 无 `fallbackToDestructiveMigration`；3→4 逐行 UUID 后 UNIQUE。限额 `10`/`10485760`/`31457280`。
12–15 纸夹/数量/Detail 查看/保存到设备：**源码有，真机未证**。`HomeScreen.kt:234`/`BillsScreen.kt:385`；`AttachmentList.kt` 查看+`CreateDocument`。
16–20 换附件不毁 Audit / Edit Before-After / Delete Before / 连续独立 Event / Immutable：**通过**。`LedgerRepository.kt:132-167,173-184`；`HistoryDao` 无单条 update/delete。
21–22 同一 Event 卡片 / Changed Fields：**源码有，真机未证**。`AuditScreen.kt:38-65` 单卡 + ↓ + 附件增删留。
23–32 geocfg / geodata ZIP / 可读 Folder+UUID8 / 无附件不建目录 / sanitize / Manifest 身份 / blob 只写一次 / 导出 Audit+真实字节：**通过**。`DataArchive.kt:40-117`。
33–42 配置全量/同名不新增；数据全量/同 UUID 不新增 C；eventUuid 冲突失败；先校验后写；失败不污染；Zip Slip；导入 SHA-256：**通过**。`ConfigTransfer.kt:66-83`；`DataTransfer.kt:33-104`；`DataArchive.read` 不碰 live DB。
43–45 GitHub 点击检查 / SemVer / 源清单仅 INTERNET：**源码通过；真网未测**。`SettingsToolsViewModel.kt:98-123`；`ReleaseVersion.kt`；`AndroidManifest.xml:4`。
46 UI 系统性 polish：**源码 token 已用；视觉归真机**。
47–49 unit/migration/assemble：**宿主 XML+日志通过；非手机 SQLite/UI**。

复现应拒绝：设置已有 `abc` 再新增 `ABC`；或本机同时活跃 `ABC`/`abc` 后导入配置（`ConfigTransfer.kt:59-62`）。

## 真机门禁（不计入 IMPLEMENTATION PASS/REVISE）

1. 无设备；未 install / connected / SAF 选文件 / 第三方查看器 / 进程死亡 / 更新真网 / 帧耗时。
2. Robolectric Room/Compose ≠ 手机 SQLite/文件系统/手势。
3. Host 截图不是实机。`GeoDatabaseMigrationInstrumentedTest` 未在设备执行。
4. 禁止把本报告或 127 宿主测试当作 1.2 最终全部门禁或发布批准。
