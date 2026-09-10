# GEO 皮肤预览版

## 范围

- 原界面保留为「经典」，旧用户和未设置用户默认使用经典。
- 「石墨银」采用冷银灰背景、深石墨余额面板、独立收支卡片和胶囊导航。首页、账单、设置、记账金额区、使用人/分类区和详情均有对应样式。
- 设置 → 主题皮肤，即时切换并保存为本机偏好；不修改 Room、不迁移账本、不复制附件、不改变导入/导出数据格式。
- 根导航和 ViewModel 不以皮肤为 key，不调用 Activity.recreate；无新增页面转场、模糊滤镜或持续动画。
- 图标文件保持原样；新界面全部用原生 Compose 控件绘制，无网络字体和远程图片依赖。

## 本地预览

版本为 1.3.0-preview / versionCode 7。本次仅本地候选包，不修改 version.json，不创建 Release、不提交或推送远程。

后续正式版请使用更高的 versionCode（至少 8），保持原签名；已补齐本机预览版本号的更新比较。同版本核心的正式版高于本机 preview，低于本机核心版本的旧 Release 不会被推荐，远程预发布版本仍不接受。

docs/skin-preview 中 PNG 来自 Robolectric / Android native graphics 下真实 Compose 视图绘制，不是设计稿或真机截图。使用独立测试数据库的合成账单，不使用用户截图中的姓名和收支。

## 验收边界

主机 UI 检查不能代替真机流畅度、OEM 手写字体、系统文件选择器和第三方附件查看器验收。用户此前已选择跳过真机调试；本轮不访问手机、不清理手机数据。

皮肤选择使用 SharedPreferences，异步加载后才显示首屏；经典布局本身未重排，设置页增加了必要的皮肤选择区域。长金额允许换行，不使用省略号隐藏余额。

## 测试覆盖

- 默认经典、未知设置回退、选择后重新创建偏好存储仍可读取。
- 首页 → 设置 → 选择石墨银，留在设置页；两套首页读取同一余额。
- 编辑器填写金额后切换两套皮肤，草稿不丢失；切换前后账单内容相等。
- 新皮肤下编辑保存返回详情、删除确认后返回首页且余额刷新。
- 393 × 852 dp 主机渲染；320 × 740 dp / 1.5 倍字体渲染与切换。
- 预览版 → 正式版版本比较；仍拒绝远程预发布版本。

测试中的编辑/删除仅操作合成的主机数据库记录。各测试结束清理自身创建的记录，避免影响旧 UI 烟测的空历史断言。

## 最终本地校验（2026-09-09）

- testDebugUnitTest：131 tests / 30 suites，0 failures / 0 errors / 0 skipped。
- lintDebug：0 errors / 13 warnings；警告为依赖升级、既有实现建议和 SharedPreferences 的 KTX 风格建议，未关闭检查。
- assembleDebug、assembleRelease、lintVitalRelease：通过。完整日志 docs/skin_validation_complete.log，BUILD SUCCESSFUL，4m31s。
- 安装包：dist/GEO-v1.3.0-preview.apk，13,164,318 bytes；com.geo.ledger / versionCode 7 / versionName 1.3.0-preview。
- SHA256：B0085D795F50ABDA74AC1FA1A30707CF15CEAF708B174DE3881D2C38C9F5EEA8。
- Release 构建，non-debuggable；zipalign -P16 检查、APK v2/v3 签名验证通过。沿用旧版证书 cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732，已与本地 1.2.0 正式包核对，无新密钥。
- GEO-icon-original.png SHA256 仍为 BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C。
- 未运行真机/模拟器安装或帧率测试；本轮未运行 Release 变体单元测试。未提交/推送/发布，version.json 保持不变。

安装前建议从旧版导出账本备份。直接覆盖安装此包，不要卸载旧版。首次打开仍为经典；在「设置 → 主题皮肤 → 石墨银」体验新界面。
