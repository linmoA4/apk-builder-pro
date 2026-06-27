package com.LM.pack.project;

import android.content.Context;
import android.content.res.AssetManager;
import com.LM.pack.model.ProjectConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

public class ProjectManager {

    public File createShellProject(Context context, ProjectConfig config, String projectRootPath) throws IOException {
        File projectRoot = new File(projectRootPath);
        File appDir = new File(projectRoot, "app");
        File javaDir = new File(appDir, "src/main/java/" + config.getPackagePath());
        File resLayoutDir = new File(appDir, "src/main/res/layout");
        File resValuesDir = new File(appDir, "src/main/res/values");
        File manifestFile = new File(appDir, "src/main/AndroidManifest.xml");

        ensureDir(projectRoot);
        ensureDir(new File(projectRoot, "gradle/wrapper"));
        ensureDir(appDir);
        ensureDir(new File(appDir, "src/main"));
        ensureDir(javaDir);
        ensureDir(resLayoutDir);
        ensureDir(resValuesDir);

        writeText(new File(projectRoot, "settings.gradle"), buildSettingsGradle(config));
        writeText(new File(projectRoot, "build.gradle"), buildRootGradle());
        writeText(new File(projectRoot, "gradle.properties"), buildGradleProperties());
        writeText(new File(appDir, "build.gradle"), buildAppGradle(config));
        writeText(manifestFile, buildManifest(config));
        writeText(new File(javaDir, "MainActivity.java"), buildShellMainActivity(config));
        writeText(new File(resLayoutDir, "activity_main.xml"), buildShellLayout(config));
        writeText(new File(resValuesDir, "strings.xml"), buildStrings(config));
        writeText(new File(resValuesDir, "colors.xml"), buildColors());
        writeText(new File(resValuesDir, "styles.xml"), buildStyles());
        writeText(new File(appDir, "proguard-rules.pro"), "");

        copyWrapperAssets(context, projectRoot);
        return projectRoot;
    }

    public void updateProjectConfig(ProjectConfig config, String projectRootPath) throws IOException {
        File projectRoot = new File(projectRootPath);
        File appDir = new File(projectRoot, "app");
        File manifestFile = new File(appDir, "src/main/AndroidManifest.xml");
        File appBuildGradle = new File(appDir, "build.gradle");
        File stringsFile = new File(appDir, "src/main/res/values/strings.xml");

        ensureDir(projectRoot);
        ensureDir(appDir);
        ensureDir(new File(appDir, "src/main/res/values"));

        writeText(new File(projectRoot, "settings.gradle"), buildSettingsGradle(config));
        if (!new File(projectRoot, "build.gradle").exists()) {
            writeText(new File(projectRoot, "build.gradle"), buildRootGradle());
        }
        if (!new File(projectRoot, "gradle.properties").exists()) {
            writeText(new File(projectRoot, "gradle.properties"), buildGradleProperties());
        }
        if (!appBuildGradle.exists()) {
            writeText(appBuildGradle, buildAppGradle(config));
        } else {
            writeText(appBuildGradle, updateAppBuildGradle(readText(appBuildGradle), config));
        }

        if (!manifestFile.exists()) {
            writeText(manifestFile, buildManifest(config));
        } else {
            writeText(manifestFile, updateManifest(readText(manifestFile), config));
        }

        if (!stringsFile.exists()) {
            writeText(stringsFile, buildStrings(config));
        } else {
            writeText(stringsFile, updateStrings(readText(stringsFile), config));
        }

        updateExistingMainActivityPackage(projectRoot, config);
    }

    private void updateExistingMainActivityPackage(File projectRoot, ProjectConfig config) throws IOException {
        File javaRoot = new File(projectRoot, "app/src/main/java");
        if (!javaRoot.exists()) {
            return;
        }
        File existingMainActivity = findFileByName(javaRoot, "MainActivity.java");
        if (existingMainActivity == null) {
            return;
        }

        String javaContent = readText(existingMainActivity);
        String updatedContent;
        if (javaContent.contains("package ")) {
            updatedContent = javaContent.replaceFirst(
                "package\\s+[A-Za-z0-9_\\.]+;",
                "package " + Matcher.quoteReplacement(config.getPackageName()) + ";"
            );
        } else {
            updatedContent = "package " + config.getPackageName() + ";\n\n" + javaContent;
        }
        writeText(existingMainActivity, updatedContent);
    }

    private File findFileByName(File dir, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                File match = findFileByName(file, fileName);
                if (match != null) {
                    return match;
                }
            } else if (fileName.equals(file.getName())) {
                return file;
            }
        }
        return null;
    }

    private String updateAppBuildGradle(String content, ProjectConfig config) {
        String updated = content;
        updated = updated.replaceAll("namespace\\s+'[^']+'", "namespace '" + Matcher.quoteReplacement(config.getPackageName()) + "'");
        updated = updated.replaceAll("applicationId\\s+\"[^\"]+\"", "applicationId \"" + Matcher.quoteReplacement(config.getPackageName()) + "\"");
        updated = updated.replaceAll("minSdkVersion\\s+\\d+", "minSdkVersion " + config.getMinSdk());
        updated = updated.replaceAll("targetSdkVersion\\s+\\d+", "targetSdkVersion " + config.getTargetSdk());
        if (!updated.contains("namespace")) {
            return buildAppGradle(config);
        }
        return updated;
    }

    private String updateManifest(String content, ProjectConfig config) {
        String updated = content;
        if (updated.contains("package=\"")) {
            updated = updated.replaceFirst("package=\"[^\"]+\"", "package=\"" + Matcher.quoteReplacement(config.getPackageName()) + "\"");
        }
        if (!updated.contains("android:label=\"@string/app_name\"")) {
            updated = updated.replaceFirst("<application", "<application\n        android:label=\"@string/app_name\"");
        }
        return updated;
    }

    private String updateStrings(String content, ProjectConfig config) {
        if (content.contains("name=\"app_name\"")) {
            return content.replaceFirst(
                "<string\\s+name=\"app_name\">.*?</string>",
                "<string name=\"app_name\">" + escapeXml(config.getAppName()) + "</string>"
            );
        }
        return "<resources>\n    <string name=\"app_name\">" + escapeXml(config.getAppName()) + "</string>\n</resources>\n";
    }

    private String buildSettingsGradle(ProjectConfig config) {
        return "rootProject.name = \"" + escapeGradle(config.getAppName()) + "\"\ninclude ':app'\n";
    }

    private String buildRootGradle() {
        return "buildscript {\n"
            + "    repositories {\n"
            + "        google()\n"
            + "        mavenCentral()\n"
            + "    }\n"
            + "    dependencies {\n"
            + "        classpath 'com.android.tools.build:gradle:8.5.2'\n"
            + "    }\n"
            + "}\n\n"
            + "allprojects {\n"
            + "    repositories {\n"
            + "        google()\n"
            + "        mavenCentral()\n"
            + "    }\n"
            + "}\n\n"
            + "task clean(type: Delete) {\n"
            + "    delete rootProject.buildDir\n"
            + "}\n";
    }

    private String buildGradleProperties() {
        return "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n"
            + "android.useAndroidX=false\n"
            + "android.nonTransitiveRClass=false\n"
            + "android.nonFinalResIds=false\n";
    }

    private String buildAppGradle(ProjectConfig config) {
        return "apply plugin: 'com.android.application'\n\n"
            + "android {\n"
            + "    namespace '" + escapeGradle(config.getPackageName()) + "'\n"
            + "    compileSdkVersion 36\n"
            + "    ndkVersion \"27.2.12479018\"\n\n"
            + "    defaultConfig {\n"
            + "        applicationId \"" + escapeGradle(config.getPackageName()) + "\"\n"
            + "        minSdkVersion " + config.getMinSdk() + "\n"
            + "        targetSdkVersion " + config.getTargetSdk() + "\n"
            + "        versionCode 1\n"
            + "        versionName \"1.0\"\n"
            + "    }\n\n"
            + "    ndk {\n"
            + "        abiFilters 'arm64-v8a'\n"
            + "    }\n\n"
            + "    compileOptions {\n"
            + "        sourceCompatibility JavaVersion.VERSION_21\n"
            + "        targetCompatibility JavaVersion.VERSION_21\n"
            + "    }\n\n"
            + "    buildFeatures {\n"
            + "        buildConfig true\n"
            + "    }\n\n"
            + "    buildTypes {\n"
            + "        release {\n"
            + "            minifyEnabled false\n"
            + "            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'\n"
            + "        }\n"
            + "        debug {\n"
            + "            minifyEnabled false\n"
            + "        }\n"
            + "    }\n\n"
            + "    lintOptions {\n"
            + "        abortOnError false\n"
            + "    }\n"
            + "}\n\n"
            + "dependencies {\n"
            + "}\n";
    }

    private String buildManifest(ProjectConfig config) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    package=\"" + escapeXml(config.getPackageName()) + "\">\n\n"
            + "    <uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" />\n"
            + "    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" />\n"
            + "    <uses-permission android:name=\"android.permission.MANAGE_EXTERNAL_STORAGE\" />\n"
            + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n\n"
            + "    <application\n"
            + "        android:allowBackup=\"true\"\n"
            + "        android:label=\"@string/app_name\"\n"
            + "        android:requestLegacyExternalStorage=\"true\"\n"
            + "        android:supportsRtl=\"true\"\n"
            + "        android:theme=\"@style/AppTheme\">\n\n"
            + "        <activity\n"
            + "            android:name=\".MainActivity\"\n"
            + "            android:exported=\"true\">\n"
            + "            <intent-filter>\n"
            + "                <action android:name=\"android.intent.action.MAIN\" />\n"
            + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
            + "            </intent-filter>\n"
            + "        </activity>\n\n"
            + "    </application>\n\n"
            + "</manifest>\n";
    }

    private String buildShellMainActivity(ProjectConfig config) {
        return "package " + config.getPackageName() + ";\n\n"
            + "import android.app.Activity;\n"
            + "import android.os.Bundle;\n"
            + "import android.widget.TextView;\n\n"
            + "public class MainActivity extends Activity {\n\n"
            + "    @Override\n"
            + "    protected void onCreate(Bundle savedInstanceState) {\n"
            + "        super.onCreate(savedInstanceState);\n"
            + "        setContentView(R.layout.activity_main);\n"
            + "        TextView textView = (TextView) findViewById(R.id.tvHello);\n"
            + "        textView.setText(\"欢迎使用 " + escapeJava(config.getAppName()) + "\");\n"
            + "    }\n"
            + "}\n";
    }

    private String buildShellLayout(ProjectConfig config) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:background=\"#000000\"\n"
            + "    android:gravity=\"center\"\n"
            + "    android:orientation=\"vertical\"\n"
            + "    android:padding=\"24dp\">\n\n"
            + "    <TextView\n"
            + "        android:id=\"@+id/tvHello\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"" + escapeXml(config.getAppName()) + "\"\n"
            + "        android:textColor=\"#FFFFFF\"\n"
            + "        android:textSize=\"24sp\"\n"
            + "        android:textStyle=\"bold\" />\n\n"
            + "</LinearLayout>\n";
    }

    private String buildStrings(ProjectConfig config) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <string name=\"app_name\">" + escapeXml(config.getAppName()) + "</string>\n"
            + "</resources>\n";
    }

    private String buildColors() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <color name=\"background_dark\">#000000</color>\n"
            + "    <color name=\"panel_dark\">#1E1E1E</color>\n"
            + "    <color name=\"log_panel\">#2D2D2D</color>\n"
            + "    <color name=\"text_primary\">#FFFFFF</color>\n"
            + "</resources>\n";
    }

    private String buildStyles() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <style name=\"AppTheme\" parent=\"@android:style/Theme.DeviceDefault.NoActionBar\">\n"
            + "        <item name=\"android:windowBackground\">@color/background_dark</item>\n"
            + "    </style>\n"
            + "</resources>\n";
    }

    private void copyWrapperAssets(Context context, File projectRoot) throws IOException {
        copyAsset(context.getAssets(), "project_template/gradlew", new File(projectRoot, "gradlew"));
        copyAsset(context.getAssets(), "project_template/gradlew.bat", new File(projectRoot, "gradlew.bat"));
        copyAsset(
            context.getAssets(),
            "project_template/gradle/wrapper/gradle-wrapper.jar",
            new File(projectRoot, "gradle/wrapper/gradle-wrapper.jar")
        );
        copyAsset(
            context.getAssets(),
            "project_template/gradle/wrapper/gradle-wrapper.properties",
            new File(projectRoot, "gradle/wrapper/gradle-wrapper.properties")
        );
        new File(projectRoot, "gradlew").setExecutable(true);
    }

    private void copyAsset(AssetManager assetManager, String assetPath, File targetFile) throws IOException {
        ensureDir(targetFile.getParentFile());
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = assetManager.open(assetPath);
            outputStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private void writeText(File file, String content) throws IOException {
        ensureDir(file.getParentFile());
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private String readText(File file) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = new java.io.FileInputStream(file);
            byte[] buffer = new byte[4096];
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void ensureDir(File dir) throws IOException {
        if (dir == null) {
            return;
        }
        if (dir.exists()) {
            return;
        }
        if (!dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dir.getAbsolutePath());
        }
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeGradle(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
