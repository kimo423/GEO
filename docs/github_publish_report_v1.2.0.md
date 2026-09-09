# GEO 1.2.0 发布记录

2026-09-09 用户明确授权跳过真机调试并直接发布。该决定不代表真机验收通过。
发布完成：正式Release和APK已公开，main及旧版更新清单已同步。
本报告后续仅文档提交不会移动v1.2.0标签。

## 最终远程证据

- 发布源码提交 / v1.2.0指向：e867ed43313f32bda80040e8a5e046d0a4aec74a。
- Release ID：385294407；Asset ID：552220542；自有资产仅 GEO.apk。
- Release：https://github.com/kimo423/GEO/releases/tag/v1.2.0，draft=false，prerelease=false，latest=v1.2.0。
- 固定下载：https://github.com/kimo423/GEO/releases/download/v1.2.0/GEO.apk。
- latest下载：https://github.com/kimo423/GEO/releases/latest/download/GEO.apk。
- 固定URL和latest URL均已匿名下载到本项目dist，SHA256与本地Release包一致：6F486DFC2D9D8DDFEC269B375B4F8F4D1EC2897FDC9379AF3A2801CD40810F32。
- GitHub资产digest也与上述哈希一致；公开包13,131,550字节。
- main已从e73ef41推进至发布提交；公开raw version.json已返回versionCode=6、versionName=1.2.0、就绪的latest/GEO.apk地址。
- 没有覆盖旧Release/旧tag/旧远程APK；无真机安装、无手机数据清理。
- 本次只上传正式APK。此前本地GEO-debug.apk和review ZIP是审查产物，不是Release资产。

## 构建与身份

- com.geo.ledger，GEO 1.2.0 / versionCode 6；Room4，旧 schema 显式迁移。
- Debug: 127 tests / 29 suites 全通过；两位 Grok 最终/增量审查通过，零残留发现。
- Release: 126 tests / 28 suites，全通过；Compose Activity 烟测仅在 Debug 变体运行。
- lintRelease: 0 errors / 15 warnings；assembleRelease PASS，构建进程 exit0，1m49s。
- 日志：docs/publish_1.2.0_validation.log；未运行 connected、手机安装或手机数据操作。
- 发布 APK：dist/GEO-v1.2.0-release.apk，作为 Release 资产 GEO.apk 上传。
- APK 大小：13,131,550 bytes；SHA256：6F486DFC2D9D8DDFEC269B375B4F8F4D1EC2897FDC9379AF3A2801CD40810F32。
- zipalign -P16 / v2+v3 签名验证通过，non-debuggable，min26 / target36。
- 签名 SHA256：cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732，与旧版一致；使用原密钥，无密钥入库。
- 所有109项被审查源码/测试/schema文件哈希与最终审查一致；发布仅修改清单/文档，不改变应用逻辑。

## 发布顺序

1. 提交升级和版本清单，推送新 tag v1.2.0，不提前推 main。
2. 创建 draft，上传 GEO.apk，核对资产大小和SHA256，再发布为 latest。
3. 匿名下载固定URL和latest URL，比对本地哈希；确认后推送main，使旧版version.json检查指向就绪的新包。
4. 公开核对raw version.json / latest API，记录最终commit、release和asset IDs。

旧版更新入口仍读 version.json；1.2.0 自身更新改为设置页手动检查 Releases API。
旧版本/旧tag/旧资产不改动。真机SAF、第三方查看器、进程死亡和页面帧性能仍未验证。
