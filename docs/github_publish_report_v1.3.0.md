# GEO 1.3.0 发布记录

2026-09-10 完成 GitHub Release 发布。用户授权发布双皮肤与使用人多选升级；没有进行真机安装或数据操作。

## 已验证产物

- 发布源码 / v1.3.0：8c23fd4cc473ad06321e62443a48453e8a480f0a。标签保持固定，后续文档提交不移动标签。
- Release ID：385971178；Asset ID：554081696；正式发布、非草稿、非预发布，latest 为 v1.3.0。
- 发布页：https://github.com/kimo423/GEO/releases/tag/v1.3.0。
- 固定 APK：https://github.com/kimo423/GEO/releases/download/v1.3.0/GEO.apk。
- latest APK：https://github.com/kimo423/GEO/releases/latest/download/GEO.apk。
- 本地发布包：dist/GEO-v1.3.0-release.apk，13,164,318 bytes，non-debuggable。
- SHA256：CECD1DAA8ECAA50C20145ECCD9A274E8C6068F5A4459B421F99513B858CAC613。
- GitHub 资产 digest、匿名下载的固定链接文件和 latest 文件，均与上述 SHA256 一致。
- com.geo.ledger / versionName 1.3.0 / versionCode 9 / Room 5。
- 沿用旧签名，证书 SHA256：cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732；APK v2/v3 签名验证通过。无密钥入库，无新密钥。

## 验证

- Debug：135 tests / 31 suites；Release：130 tests / 29 suites；均为 0 failures / errors / skipped。
- Release 测试排除仅能在 Debug ui-test-manifest 下运行的两个 Compose 主机测试类；它们已在 Debug 验证。仓库、迁移、备份、业务测试仍在 Release 运行。
- lintRelease：0 errors / 16 warnings；assembleRelease 通过。
- 完整构建日志：docs/publish_1.3.0_validation.log，BUILD SUCCESSFUL，1m37s。
- 未做真机流畅度、系统文件选择器或第三方查看器验收；不将主机渲染当作真机结果。

## 发布顺序与兼容

先推送 v1.3.0 标签，创建草稿，上传 GEO.apk 并校验资产 digest，再发布为 latest；随后匿名下载两个链接校验，最后推送 main 中的源码及 version.json。

version.json 使用固定 v1.3.0/GEO.apk 地址与 versionCode 9。1.2.0 及之后的版本在设置页手动检查 Releases API；更旧版本的启动检测读取 version.json。预览版可以检测到同核心版本的正式版。

经典皮肤仍为默认，新皮肤从设置页选择。使用人支持多选/全选/清空，历史快照与附件完整保留。新导出的 geodata 格式 2 不支持旧版 GEO 读取；新版兼容旧格式 1。

旧 Release、旧标签和旧远程 APK 均未覆盖。建议备份后直接覆盖安装，不要卸载旧版。
