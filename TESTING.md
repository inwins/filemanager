# 测试策略与覆盖

## 目标与边界

测试按“纯逻辑单元测试 → 黑盒协议测试 → 服务/发现集成测试 → 真机冒烟”分层组织。

整个 Android 工程无法用 JVM 单元测试达到 100% 行覆盖：Compose UI、ExoPlayer、MediaScanner、SMB/SFTP、系统通知和真实网络 IO 需要仪器化或真机测试。因此本仓库设置了两个可执行的覆盖率门槛：

- `jacocoPureCoverage`：`LanTransferPaths`、`LanPeer`、`LanEntry` 行覆盖 **100%**。
- `jacocoLanCoverage`：`io.github.inwins.filebrowser.lan` 行覆盖 **≥95%**、分支覆盖 **≥80%**。

真机冒烟测试覆盖浏览器页面、App 间发现/浏览/上传/下载、远程音频流式播放和后台前台服务。

## 测试分层

| 层次 | 测试类 | 说明 |
| --- | --- | --- |
| 纯逻辑单元 | `LanTransferPathsTest`、`LanModelsTest`、`PathsTest`、`StreamRangeParserTest` | 路径解析、路径穿越防护、文件名、模型值语义、Range 解析 |
| 协议黑盒 | `LanTransferServerTest` | 通过真实 TCP 连接测试 HTTP 契约，不直接调用私有实现 |
| 客户端契约 | `LanClientTest`、`LanTransferServerTest` | URL 编码、列表容错、上传/下载、进度回调、错误处理 |
| 生命周期 | `LanTransferServiceTest`、`LanTransferServerLifecycleTest` | 前台服务启动/停止、无 root、STOP action、端口占用、根目录校验、URL 刷新 |
| 设备发现 | `LanDiscoveryTest` | 广播报文解析、非法报文、端口占用、UDP 实际接收 |
| Android 组件 | `AudioPlayerTest` | ExoPlayer/缓存初始化、失败回退、停止清理 |
| 真机冒烟 | 手工/ADB | Web 页面、App 互传、流式音频、通知与后台运行 |

## 黑盒用例矩阵

| 场景 | 覆盖 |
| --- | --- |
| 开启/停止互传 | ✅ 服务生命周期 + 服务器启停 |
| 浏览器首页与令牌校验 | ✅ HTML、错误令牌 404 |
| 目录列表/嵌套目录/空目录 | ✅ 列表、排序、目录优先 |
| 上传空文件/中文名/空格/反斜杠 | ✅ |
| 同名并发上传 | ✅ 8 并发上传不覆盖 |
| 下载完整文件/Range/HEAD | ✅ 200/206/HEAD |
| 非法/空文件 Range | ✅ 416 |
| 路径穿越/符号链接边界 | ✅ 列表、下载、上传、创建目录 |
| 缺少/负数 Content-Length | ✅ 411 |
| 上传中断 | ✅ 500 且清理半成品 |
| 创建目录/冲突/非法名称 | ✅ 200/409/400 |
| 100-continue | ✅ |
| 长请求头/无冒号请求头/空请求行 | ✅ |
| MIME 回退 | ✅ application/octet-stream |
| 设备发现报文 | ✅ 有效/无效/端口占用/UDP 接收 |
| 客户端 URL 编码 | ✅ 空格/加号/中文 |
| 客户端列表容错 | ✅ 缺失 entries、null 条目、空名称 |
| 客户端下载失败清理 | ✅ 无 `.part` 残留 |
| 远程音频缓存初始化 | ✅ 成功/失败回退 |

## 运行

```bash
# 全部单元测试
/opt/gradle-8.7/bin/gradle testDebugUnitTest

# 生成 HTML/XML 覆盖率报告
/opt/gradle-8.7/bin/gradle jacocoTestReport

# 覆盖率门槛
/opt/gradle-8.7/bin/gradle jacocoPureCoverage jacocoLanCoverage
```

报告位置：

- HTML：`app/build/reports/jacoco/html/index.html`
- XML：`app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`

## 当前结果

- 单元测试：**93 项，全部通过**
- LAN 模块：**行覆盖 96.4%，分支覆盖 82.9%**
- 纯路径/模型层：**行覆盖 100%**
- 整体 JVM 行覆盖：42.2%（未包含 Compose UI、ExoPlayer、SMB/SFTP 和系统集成代码）

如需追求整个工程 100% 行覆盖，需要补充 Compose UI 测试、仪器化测试以及可替换的系统 IO/网络测试桩；这超出当前 JVM 单元测试的范围。
