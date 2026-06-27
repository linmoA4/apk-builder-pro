# LM打包工具重构说明

这份说明基于最近几轮重构与功能补充，重点记录当前版本已经落地的结构变化、启动流程调整和界面升级点。

## 第一轮重构

1. 拆分 `MainActivity`
- 新增 `build/BuildManager.java`
- 新增 `env/EnvironmentManager.java`
- 新增 `project/ProjectManager.java`
- 新增 `log/LogManager.java`
- 新增 `model/BuildResult.java`
- 新增 `model/EnvironmentState.java`
- 新增 `model/ProjectConfig.java`
- 新增 `service/BuildWorkflowService.java`
- 新增 `service/ProjectWorkspaceService.java`
- 新增 `service/ProjectFileService.java`

2. 把“创建空壳项目”改成真实写文件
- 现在会在 `/storage/emulated/0/LMBuildTools/projects/<应用名>` 下真实创建工程目录
- 会写入 `settings.gradle`、根级 `build.gradle`、`gradle.properties`
- 会写入 `app/build.gradle`、`AndroidManifest.xml`
- 会写入 `MainActivity.java`、`activity_main.xml`、`strings.xml`
- 会复制 `assets/project_template/` 里的 `gradlew`、`gradlew.bat` 和 `gradle-wrapper`

3. 把“生成/更新配置”改成真实改文件
- 会把包名、应用名、`minSdkVersion`、`targetSdkVersion`、`versionCode`、`versionName` 写回工程文件
- 会补齐缺失的根级 Gradle 文件
- 会尝试更新已有 `MainActivity.java` 的包声明
- 会补写启动图标、启动图和项目元信息

## 环境与构建补充

这一轮主要把“能不能真跑起来”继续往前推了一步：

- `BuildManager` 已支持真实调用 Gradle 构建
- 真实构建除了项目自带 `gradlew`，还增加了内置离线 `Gradle 8.7` 的准备逻辑
- `EnvironmentManager` 已支持 JDK / NDK 多版本登记与切换
- `ToolchainInstaller` 已支持内置压缩包解压，以及在线下载后解压安装
- 预检查逻辑已经独立到 `ProjectPreflightChecker`
- 包名识别已经支持从 `namespace`、`applicationId`、`AndroidManifest.xml` 多处识别
- 导入同名项目时会自动改名，避免直接覆盖旧目录

## 启动流程调整

最近一轮更新把原来“进设置页再准备环境”的方式改成了真正的启动前置流程：

1. 新增 `StartupActivity`
- 现在 APP 启动后先进入独立启动页，而不是直接进 `MainActivity`
- 启动页中间显示头像图，带有从偏大到合适大小的缩放动画
- 下方显示启动状态和进度条

2. 首次启动自动准备环境
- 首次启动会先检查内置环境是否已准备
- 如果缺失，就先解压内置 `JDK 21`、`NDK r27c`、`Gradle 8.7`
- 这些准备完成之后，才会跳转进入主界面

3. 二次启动先做环境校验
- 第二次进入时，不再重复全量解压
- 会先检查当前选中的 JDK、NDK 和离线 Gradle 是否可用
- 检查通过后直接进入主界面；如果发现损坏或缺失，再回到自动准备流程

## 图标与启动图更新

为了让入口体验统一，最近补了品牌图接入：

- 应用图标已改为直接使用 `@drawable/app_brand`
- 启动页中间头像也改为复用同一张 `app_brand.jpg`
- 这样应用图标、启动页头像和默认品牌图保持一致，减少资源分叉

## 导入与进度条更新

这部分是最近新增的 UI 改进：

1. 导入 ZIP 时显示真实解压进度
- `ProjectManager.extractZipToTemp()` 现在支持按字节量和条目数汇报解压进度
- `ProjectWorkspaceService.importZipProject()` 已向上抛出导入进度回调
- `MainActivity.importZipProject()` 已接入真实百分比显示

2. 替换旧的原生进度弹窗
- 原来的 `ProgressDialog` 已从主流程移除
- 改成页面内悬浮卡片 + 自定义进度条显示
- 新增 `theme/GlassProgressBarView.java`，用于绘制渐变、流光样式的自定义进度条

## 液态玻璃性能优化

之前的液态玻璃背景在部分设备上会明显掉帧，这一轮做了几项减负：

- 移除了最耗性能的软模糊 `BlurMaskFilter`
- 减少了背景噪点数量
- 把底色渐变改成缓存复用，不再每一帧重新创建
- 降低了液态动画的刷新频率，避免无意义高频重绘

目前的效果比旧版顺滑很多，但仍然保留了液态渐变和轻微动态感。

## 当前状态

到这一步，这个项目已经不再只是“界面壳子”，而是具备下面这些能力：

- 创建可直接编辑和继续打包的 Android 空壳工程
- 导入 ZIP 或已有目录，并识别 Android 工程根目录
- 在应用内浏览项目文件、打开文本文件、多标签编辑、自动保存
- 首次启动自动准备内置工具链
- 二次启动先做环境检查再进入主界面
- 构建前执行只读预检查
- 调用项目 `gradlew` 或内置离线 Gradle 进行真实构建
- 构建失败后提取问题并给出修复建议

## 已知限制

- `Android SDK` 这部分仍然依赖内置包或手动准备，完整的 SDK 组件管理还可以继续加强
- 对“导入项目”的包名修改仍属于轻量更新，复杂项目不保证自动完成全量包重命名
- 应用图标目前直接使用 `drawable` 里的品牌图，若后续要适配更多启动器，建议补齐更标准的 `mipmap` 多密度图标方案
- 本地验证时，项目自带 Gradle Wrapper 下载 `gradle-8.7-bin.zip` 仍可能因网络超时失败，所以完整构建链路更适合在设备端或已具备缓存的环境里验证
