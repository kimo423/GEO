# GEO 1.2 Round3 Agent B 终审 — 修复后附录

日期：2026-09-09。仅核 E27F… 快照后两处源码；先前全文作先前快照保留。未跑 Gradle/设备/网络。
**implementation: PASS**　**REVISE: 0**　Critical **0** / High **0** / Medium **0** / Low **0**

## 最新哈希（磁盘核验）

| 项 | 值 |
|---|---|
| sourceDigest | `54720AEC4A3FAEEE88910589D601AD0186B70A84F67CAEED517A2158000B893E` |
| 清单 109 entries | **109/109** SHA-256 一致，0 mismatch |
| source+dist debug APK | `712C7192258F1C6D1DCF110E4FE68C7829D6B649847CF1FDBF5E7A2C811F0CD9`（20178362B，两份相同） |
| 宿主 XML | **29 suites / 127 tests / 0 fail / 0 error / 0 skip**；`SettingsToolsViewModelTest` 1/0 |
| lint / 校验 log | **0 errors / 12 warnings**；`BUILD SUCCESSFUL in 34s`，记载 exit 0 |

Signer `cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732` 协调方复核未变，本附录不探测 JAR。

## Delta：原 Low1 → 关闭

`SettingsToolsViewModel.applyImport` 在 `archive.value=null` 后 `withContext(NonCancellable+Dispatchers.IO){ pack.close() }`（`:75-76`），与已接受 inspect 关包同模式。宿主测试成功 apply 断言 staging `import-*` 不存在；**不是强制取消，不是设备证明**。无新增 C/H/M。设备/杀进程/实网仍未验证。终裁 **PASS**。

---

# 【先前快照 E27F…】GEO 1.2 Round3 Agent B 终审（对抗 Bug Hunt）

日期：2026-09-09。工作区仅 `C:\Users\SUN\Desktop\GEO`。
**implementation: PASS**
Critical **0** / High **0** / Medium **0** / Low **1**

本轮独立核验 Round2 裁决与修复本身。先前 focused verification 因 max turns 无报告，**不视为通过**。本会话未跑 Gradle、未装机、未连设备、未访问网络、未改生产代码。旧 1.1 报告已在 `docs/grok_bug_hunt_v1.1_archived.md`。

---

## 证据哈希（只读）

| 项 | 值 |
|---|---|
| sourceDigest（清单记载） | `E27F2F65EE3897368B6FD844E3FC3DEF1027A1AAAC24C4F316A9EAF969D0291D` |
| 清单 entries | **109/109** 与磁盘 SHA-256 一致，0 mismatch |
| `dist/GEO-debug.apk` | `829E1F5C864C55C2C5771BC1913351B469CA2A26DE07C6AF957323C04AFEF487` |
| `app/build/outputs/apk/debug/app-debug.apk` | 同上（与 `upgrade_validation_manifest.json` / `docs/build_status.md` 一致） |
| 宿主测试 XML | **29 suites, 127 tests, 0 fail / 0 error / 0 skip** |
| lint XML/TXT | **0 errors / 12 warnings**（GradleDependency7, ConfigurationScreenWidthHeight1, KaptUsageInsteadOfKsp1, ObsoleteSdkInt1, UsableSpace1, UseKtx1） |
| `docs/upgrade_final_validation.log` | `BUILD SUCCESSFUL in 38s`，进程 exit 0；SHA-256 `52F172293FD4BDB101F15DBBAB83337F7A3E7F57D403635F533D50CE0D997B34` |

`dist/GEO.apk` 现哈希 `CC8AE599…`，为历史包。`dist/SHA256SUMS.txt` 仍写旧 `3004B426…`，**不是当前 debug 产物校验**。debug APK 无 JAR `META-INF/*.RSA`（v2/v3）；本轮未跑 apksigner，**未独立抽出证书指纹**。文档记载 signer `cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732` 与旧包相同——记为文档声明，非本轮工具证明。

---

## Round2 修复核验（关闭，非残留）

### H1 活跃创建键回放 → 已修

- `LedgerRepository.kt:90-93`：`id==null` 且 tombstone 则返回 id、不复活；随后 `require(existing?.isDeleted != true)`。
- 同文件 `:110,:132,:142-151`：活跃已存在行按 **edit**；`business()` 相同则 noop。
- `AddTransactionViewModel.kt:377-382`：成功后写入 `ARG_TRANSACTION_ID`。
- 宿主：`UpgradeRepositoryTest.restoredCreationDraftEditsActiveRowButSameDraftIsNoop`（A,B→A,C，同稿 noop）；`auditContinuousNoopDeleteAndPrivateAttachments` 第 129 行 tombstone+`client_op_key` 不复活。XML 该套件 4/0。

### M1 ZIP 目录前缀 → 原 Python 探针误判；现加固

- 不在本机起 Java 证明 slash。按裁决：`ZipEntry.isDirectory` 名含尾斜杠。
- `DataArchive.kt:210-216`：允许目录 = 声明文件路径的显式父前缀集合（带 `/`），非 `startsWith` 误伤。
- `ArchiveSecurityTest.kt:43-44,63-64`：真实 ZIP 拒绝 `attach/`、`manifest.json/`。

### M2 附件限额文案 → 已修

- `AttachmentPolicy.kt:13-22,34-50`：三条中文；`copy` 用调用方 `limitMessage`。
- `PrivateBlobStore.kt:20-21`：`limit<MAX_FILE` → 总额文案，否则单文件文案。
- `AddTransactionViewModel.kt:114-126`：`min(MAX_FILE, remaining)`。
- `TransferPrimitivesTest.limitFailuresGiveDistinctMessages` 通过。

### M3 选项归一化对齐导入 → 已修（旧键不改写）

- `normalizedOptionName`：`ConfigTransfer.kt:13-14` NFC + ASCII 小写。
- UI：`OptionNameValidator.kt:26-28`；写入：`LedgerRepository.kt:188,208,235,255`。
- `activeOptionNameKey` 仍写展示名（`Entities.kt:104-105`），unique index 未改折叠键。
- `UpgradeRepositoryTest.activeOptionNamesUseImportNormalizationWithoutRewritingOldRows`：`ABC`/` abc ` 拒绝；NFC `e\u0301` 拒绝。

### M4/M5 预览所有权 + 严格 UTF-8 → 已修，宿主测试通过

- `SettingsToolsViewModel.kt:44-68`：替换前 NonCancellable close 旧包；失败 `pending?.close()`；config 用 `StrictJson.decodeUtf8`。
- `StrictJson.kt:5-8` / `DataArchive.kt:180-181`：`CodingErrorAction.REPORT`。
- `SettingsToolsViewModelTest.replacingPreviewClosesOldStagingAndMalformedConfigKeepsCurrentPreview` XML 1/0。
- `TransferPrimitivesTest.strictUtf8RejectsDamagedConfigurationNames` 对 `C3 28` 抛 `CharacterCodingException`。

L2 目录回退已 `usedFolders.add`（`DataArchive.kt:69-73`）。L3 不可读文案、L5 `ConnectException`/`SSLException` 映射（`SettingsToolsViewModel.kt:118-119`）源码在。L1 空文件按裁决允许。

---

## Residual（源码可定位）

### Low（1）

**L1 导入成功后取消可能跳过 staging close**

- 位置：`SettingsToolsViewModel.kt:73-77`（`onCleared` `:125-127` 只 close `archive.value`）。
- 复现：`apply(pack)` 已提交 → `archive.value=null` → 协程在 `pack.close()` 前取消（离开 Settings / ViewModel clear）。DB 已正确保留；`filesDir/staging/import-*` 可能残留。非数据损坏，非 H1。
- 对照：inspect 路径 `:68` 有 `pending` finally。无宿主测试覆盖 apply 取消。

无其他已证 Critical/High/Medium。不把未跑的设备路径记为失败。

---

## 未验证（单独列出，非 pass/fail）

1. 真机/模拟器：SAF 选文件、11 附件、10MiB+1、30MiB、第三方查看器、小屏/字体缩放、busy/返回。
2. 进程杀死：创建回放、导入 commit 后杀进程、Activity recreation（host Room 不是 process-death）。
3. 实网 GitHub：403/404/超时/真 latest JSON；`checkUpdate` 体仍 `toString("UTF-8")` REPLACE（`:113`），与 inspect REPORT 不同，无实网证据。
4. `connectedDebugAndroidTest` / 真机 Migration 1..3→4：**未跑**（adb 空）。host `UpgradeRepositoryTest` 迁移不是手机。
5. 导出失败时 `deleteDocument` 是否删用户原 SAF 文档。
6. 帧性能 / Compose 流畅度；host `UpgradeComposeSmokeTest` 的 `decorView.draw` 不是设备帧证据。
7. debug APK 证书指纹（无 JAR RSA；未跑 apksigner）。

用户完成门仍因无手机未完成。宿主 127 绿不是 SAF/设备/杀进程证据。

---

## 裁决

Round2 接受项在冻结源码与 XML 中成立。实现 **PASS**（Critical=0, High=0）。Low 不挡门。设备/杀进程/网络流保持 **未验证**。
