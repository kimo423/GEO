# GEO 1.1.3 发布记录

- 修复提交（tag `v1.1.3` 指向此提交，不得移动）：`3a2ccbae03930b9b784409ac3d89d90bdfd16111`
- 提交说明：`fix: clipped directional navigation and off-main ledger work`
- 本报告为后续仅文档提交；`main` 比 tag 多 1 个文档提交。

## 根因

- v1.1.2 将 NavHost enter/exit/pop 全设为 `Transition.None`，首帧仍在点击帧合成且无动画遮挡，切换更卡。
- `LedgerObserver.withRunningBalances` / `HomeDashboard.from` / `BillsQuery.groups` 在 ViewModel collector（Main）上整表计算，Home/Bills/Detail 各自重复。

## 关键修复

- 220ms、按方向的水平平移（无淡化），`clipToBounds` + `SizeTransform(clip)`，避免两页叠屏。
- `distinctUntilChanged → map(observe) → flowOn(Default) → shareIn(appScope)`；Home/Bills Ready 派生 `withContext(Default)`。
- 保存/删除反馈与 `HH:mm:ss` 未改。

## 测试

- JVM `testDebugUnitTest`：19 suites / 100 tests，0 fail / 0 error / 0 skip（XML）
- lintDebug：0 error / 10 warnings
- assembleDebug：成功（debug APK 仅作证书对照，**不是** Release 资产）
- assembleRelease：成功，产出 `app-release-unsigned.apk`；zipalign 后用本机 Android Debug keystore **外部签名**（未写入仓库，未改 minify/R8）
- `connectedDebugAndroidTest` 未执行（无加速模拟器/未连接真机），不视为通过；真机流畅度待用户验收

## Release / APK

- Tag / Title：`v1.1.3` / GEO 1.1.3（latest，非 draft，非 prerelease）
- Release：https://github.com/kimo423/GEO/releases/tag/v1.1.3
- Asset：https://github.com/kimo423/GEO/releases/download/v1.1.3/GEO.apk
- Latest：https://github.com/kimo423/GEO/releases/latest/download/GEO.apk
- raw：https://raw.githubusercontent.com/kimo423/GEO/main/version.json （versionCode=5）
- 自有资产恰为 `GEO.apk`（13,000,422 字节）
- SHA256：`CC8AE599AD259CC2EDB671DA9DF79D130D9D7607135CDF3098056C5A8DB534E4`（latest / tagged / 本地一致）
- aapt：`com.geo.ledger` versionCode 5 / versionName 1.1.3
- apksigner：v2+v3 Verifies
- 证书 SHA-256 与 v1.1.2 / `GEO-debug.apk` 相同：`cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732`
- apkanalyzer `manifest debuggable`：**false**；aapt 无 `application-debuggable`；manifest 无 `android:debuggable`
- 未生成 keystore，未重新生成证书；未覆盖 v1.1.0 / v1.1.1 / v1.1.2
