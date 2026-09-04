# GEO 1.1.1 发布记录

- 仓库：https://github.com/kimo423/GEO
- 版本提交（tag `v1.1.1` 指向此提交，不得移动）：`15c07a260f29dcf8eb8c0e1636df167594c960d4`
- 提交说明：`chore: release GEO 1.1.1 update test`
- 本报告为后续仅文档提交；`main` 可能比 tag 多 1 个文档提交。

## 两阶段激活

1. 先 push tag `v1.1.1`（携带版本提交对象），此时远端 `main` / raw `version.json` 仍为 versionCode **2**。
2. 创建 latest Release `v1.1.1` 并上传 `GEO.apk`；固定 URL 与 latest URL 下载核验通过后，才 push `main`。
3. 推送后公开 raw `version.json` 生效为 versionCode **3**。

## Release / URL

- Tag / Title：`v1.1.1` / GEO 1.1.1（latest，非 draft，非 prerelease）
- Release：https://github.com/kimo423/GEO/releases/tag/v1.1.1
- Asset：https://github.com/kimo423/GEO/releases/download/v1.1.1/GEO.apk
- Latest 下载：https://github.com/kimo423/GEO/releases/latest/download/GEO.apk
- raw：https://raw.githubusercontent.com/kimo423/GEO/main/version.json
- 未删除/覆盖 `v1.1.0`：https://github.com/kimo423/GEO/releases/tag/v1.1.0

## APK

- 大小：19,739,876 字节
- SHA256：`B667583EA2B758A2AF3B43A6F065C165932CA293DF0BDCFB27D76B4E6A8B7079`
- aapt：`com.geo.ledger` versionCode **3** / versionName **1.1.1**
- 签名证书 SHA-256：`cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732`（与 v1.1.0 / 旧 `dist\GEO.apk` 相同，CN=Android Debug）
- 未生成 keystore，未重新签名

## 测试与边界

- JVM：15 suites / 81 tests 全部通过
- lintDebug：0 error / 10 warnings
- assembleDebug：成功
- `connectedDebugAndroidTest` 因本机无硬件加速模拟器未执行，不视为通过
- 当前 APK 为 Android Debug 证书；未来更新必须沿用同一签名
- 公开 raw：`versionCode=3`、`versionName=1.1.1`，releaseNotes 与 apkUrl 正确
