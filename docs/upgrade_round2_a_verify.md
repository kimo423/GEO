# GEO 1.2 Round2 复检（Grok Agent A）

- 角色：Implementation Reviewer；对照 `docs/upgrade_round2_a.md`，不把 `upgrade_round2_resolution.md` 当证据
- 仅写入本文件。未改生产代码、未 git、未联网、未 Gradle、未 connected/install
- 宿主 Compose / `docs/ui-review` 不是真机证据
- `docs/upgrade_final_validation.log` 现止于 Gradle `BUILD SUCCESSFUL in 1m 46s`（74 tasks）；`docs/build_status.md` 未认证 1.2 计数。本文件**不**作最终全量通过

## 门禁

**IMPLEMENTATION PASS**（真机门禁另列）

Critical = 0；Major = 0；Minor = 1。

原 A：Major 1 + Minor 6。源码复检均已对应用户可见缺陷；余 1 条文档链。未重开数据层设计。

---

## 原发现处置（独立核实）

| ID | 结论 | 依据（非决议书） |
|---|---|---|
| M1 | 基本已修；余文档链见 Minor | README 已无「本次交付 81 tests / 1.1.0 code 2」。`:31` 与 `app/build.gradle.kts:16-17` 均为 1.2.0/6。`build_status.md:3-11` 标明未认证并归档 1.1 |
| m1 | 已修 | `AttachmentPolicy.kt:13-15` 三句与 §15 一致。`PrivateBlobStore.kt:20-21`：`limit<MAX_FILE`→`TOTAL_ERROR` 否则 `FILE_ERROR` |
| m2 | 已修 | `AddTransactionScreen.kt:204,210` 失败文案 + `canSave` 含 `!attachmentLoadFailed`；`AddTransactionViewModel.kt:273,352` `save()` 直接 return |
| m3 | 已修（见唯一性） | UI/仓库/导入查重均 `normalizedOptionName`；不改写遗留键 |
| m4 | 已修 | `AttachmentList.kt:47` `有${count}个附件`（无空格） |
| m5 | 已修（适配器保留） | 见启动路径 |
| m6 | 已修（源码） | Home/Bills/Add/Detail/Settings 使用 `GeoSpacing.Page`。视觉 polish 归真机 |

未把 ColumnInfo 默认值、墙钟最新 audit、空文件限额、Host 截图记为缺陷。

---

## 唯一性（折叠查重 + 遗留键）

新写入：`OptionNameValidator.kt:26-28` 与 `LedgerRepository.kt:188,208,235,255` 用 NFC+trim+ASCII 小写比配。`Valid.normalized` 仍为 trim 后展示名（`:23-31`）。

索引键：`Entities.kt:104-105` `activeOptionNameKey` = 展示名或 null。`ConfigTransfer.kt:72-82` 写入导入展示名。`MIGRATION_1_2`（`GeoDatabase.kt:101,113-116`）`GROUP BY name`（精确名）后 `active_name_key=name`。3→4 不改选项键。

**不会静默合并大小写/Unicode 异名用户行。** 本机 `ABC`+`abc` 并存时 `ConfigTransfer.kt:59-62` 抛「请先重命名消除歧义」，不合并。导入包内折叠重名 `:37` 拒绝。精确同名 1→2 只停用多余活跃行（既有 v2 契约，非折叠合并）。

复现（应拒绝、不得并行）：设置已有 `abc`，再新增 `ABC`；或本机同时活跃 `ABC`/`abc` 后导入配置。

---

## 遗留更新器

- `GeoApp.kt` / `MainActivity.kt` / `GeoApplication.kt` 无 `AppUpdateGate`、无 `checkOnce`、无 `version.json` 请求。`UpgradeUiContractsTest.kt:10-13` 断言导航未挂、无 `init {`
- `AppUpdateViewModel.kt:32-35` 构造不联网；`checkOnce` 仅测试调用
- 生产检查：`SettingsScreen.kt:102` → `SettingsToolsViewModel.kt:98-124` 点击后 GitHub Releases
- `AppUpdateDialog.kt:26-28` 适配器仍在主源码，默认 `HttpUrlConnectionFetcher`，**当前无启动挂载**

---

## Remaining

### Minor

**r1. README 把 1.1 的 `test_report.md` 标成 1.2 当前证据**

- `README.md:109-111` 「Current final-run evidence」链到 `docs/test_report.md`
- `docs/test_report.md:1-6` 日期 2026-09-04，JVM **81 / 0**，无 1.1 归档条（与已归档的 `build_status.md` 不同）
- 复现：打开 README「Validation for 1.2.0」再打开 test report
- 建议：给 test_report 加 1.1 归档头，或 README 只指向 `build_status.md`
- 不构成版本表矛盾；不回退 M1 主修复

Critical / Major：无。

---

## 真机门禁（不计入实现 PASS/REVISE）

1. 无设备；未 install / connected / SAF / 预览手势 / 更新真网
2. Robolectric Room/Compose ≠ 手机 SQLite/UI
3. 全量日志未由 `build_status.md` 认证，禁止当作 1.2 最终 JUnit/lint/APK 门禁
4. Host `docs/ui-review/*.png` 不是实机
