# APK Builder Pro 外置环境打包说明

这个仓库已经改成“外置环境模式”。
现在 APP 不再把 `JDK / NDK / SDK / Gradle` 大包内嵌进 APK，打包时应优先按项目实际需求自动检测，再从外部下载对应环境。

## 先看哪里

- 直链总表：`安卓开发直链清单_2020到2026_按镜像源拆分.md`
- 仓库内的 Wrapper 镜像：`gradle/wrapper/gradle-wrapper.jar`
- APP 里的环境检测与下载逻辑：
  - `app/src/main/java/com/LM/pack/env/EnvironmentManager.java`
  - `app/src/main/java/com/LM/pack/env/ToolchainInstaller.java`
  - `app/src/main/java/com/LM/pack/build/BuildManager.java`
  - `app/src/main/java/com/LM/pack/build/ProjectPreflightChecker.java`

## 打包规则

1. 先读取目标项目的 `gradle/wrapper/gradle-wrapper.properties`
2. 再读取 `app/build.gradle` 或 `app/build.gradle.kts`
3. 自动识别这些字段：
   - `compileSdk`
   - `buildToolsVersion`
   - `ndkVersion`
   - `sourceCompatibility`
   - AGP 版本
   - Gradle Wrapper 版本
4. 再决定推荐环境，不要直接写死一种环境全项目通吃

## JDK 推荐规则

- `Gradle >= 8.5` 或 `AGP >= 8.2`：优先 `JDK 21`
- `Gradle 7.x` 或 `AGP 7.x`：优先 `JDK 17`
- 更老的 Android 项目：优先 `JDK 11`
- 极老项目或明确要求 `1.8`：最后才回退 `JDK 8`

## NDK 推荐规则

- 如果项目显式声明 `ndkVersion = 29...`：用 `NDK r29`
- 如果项目显式声明 `ndkVersion = 28...`：用 `NDK r28c`
- 没写或普通项目：默认 `NDK r27c`

## SDK / Build-Tools 规则

- `compileSdk = X` 时，至少安装：
  - `platform-tools`
  - `platforms;android-X`
  - `build-tools;版本号`
- 如果项目没有显式写 `buildToolsVersion`，就按 `compileSdk.0.0` 推断

## 下载源规则

只区分两类：

- 国内：优先镜像源
- 国外：优先官方源

Gradle、SDK、NDK 的直链优先从根目录的 `安卓开发直链清单_2020到2026_按镜像源拆分.md` 里取。

## 缺少 Wrapper 时

如果目标项目缺少 `gradle/wrapper/gradle-wrapper.jar` 或 `gradle/wrapper/gradle-wrapper.properties`：

1. 先弹出 APP 自定义弹窗
2. 允许用户二选一：
   - 用仓库源补齐
   - 用官方源补齐
3. 补齐后重新检测，再继续打包

仓库源建议地址：

- `https://raw.githubusercontent.com/linmoA4/apk-builder-pro/main/gradle/wrapper/gradle-wrapper.jar`

官方源建议地址：

- `https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar`

`gradle-wrapper.properties` 可以直接按目标 Gradle 版本生成，例如：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

## 给别的 AI 的执行要求

- 不要再尝试从 APK 内提取工具链
- 直接按上面的检测规则决定 JDK / NDK / SDK / Gradle
- 国内用户优先用镜像源，国外用户优先用官方源
- 缺少 `gradle-wrapper.jar` 时，优先用仓库根目录提供的镜像补齐
- 补齐完成后再执行 `assembleDebug` 或对应构建任务

## 当前改造目标

- 减少 APK 体积
- 把工具链从“内嵌资源”改成“外置下载”
- 把错误提示从系统弹窗改成 APP 自定义弹窗
- 把缺失 Wrapper 的修复流程前置到打包前检查阶段
