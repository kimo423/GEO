# GEO 1.2 升级交付核对 — 2026-09-09

发布决定更新：用户随后明确要求“无需真机调试，测试通过直接上传”。据此执行GitHub
发布，真机步骤由用户跳过，不改写为PASS。以下59项为此前本地交付快照（其中“未推送”
描述的是当时状态）；最终发布结果以 github_publish_report_v1.2.0.md 为准。

本地实现与127项宿主测试通过；Round3及最后修复复核均通过，真机验收尚未完成。
未发布、未推送。以下按需求119节的59项报告；不将实现/宿主通过冒充手机验证。

1. 项目绝对路径：C:\Users\SUN\Desktop\GEO，沿用原仓库。
2. 原版本：1.1.3 / versionCode 5。
3. 新版本：1.2.0 / versionCode 6。
4. Application ID：com.geo.ledger；应用名 GEO，原图标不变。
5. 原 Database Version：3。
6. 新 Database Version：4。
7. Migration：显式 1→2→3→4；3→4 增加 UUID/tombstone、逐行补 UUID 后建立唯一索引，新增附件/审计表；无 destructive fallback。
8. Migration Test：宿主实际 Room 从 schema 1、2、3 分别迁移到 4，旧 ID/金额/日期/快照/余额顺序/UUID 均通过。手机迁移未运行。
9. Transaction UUID：永久随机 UUID，旧记录迁移补齐；编辑保留，导入只凭 UUID 匹配，不凭金额/名字/目录。
10. 删除语义：同事务写 DELETE before、停用附件关系、写 tombstone；普通查询/余额排除已删除行，重复删除 no-op。
11. 新增 Room 表：attachment_blobs、transaction_attachments、audit_events。
12. Attachment 模型：attachmentUuid、blobUuid、transactionUuid、原文件名、排序、当前/移除状态；blob 保存 SHA256、字节数、MIME、内部 key。
13. 私有存储：filesDir/blobs/<random internalStorageKey>；blobUuid 不作实际路径；不依赖外部原文件，文件名/URI 不做磁盘路径。
14. 单附件限额：10,485,760 bytes，流式复制时逐块累计，超过即拒绝。
15. 总附件限额：每个当前集合/历史快照最多 31,457,280 bytes；添加时剩余配额作为实际流式上限。
16. 数量：0～10；多选达到 11 整批拒绝，保存/导入重新验证。
17. 流式大小：64 KiB 缓冲，不信任 provider SIZE；读取失败/持续空读/超限清理本次临时文件。
18. SHA256：Java MessageDigest，abc 已知向量测试；导出/导入/查看/保存前验证真实内容。
19. Preview/Open：PNG/JPEG/WebP 内置有界采样预览；PDF/其他通过只读 FileProvider 交给已安装查看器，无程序提示；手机兼容性未验。
20. 保存到设备：SAF CreateDocument，复制私有原始内容到用户所选位置；历史附件同样支持；真实 provider 待验。
21. Audit Event：eventUuid、transactionUuid、EDIT/DELETE、USER/IMPORT、毫秒时间、schemaVersion、before/after JSON。
22. Edit Before/After：与原业务快照比较，变化才追加事件；同事务保存行/附件/快照。恢复的新建 key 再次保存可作为编辑，完全相同 no-op。
23. Delete Audit：保存删除前快照，历史事件/内容不随逻辑删除被物理删除。
24. 历史移除附件：旧快照保留稳定引用，blob 保留；从历史仍可查看/保存。
25. Audit UI：每事件一个外层卡片，内部修改前→修改后区块、字段变化标签、附件增删留摘要；不是两条独立账单。
26. geocfg：严格 UTF-8 JSON，GEO_CONFIG / formatVersion 1，导出时间/app版本/人员/分类。
27. Config Full：导入配置成为当前有效配置，旧有效项停用、同名复用、其余新增；不改历史账单名称快照。
28. Config Same-name：同类型 NFC+trim+ASCII-case-fold 匹配；只替换同名、忽略 unmatched 不新增；旧大小写歧义安全拒绝，需用户重命名。
29. geodata：ZIP 根 manifest.json、transactions.json、audit_logs.json，真实附件在 attachments/。
30. Transaction Folder：日期_收支_金额_可选说明_UUID8；碰撞扩展完整 UUID；无附件记录不建目录。
31. Folder Sanitization：非法符号/控制字符替换，限长不截断代理对，处理保留名和文件重名；目录不参与身份判断。
32. current/history_only：当前引用放 current，仅历史引用放 history_only；同 blob 只写一次，manifest 关联各引用。
33. Manifest：格式/版本/时间/app版本、数量、交易 UUID 索引、当前快照与根 JSON 哈希、附件身份/归属/路径/大小/MIME/SHA256。
34. Data Full：完整替换账单、附件元数据和审计为导入包状态，不改配置；不人为生成旧账 DELETE 海或额外 IMPORT 事件。
35. Data Same-record：只处理 UUID 交集；本机 A 留、匹配 B 换、新 C 忽略；变化的 B 有 IMPORT EDIT，无变化不加；eventUuid 同身份不同规范内容整包拒绝。
36. Import Atomicity：私有 staging 全部验证→新 key 文件晋升→单 Room 事务；失败仅删本次且确认数据库未引用文件；提交后取消不误删活跃内容。
37. ZIP Security：拒绝路径逃逸/绝对/驱动/反斜杠/重复大小写/未声明/缺失/坏哈希/异常压缩率；输入、展开、JSON、条目、记录数上限明确。
38. Round Trip：宿主全量和同 UUID、配置两模式、删除/审计/10同名附件/真实字节、冲突回滚测试通过；不代表手机 SAF 流程通过。
39. Update Source：https://api.github.com/repos/kimo423/GEO/releases/latest，仅正式 Release。
40. Update Checker：只在设置页点击请求，8s 超时和中文失败反馈；新版本提示，优先 GEO.apk，无 APK 回落 Release 页面；不启动检查、不自动安装。
41. SemVer Tests：数值 BigInteger 比较、v前缀/build metadata、拒预发布/坏版本；官方公开响应捕获样本解析通过，测试本身不联网。
42. Permissions：源清单仅 INTERNET。合并 APK 另含 AndroidX 自动生成的 signature 级 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION；无危险存储/安装权限。
43. UI：暖白/石墨/银灰/蓝灰，统一页面间距、圆角和字体；纸夹数量、附件/历史/设置分组；24小时秒级时间保留。未声称手机帧耗时已改善。
44. Unit Test：最终全量 127/127，29 suites，0 failures/errors/skipped，Gradle 正常退出 0。
45. Attachment Tests：0/1/10/11、10MiB/30MiB边界、外部删除后私有副本、坏文件/失败清理/哈希/同名文件等宿主验证通过；系统选择/查看/保存待验。
46. Audit Tests：连续编辑、no-op、A,B→A,C、重复删除、tombstone 回放、恢复草稿和不可变事件冲突通过。
47. Import/Export Tests：全量/交集、UUID、坏包原库不变、新文件回滚、压缩/路径/哈希/目录白名单、严格UTF8、预览替换清理通过。
48. lint：0 errors / 12 warnings；依赖建议7、屏宽1、Kapt1、旧SDK1、空间检查1、KTX建议1，未为绿灯隐藏警告。
49. assembleDebug：BUILD SUCCESSFUL in 34s，exit 0；AndroidTest Kotlin 编译通过，connected 未运行。
50. APK：C:\Users\SUN\Desktop\GEO\dist\GEO-debug.apk，20,178,362 bytes；SHA256 712C7192258F1C6D1DCF110E4FE68C7829D6B649847CF1FDBF5E7A2C811F0CD9；与旧包同签名。
51. Grok A Final Overall：Round3及post-fix IMPLEMENTATION PASS，C0/Major0/Minor0，实际退出0；全文加两处增量复核。最新指纹由主Codex和B重算，A补记不声称独立重算全部哈希。
52. Grok B Final Counts：post-fix IMPLEMENTATION PASS，C0/H0/M0/L0；已独立核验109文件和APK/127项XML，关闭最后staging清理问题。原超轮次失败不算通过。
53. Grok 真实问题：Round2去重11项加Round3清理问题1项，共12项；未复现ZIP脚本问题不计漏洞，架构建议另记。
54. 主 Codex 修复：12项均有代码/文档修改并经独立复核；见 upgrade_round2_resolution 与两份最终审查补记。
55. Known Issues：无设备、真机门禁待验；保守 orphan 留存、旧选项歧义、资源上限、debug签名；见 known_issues.md。
56. README：C:\Users\SUN\Desktop\GEO\README.md。
57. data_format：C:\Users\SUN\Desktop\GEO\docs\data_format.md。
58. Git：沿用 main/原 origin，当前含本次修改和新增文件，未 commit、未 push；最终清单另存。
59. 目录确认：项目、代码、测试、文档、Grok报告、日志、截图、最终APK全部在 Desktop/GEO；用户附件只读，未动手机实际账本。

## 真机剩余门禁

手机开启USB调试并允许电脑后，先只读核对版本/设备/签名，保护真实账本；
不得卸载/清数据或在正式账本上运行破坏性connected测试。先备份，使用隔离测试数据
验收迁移、附件SAF/第三方查看器、进程恢复、更新浏览器跳转和页面帧耗时。
目前不能声称全部完成Gate。旧dist/GEO.apk和旧ZIP是历史版本；本次请只用GEO-debug.apk。
