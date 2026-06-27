package com.LM.pack.service;

import android.content.Context;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.ProjectConfig;
import com.LM.pack.model.ProjectEntry;
import com.LM.pack.project.ProjectManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ProjectWorkspaceService {
    public interface ImportProgressListener {
        void onProgress(String message, int percent);
    }

    public static class ImportResult {
        private final File detectedRoot;
        private final File importedRoot;

        public ImportResult(File detectedRoot, File importedRoot) {
            this.detectedRoot = detectedRoot;
            this.importedRoot = importedRoot;
        }

        public File getDetectedRoot() {
            return detectedRoot;
        }

        public File getImportedRoot() {
            return importedRoot;
        }
    }

    private final ProjectManager projectManager;
    private final EnvironmentManager environmentManager;

    public ProjectWorkspaceService(ProjectManager projectManager, EnvironmentManager environmentManager) {
        this.projectManager = projectManager;
        this.environmentManager = environmentManager;
    }

    public ArrayList<ProjectEntry> loadProjects() {
        ArrayList<ProjectEntry> entries = projectManager.scanProjects(
            environmentManager.getManagedProjectRootDir(),
            environmentManager.getImportedProjectRootDir()
        );
        Collections.sort(entries, new Comparator<ProjectEntry>() {
            @Override
            public int compare(ProjectEntry a, ProjectEntry b) {
                return a.getProjectName().compareToIgnoreCase(b.getProjectName());
            }
        });
        return entries;
    }

    public File createProject(Context context, ProjectConfig config) throws IOException {
        return projectManager.createShellProject(context, config, environmentManager.getProjectRootDir(config.getAppName()));
    }

    public ImportResult importZipProject(File zipFile) throws IOException {
        return importZipProject(zipFile, null);
    }

    public ImportResult importZipProject(File zipFile, ImportProgressListener listener) throws IOException {
        File tempRoot = new File(environmentManager.getImportTempDir(), sanitizeName(zipFile.getName()));
        projectManager.extractZipToTemp(zipFile, tempRoot, new ProjectManager.ExtractProgressListener() {
            @Override
            public void onProgress(String message, int percent) {
                if (listener != null) {
                    listener.onProgress(message, percent);
                }
            }
        });
        if (listener != null) {
            listener.onProgress("正在识别 Android 工程根目录...", 96);
        }
        File detectedRoot = projectManager.deepFindAndroidProject(tempRoot);
        if (detectedRoot == null) {
            throw new IOException("没有从压缩包中识别到有效的 Android 工程根目录。");
        }
        File importedRoot = projectManager.importProject(
            detectedRoot,
            stripExtension(zipFile.getName()),
            environmentManager.getImportedProjectRootDir()
        );
        if (listener != null) {
            listener.onProgress("导入完成，正在打开项目...", 100);
        }
        return new ImportResult(detectedRoot, importedRoot);
    }

    public ImportResult importFolderProject(File folder) throws IOException {
        File detectedRoot = projectManager.deepFindAndroidProject(folder);
        if (detectedRoot == null) {
            throw new IOException("没有找到完整的 Android 工程目录。");
        }
        File importedRoot = projectManager.importProject(detectedRoot, detectedRoot.getName(), environmentManager.getImportedProjectRootDir());
        return new ImportResult(detectedRoot, importedRoot);
    }

    public ProjectEntry readProjectEntry(File projectRoot) {
        return projectManager.readProjectEntry(projectRoot);
    }

    public boolean isZipFile(File file) {
        return projectManager.isZipFile(file);
    }

    public String readText(File file) throws IOException {
        return projectManager.readText(file);
    }

    public void writeText(File file, String content) throws IOException {
        projectManager.writeText(file, content);
    }

    public File resolveDefaultEditorFile(ProjectEntry entry) {
        if (entry == null) {
            return null;
        }
        File projectDir = new File(entry.getProjectDir());
        File manifestFile = new File(projectDir, "app/src/main/AndroidManifest.xml");
        File buildGradle = new File(projectDir, "app/build.gradle");
        File buildGradleKts = new File(projectDir, "app/build.gradle.kts");
        ArrayList<String> packageCandidates = projectManager.readPackageCandidates(projectDir);
        String entryPackage = safeText(entry.getPackageName());
        if (entryPackage.length() > 0 && !packageCandidates.contains(entryPackage)) {
            packageCandidates.add(entryPackage);
        }
        for (int i = 0; i < packageCandidates.size(); i++) {
            String packageName = packageCandidates.get(i);
            File mainJava = new File(projectDir, "app/src/main/java/" + packageName.replace('.', '/') + "/MainActivity.java");
            File mainKt = new File(projectDir, "app/src/main/kotlin/" + packageName.replace('.', '/') + "/MainActivity.kt");
            if (mainJava.exists()) {
                return mainJava;
            }
            if (mainKt.exists()) {
                return mainKt;
            }
        }
        if (manifestFile.exists()) {
            return manifestFile;
        }
        if (buildGradle.exists()) {
            return buildGradle;
        }
        if (buildGradleKts.exists()) {
            return buildGradleKts;
        }
        File fallbackMainActivity = findFileBySuffix(projectDir, "MainActivity.java");
        if (fallbackMainActivity == null) {
            fallbackMainActivity = findFileBySuffix(projectDir, "MainActivity.kt");
        }
        return fallbackMainActivity;
    }

    public String getPlannedProjectRoot(String appName) {
        return environmentManager.getProjectRootDir(appName);
    }

    public File findFileBySuffix(File dir, String name) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                File found = findFileBySuffix(file, name);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(name)) {
                return file;
            }
        }
        return null;
    }

    private String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String sanitizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('/', '_').replace('\\', '_');
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
