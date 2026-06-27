# LM打包工具重构说明

这版重构主要做了三件事：

1. 拆分 `MainActivity`
- 新增 `build/BuildManager.java`
- 新增 `env/EnvironmentManager.java`
- 新增 `project/ProjectManager.java`
- 新增 `log/LogManager.java`
- 新增 `model/BuildResult.java`
- 新增 `model/EnvironmentState.java`
- 新增 `model/ProjectConfig.java`

2. 把“创建空壳项目”改成真实写文件
- 现在会在 `/storage/emulated/0/LMBuildTools/projects/<应用名>` 下真实创建工程目录
- 会写入 `settings.gradle`、根级 `build.gradle`、`gradle.properties`
- 会写入 `app/build.gradle`、`AndroidManifest.xml`
- 会写入 `MainActivity.java`、`activity_main.xml`、`strings.xml`
- 会从 `assets/project_template/` 复制 `gradlew` 和 `gradle-wrapper`

3. 把“生成/更新配置”改成真实改文件
- 会把包名、应用名、`minSdkVersion`、`targetSdkVersion` 写回工程文件
- 会补齐缺失的根级 Gradle 文件
- 会尝试更新已有 `MainActivity.java` 的包声明

额外说明：

- 真实打包逻辑已经从 `MainActivity` 移到了 `BuildManager`
- 真实构建改为 `ProcessBuilder("./gradlew", "assembleDebug", "--stacktrace")`
- 当前版本仍然保留了原有的老式 UI 和 `ProgressDialog`，这是为了先保证功能落地，再做第二轮界面和兼容性升级

已知限制：

- `安装 JDK/NDK` 目前仍是“安装计划 + 已存在目录登记”，还没有自动下载和解压
- 对“导入项目”的包名修改属于轻量更新，复杂项目不保证自动完成全量包重命名
- 本地验证时，Gradle Wrapper 下载 `gradle-8.7-bin.zip` 因连接超时未完成，所以没有跑完整构建
