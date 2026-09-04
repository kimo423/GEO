# GEO 1.1.2 发布记录

- 修复提交（tag `v1.1.2` 指向此提交，不得移动）：`21d3b88c4d4cf51425daec019d5b9e996912e4b0`
- 提交说明：`fix: smooth navigation and transaction feedback`
- 本报告为后续仅文档提交；`main` 可能比 tag 多 1 个文档提交。

## 根因

- 保存/删除成功后调用的 `onBack` 受 `navigationLocked` 拦截；成功事件在锁解除的 Compose 重组前被消费，页面不退出。
- 删除成功后 `_deleted=true` 且未完成 pop，详情进入 `Deleted` 只显示转圈。
- NavHost 默认交叉淡入淡出同时绘制旧/新 destination，低端设备叠屏卡顿。

## 关键修复

- NavHost enter/exit/pop 全部 `Transition.None`。
- 用户返回仍受 in-flight 锁；成功完成走独立 pop，绕过用户锁，且幂等只 pop 一次。
- 成功 Snackbar 由 GeoApp Scaffold `SnackbarHostState` 在返回后的页面显示（已记入/已保存/已删除）。
- 失败留在当前页、解锁、页内失败 Snackbar。
- 收支记录时间：`transactionDate` 日期 + `createdAtMillis` 系统时区 LocalTime，格式 `yyyy年M月d日 HH:mm:ss`。

## 测试

- JVM：17 suites / 88 tests 全部通过
- lintDebug：0 error / 10 warnings
- assembleDebug：成功；APK `com.geo.ledger` versionCode 4 / versionName 1.1.2
- `connectedDebugAndroidTest` 因本机无硬件加速模拟器未执行，不视为通过

## Release / APK

- Tag / Title：`v1.1.2` / GEO 1.1.2（latest，非 draft，非 prerelease）
- Release：https://github.com/kimo423/GEO/releases/tag/v1.1.2
- Asset：https://github.com/kimo423/GEO/releases/download/v1.1.2/GEO.apk
- Latest：https://github.com/kimo423/GEO/releases/latest/download/GEO.apk
- raw：https://raw.githubusercontent.com/kimo423/GEO/main/version.json （versionCode=4）
- 大小：19,757,468 字节
- SHA256：`18F0160154DAE3865E5AF93F9F96CC39F0EAFDE39C909A7907F09C7499EAA738`
- 签名证书 SHA-256 与 v1.1.1 相同：`cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732`
- 未生成 keystore，未重新签名；未覆盖 v1.1.0 / v1.1.1

## 两阶段

1. 先 push tag `v1.1.2`，远端 main / raw 仍为 3。
2. 创建 Release 并上传 APK，固定 URL 与 latest URL 核验通过后才 push main。
3. 公开 raw 已返回 versionCode=4。
