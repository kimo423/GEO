# GEO GitHub 发布记录

- 仓库：https://github.com/kimo423/GEO
- 分支：`main`
- 发布提交（tag `v1.1.0` 指向此提交，不要改 tag）：`614f3289147af5e038b3401a317c3c1888090396`
- 提交说明：`feat: build GEO ledger with GitHub updates`
- `main` 在本报告提交后可能比 tag 多 1 个文档提交；Release / tag 仍对应上述首个发布提交。

## Release

- Tag：`v1.1.0`（target `main`，latest，非 draft，非 prerelease）
- Title：GEO 1.1.0
- Release：https://github.com/kimo423/GEO/releases/tag/v1.1.0
- Latest：https://github.com/kimo423/GEO/releases/latest
- Asset 名：`GEO.apk`
- Content-Type：`application/vnd.android.package-archive`
- Asset：https://github.com/kimo423/GEO/releases/download/v1.1.0/GEO.apk
- Latest 下载：https://github.com/kimo423/GEO/releases/latest/download/GEO.apk
- raw `version.json`：https://raw.githubusercontent.com/kimo423/GEO/main/version.json

## 哈希与公开核验

- 期望 / 本地 / 公开下载 SHA256：`3004B42684B2E568BE534BA035F1288840054CF381088BF3FB0625CA97571D72`（一致）
- 公开 `latest/download/GEO.apk` 已下载到项目内 `.tmp-geo-apk-verify/`，比对后仅删除该临时目录
- raw `version.json`：`versionCode=2`、`versionName=1.1.0`，与本地相同
- 因此本版本启动更新检测不会提示自己；未来发布必须提高 `versionCode`

## 签名与测试边界

- 当前 APK 使用 Android Debug 证书签名。未来更新必须沿用同一签名，否则无法覆盖安装。未生成新 keystore，未重新签名。
- JVM：15 suites / 81 tests 全部通过。lint：0 error / 10 warnings。
- `connectedDebugAndroidTest` 因本机无硬件加速模拟器未执行，不视为通过。
