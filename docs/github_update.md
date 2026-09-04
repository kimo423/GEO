# GitHub 更新检测

## version.json

仓库根目录（`main` 分支）必须有 `version.json`：

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "releaseNotes": "中文更新说明",
  "apkUrl": "https://github.com/kimo423/GEO/releases/latest/download/GEO.apk"
}
```

| 字段 | 规则 |
|---|---|
| `versionCode` | 正整数。客户端用它与本地 `BuildConfig.VERSION_CODE` 比较；更大才提示。 |
| `versionName` | 非空，仅展示。 |
| `releaseNotes` | 非空、最长 2000 字。语言由发布者自定（可中文或其他文字）；客户端不强制 CJK。 |
| `apkUrl` | 仅 `https://github.com/kimo423/GEO/releases/latest/download/GEO.apk` 或 `.../releases/download/<tag>/GEO.apk`。主机必须是 `github.com`（不要 `www`），默认 443，无 userInfo/query/fragment。 |

客户端只 GET 这两个清单地址（规范化后精确匹配）：

- `https://raw.githubusercontent.com/kimo423/GEO/main/version.json`
- `https://raw.githubusercontent.com/kimo423/GEO/refs/heads/main/version.json`

重定向必须仍落在上述地址。无缓存、短超时、体积上限 8KB。失败（网络/HTTP/解析/取消）不弹错误，不挡账本。

## Release 资源

GitHub Release 的资源文件名必须是 **`GEO.apk`**，以便

`https://github.com/kimo423/GEO/releases/latest/download/GEO.apk`

可下载。也可使用带 tag 的  
`https://github.com/kimo423/GEO/releases/download/<tag>/GEO.apk`。

远程仓库需要同时具备：`main/version.json` **以及** 名为 `GEO.apk` 的 Release 资源。

**当前边界：** `https://github.com/kimo423/GEO` 仍为空、未发布。本地 `version.json` 已是 2 / 1.1.0，APK URL 为上述 `GEO.apk`。客户端冷启动检测、浏览器安装 **尚未在真机/加速模拟器上验证**。GitHub update 实现/安全审查为 **PASS、zero findings**。交付物里的安装包是 **debug 签名**；以后升级必须用同一证书。

## 以后发版

1. 升高 `app/build.gradle.kts` 的 `versionCode` / `versionName`。
2. 同步根目录 `version.json` 的 `versionCode`、`versionName`、`releaseNotes`。
3. 打 tag、创建 GitHub Release，上传 **`GEO.apk`**（不要改资源名）。
4. 推送 `version.json` 到 `main`。用户冷启动后若远程 `versionCode` 更大，会看到「稍后更新 / 立即更新」。

「稍后更新」只在本进程有效；杀进程后再启动会重新检查。本机不缓存远程 JSON。
