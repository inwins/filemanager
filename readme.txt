文件浏览器 —— 构建说明
====================================

项目：com.example.filebrowser
路径：仓库根目录
技术栈：Kotlin + Jetpack Compose (Material 3)
compileSdk 34 / minSdk 26 / targetSdk 34
当前版本：1.7.0（versionCode 12）

界面与交互参考主流文件管理器，使用原创代码实现。依赖统一走
阿里云 Maven 镜像；如需拉取 GitHub 参考项目，建议使用 gitclone、ghproxy
等镜像，避免直连失败。


一、已实现功能
--------------
1. 存储首页
   - 内部存储 / SD 卡卡片，显示已用、剩余和容量进度。
   - 快速访问：下载、相机、图片、文档、音乐、视频、Android。
   - 分类入口、书签、最近打开记录。

2. 文件浏览
   - 列表 / 网格两种视图，目录优先。
   - 按名称（自然排序）、大小、修改时间、类型排序，升序 / 降序。
   - 面包屑导航、显示隐藏文件、刷新、路径记忆。
   - 新建文件夹 / 文件、重命名、删除、复制、剪切、粘贴。
   - 同名冲突自动生成 “名称 (1).扩展名”，不会覆盖原文件。
   - 多选、全选、反选、批量分享。
   - ZIP 压缩 / 解压（含 Zip Slip 路径穿越防护、GBK 文件名回退）。
   - 文件属性：类型、大小（文件夹递归计算）、修改时间、权限、路径。
   - 图片内置查看器：左右翻页、双指缩放、双击缩放、分享。
   - TXT 阅读器：章节自动识别、目录跳转、阅读进度记忆。
   - 阅读书签：按书籍保存、添加、跳转、删除。
   - TTS 语音朗读：当前页开始、暂停/继续、停止、自动下一章、语速调节。
   - 阅读设置：字号、行距、纸张/羊皮纸/护眼/深色主题。
   - 内置音频播放器：专辑封面、元数据、播放列表、进度、上一首/下一首、底部迷你播放条。
   - 本地、Root、SMB、SFTP 音频可直接在应用内播放（远程音频先缓存）。
   - 歌词显示：同名 .lrc 文件、时间轴同步、当前行高亮、自动滚动、偏移/元数据支持。
   - 本地、Root、SMB、SFTP 音频会自动查找同名 .lrc；远程歌词随音频缓存。
   - 远程目录支持多选、复制/剪切/删除；可粘贴到远程目录或本地目录，也支持本地复制后粘贴到远程。
   - 视频/音频可通过本机 HTTP 流媒体接口转发给 VLC 等外部播放器，支持 Range 拖动。
   - 压缩包预览/解压：ZIP、7Z、RAR、TAR、TAR.GZ、GZ；支持 AES 加密 ZIP 和分卷 ZIP 创建。
   - 文件哈希：MD5 / SHA-1 / SHA-256。
   - 存储清理：重复文件、大文件、缓存清理。
   - 标签、收藏、置顶和颜色标记；收藏/置顶显示在首页。
   - 本地与远程图片/视频缩略图（远程通过本机流媒体接口加载，不额外下载整个目录）。
   - 远程路径也支持标签/收藏（按服务器+路径保存）。
   - 回收站：删除默认移入回收站，可恢复或永久删除/清空。
   - 内置视频播放器：本地及缓存后的远程视频可直接播放。
   - 文本内置编辑器：UTF-8 / GBK 读取、保存、未保存提醒。
   - 打开方式、分享、APK 安装（FileProvider）。
   - Root 访问：浏览 /data 等受限路径，支持 Root 文件操作与本地互传。
   - SMB2/SMB3 与 SFTP 网络存储：添加服务器、浏览、上传、下载、重命名、删除。
   - 网络密码使用 Android Keystore 加密保存。

3. 分类
   - 图片、视频、音频、文档、下载、安装包、压缩包、最近。
   - 基于 MediaStore 查询，文件操作后自动触发媒体扫描。
   - 图片 / 视频网格预览，图片直接进入内置查看器。
   - 压缩包点击后可选择解压、打开方式或查看属性。

4. 应用管理
   - 已安装应用列表、搜索、用户应用 / 系统应用切换。
   - 打开、应用信息、卸载、分享安装包。

5. 搜索
   - 当前目录递归搜索，300ms 防抖。
   - 显示搜索范围和已扫描文件数，可取消，最多返回 500 条。
   - 支持隐藏文件开关。

6. 存储分析
   - 递归统计当前目录下各项占用，按大小降序展示比例条。
   - 可下钻到子目录，可取消，扫描过程实时显示已扫描文件数。

7. 设置
   - 跟随系统 / 浅色 / 深色主题，动态取色。
   - 默认列表 / 网格、显示隐藏文件。
   - 所有文件访问权限状态与跳转。
   - 存储分析、浏览根目录、清除最近记录、版本信息。

8. Root 访问
   - 设置 → Root 访问：开启并授予超级用户权限。
   - 可浏览 /data、/system 等受限目录，支持新建、重命名、删除。
   - 支持 Root 与普通存储之间复制/粘贴。
   - Root 文件打开时会复制到缓存预览，编辑不会写回原文件。

9. 网络存储
   - 网络标签页支持 SMB2/SMB3 和 SFTP（密码登录）。
   - 添加/编辑/删除服务器；浏览共享目录、上传、下载、重命名、删除、属性。
   - 服务器密码使用 Android Keystore 加密保存。
   - SFTP 暂未验证主机指纹；SMB 仅支持 SMB2/SMB3，不支持 SMB1。

10. 更新日志
   - 设置 → 更新日志，内置中英文版本记录。
   - res/raw/changelog.txt（英文）、res/raw-zh/changelog.txt（中文）。

11. 权限
   - Android 11+ 使用 “所有文件访问权限”（MANAGE_EXTERNAL_STORAGE）。
   - Android 10 及以下使用 READ/WRITE_EXTERNAL_STORAGE 运行时权限。


二、环境要求
------------
1. JDK 17
   验证：java -version

2. Gradle 8.7
   本机路径：/opt/gradle-8.7/bin/gradle
   验证：/opt/gradle-8.7/bin/gradle --version

3. Android SDK
   本机路径：$ANDROID_HOME
   需要：platforms;android-34、build-tools、platform-tools(adb)
   local.properties 中已配置：
       sdk.dir=$ANDROID_HOME
   若换机器，请重新创建 local.properties 并写入自己的 SDK 路径。

4. 设备调试
   adb 位于 ~/sdk/platform-tools/adb
   测试机序列号：（已移除）（Redmi Note 8 Pro / Android 11 / 已 root）


三、构建 Debug APK
------------------
在项目根目录执行：

    /opt/gradle-8.7/bin/gradle assembleDebug --console=plain

或：

    ./gradlew assembleDebug --console=plain

产物：

    app/build/outputs/apk/debug/app-debug.apk   （约 19 MB）

提示：
- 本机网络受限时优先使用 /opt/gradle-8.7/bin/gradle；
- 依赖镜像已在 settings.gradle.kts 中配置为阿里云；
- 首次构建需要下载依赖，之后可加 --offline。


四、运行单元测试
----------------
    /opt/gradle-8.7/bin/gradle testDebugUnitTest --console=plain

或使用脚本：

    ./scripts/test.sh

测试覆盖：
- 文件操作：复制、移动、删除、重命名、创建、ZIP / 解压、Zip Slip 防护。
- 递归搜索、隐藏文件过滤、结果上限、文件夹大小统计。
- 名称自然排序、大小 / 时间排序。
- 路径处理、名称校验、大小格式化。
- TXT 章节识别、目录/书签偏移映射、TTS 分句。
- SharedPreferences 设置 / 书签 / 最近记录 / 阅读进度（Robolectric）。
- Root ls 输出解析、隐藏文件过滤。
- LRC 歌词解析、时间轴定位。
- HTTP Range 请求解析、文件哈希、ZIP 创建/加密/解压。
- 回收站元数据读写。
- 共 47 项测试。

报告：app/build/reports/tests/testDebugUnitTest/index.html


五、构建 Release APK
--------------------
    /opt/gradle-8.7/bin/gradle assembleRelease --console=plain

产物：

    app/build/outputs/apk/release/app-release-unsigned.apk

说明：
- release 开启 R8（minify + shrinkResources）；
- 未配置签名时输出为 unsigned，不能直接覆盖安装 debug 版；
- R8 构建较慢，内存不足可在 gradle.properties 调大：
      org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g

配置正式签名（可选）：
在项目根目录创建 keystore.properties（不要提交到 git）：

    storeFile=release.jks
    storePassword=你的密码
    keyAlias=你的别名
    keyPassword=你的密码

再次执行 assembleRelease，会输出已签名的 app-release.apk。
注意：签名必须与已安装版本一致，否则 Android 会拒绝覆盖安装。


六、安装到设备
--------------
1. 确认设备：

    ~/sdk/platform-tools/adb devices

2. 直接安装（data 保留）：

    ~/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

3. 本机 MIUI 推荐方式（adb install 偶发失败时）：

    ADB=~/sdk/platform-tools/adb
    $ADB push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/filebrowser.apk
    $ADB shell "su -c 'pm install -r /data/local/tmp/filebrowser.apk'"

4. 授权（Android 11）：

    $ADB shell "su -c 'appops set com.example.filebrowser MANAGE_EXTERNAL_STORAGE allow'"

   也可以打开应用后点击“去授权”，在系统设置中开启“所有文件访问权限”。

5. 启动：

    $ADB shell am start -n com.example.filebrowser/.MainActivity

重要：
- 使用 -r 覆盖安装可保留设置、书签和最近记录；
- 不要 uninstall / 清除数据，否则设置和书签会丢失；
- debug 版与 release 版签名不同，不能互相覆盖。


七、发布流程（版本号 + 提交 + tag）
----------------------------------
1. 修改版本号：app/build.gradle.kts
       versionCode = 2          // 每次 +1
       versionName = "1.0.1"

2. 执行发布脚本（assembleDebug、自动 commit、打 tag）：

       ./scripts/release.sh 1.0.1

3. 安装验证：

       ~/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk


八、常用命令速查
----------------
清理：

    /opt/gradle-8.7/bin/gradle clean

编译 + 单测：

    /opt/gradle-8.7/bin/gradle assembleDebug testDebugUnitTest --console=plain

查看日志：

    ~/sdk/platform-tools/adb logcat | grep -i filebrowser

强制停止：

    ~/sdk/platform-tools/adb shell am force-stop com.example.filebrowser


九、目录结构
------------
app/src/main/java/com/example/filebrowser/
  AppState.kt        全局状态、导航栈、剪贴板、文件操作调度
  MainActivity.kt    权限申请与 Compose 入口
  FileBrowserApp.kt Application
  data/
    Models.kt        数据模型（FileItem / StorageItem / 设置等）
    ReaderModels.kt  阅读器模型（章节、书签、TTS 事件）
    TtsController.kt Android TTS 封装
    FileOps.kt       纯 Java IO 文件操作（可单元测试）
    FileRepository.kt Android 相关：存储卷、MediaStore、应用、Intent
    RootShell.kt     Root 命令执行与文件操作
    AudioPlayer.kt  ExoPlayer 音频播放、元数据与歌词加载
    RemoteModels.kt  网络存储模型
    RemoteFileSystem.kt SMB / SFTP 实现
    RemoteTransfer.kt 远程/本地复制粘贴
    ArchiveOps.kt    ZIP/7Z/RAR/TAR 预览与解压
    StorageCleaner.kt 重复文件/大文件/缓存扫描
    SecretStore.kt   网络密码加密
    Prefs.kt         SharedPreferences（设置 / 书签 / 最近记录 / 阅读进度）
  ui/
    AppRoot.kt       权限页、Scaffold、底部导航、Snackbar
    HomeScreen.kt    首页
    BrowserScreen.kt 文件浏览
    CategoryScreen.kt 分类、应用列表
    SearchScreen.kt  搜索
    AnalyzerScreen.kt 存储分析
    SettingsScreen.kt 设置
    ViewerScreens.kt 图片查看器、文本编辑器
    ReaderScreen.kt  TXT 阅读器（章节 / 书签 / TTS / 主题）
    ChangelogScreen.kt 更新日志
    NetworkScreen.kt 网络存储列表、配置与远程浏览
    ArchivePreviewScreen.kt 压缩包预览与解压
    CleanerScreen.kt 存储清理
    TrashScreen.kt  回收站
    VideoPlayerScreen.kt 内置视频播放器
    HashDialog.kt    MD5/SHA-1/SHA-256 计算
    Components.kt    文件行 / 网格、图标、对话框、底部弹层
    theme/Theme.kt   Material 3 主题
  stream/            本地 HTTP 流媒体服务、前台服务、令牌注册
  util/              格式化、路径、MIME、TXT 章节、LRC 歌词、Range、Root 列表解析工具
app/src/main/res/    资源与中英文文案、raw 更新日志
app/src/test/        单元测试 / Robolectric
scripts/test.sh      单元测试脚本
scripts/release.sh   发布脚本
.github/workflows/   CI


十、数据位置与注意事项
----------------------
- 设置、书签、最近记录、阅读进度、阅读书签、网络服务器：
  /data/data/com.example.filebrowser/shared_prefs/
- Root 预览缓存：应用 cache/root_preview/
- 网络文件预览缓存：应用 cache/remote/
- 分享 APK 临时文件：应用 cache/shared_apks/
- 应用不创建自己的回收站，删除即永久删除，删除前会二次确认；
- Android/data、Android/obb 在 Android 11+ 上仍可能受系统限制；
- 媒体分类依赖 MediaStore，应用内新建/复制/压缩后会主动触发媒体扫描，
  但个别机型的媒体库可能有延迟，可点击分类页刷新；
- 卸载应用会删除设置、书签和最近记录，但不会删除用户文件。


十一、Git 版本控制
------------------
仓库已在项目根目录初始化，默认分支 main。

常用命令：

    git status
    git add -A
    git commit -m "描述本次修改"
    git log --oneline --graph
    git tag v1.1.0

已通过 .gitignore 排除 build/、.gradle/、local.properties、APK 和签名文件。


十二、后续可扩展
----------------
- 回收站、批量重命名。
- SFTP 密钥登录、主机指纹校验。
- 云盘（WebDAV / 坚果云等）。
- 视频内置播放器、更多压缩格式（7z / rar）
- 流媒体服务目前仅监听 127.0.0.1，供本机其他应用访问；暂不支持局域网/PC 访问。
。
- 深色 AMOLED、平板双栏布局、文件标签。
