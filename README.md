# LM打包工具

这是一个完整目录结构的 Android 项目骨架，包含：

- 根级 `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `gradlew` / `gradlew.bat` / `gradle/wrapper`
- `app` 模块
- `AndroidManifest.xml`
- `MainActivity.java`
- 深色工具界面与日志分色逻辑

## 导入方式

1. 作为 Gradle 项目导入 Android Studio。
2. 或在 AIDE 中按目录打开，再按需要调整 Gradle 兼容版本。

## 当前特性

- 创建空壳项目
- 导入项目，并在同名冲突时自动改名保护旧目录
- JDK / NDK 多版本环境状态记录
- 打包前只读环境检查
- 包名识别支持 `namespace`、`applicationId`、Manifest 多来源解析
- 对导入的带 `gradlew` 项目尝试执行真实 `assembleDebug`

## 注意

- 真实打包依赖目标设备或环境里可用的 JDK、NDK、Gradle Wrapper。
- 预检查不再自动改写项目文件，缺少 `local.properties` 时会给出修复提示。
- 新建模板默认启用 AndroidX，并移除了过时的全盘存储权限默认值。
- 如果你要把它做成完全自动下载解压工具链的版本，还需要继续补下载器与解压器逻辑。
