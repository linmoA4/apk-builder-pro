package com.LM.pack.service;

import com.LM.pack.model.FileTreeItem;
import com.LM.pack.model.ProjectEntry;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ProjectFileService {
    public ArrayList<FileTreeItem> buildFileTree(ProjectEntry currentProject, ArrayList<String> expandedDirs) {
        ArrayList<FileTreeItem> items = new ArrayList<FileTreeItem>();
        if (currentProject == null) {
            return items;
        }
        File projectRoot = new File(currentProject.getProjectDir());
        flattenDirectory(projectRoot, 0, expandedDirs, items);
        return items;
    }

    public void ensureDefaultExpandedDirs(ProjectEntry currentProject, ArrayList<String> expandedDirs) {
        expandedDirs.clear();
        if (currentProject == null) {
            return;
        }
        expandedDirs.add(currentProject.getProjectDir());
        expandedDirs.add(new File(currentProject.getProjectDir(), "app").getAbsolutePath());
        expandedDirs.add(new File(currentProject.getProjectDir(), "app/src").getAbsolutePath());
        expandedDirs.add(new File(currentProject.getProjectDir(), "app/src/main").getAbsolutePath());
    }

    public ArrayList<FileTreeItem> listDirectory(ProjectEntry currentProject, File directory) {
        ArrayList<FileTreeItem> items = new ArrayList<FileTreeItem>();
        if (currentProject == null) {
            return items;
        }
        File root = new File(currentProject.getProjectDir());
        File currentDir = directory == null ? root : directory;
        if (!currentDir.exists() || !currentDir.isDirectory()) {
            return items;
        }
        File[] files = currentDir.listFiles();
        if (files == null) {
            return items;
        }
        ArrayList<File> directories = new ArrayList<File>();
        ArrayList<File> normalFiles = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (shouldIgnore(file)) {
                continue;
            }
            if (file.isDirectory()) {
                directories.add(file);
            } else if (isTextEditableFile(file)) {
                normalFiles.add(file);
            }
        }
        Collections.sort(directories, new FileNameComparator());
        Collections.sort(normalFiles, new FileNameComparator());
        for (int i = 0; i < directories.size(); i++) {
            items.add(new FileTreeItem(directories.get(i), 0, true));
        }
        for (int i = 0; i < normalFiles.size(); i++) {
            items.add(new FileTreeItem(normalFiles.get(i), 0, false));
        }
        return items;
    }

    public boolean isTextEditableFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".java")
            || name.endsWith(".kt")
            || name.endsWith(".xml")
            || name.endsWith(".gradle")
            || name.endsWith(".kts")
            || name.endsWith(".properties")
            || name.endsWith(".txt")
            || name.endsWith(".md")
            || "gradlew".equals(name);
    }

    public String buildDisplayPath(ProjectEntry currentProject, File file) {
        if (file == null) {
            return "请选择一个文件";
        }
        if (currentProject == null) {
            return file.getAbsolutePath();
        }
        String projectRoot = currentProject.getProjectDir();
        String fullPath = file.getAbsolutePath();
        if (fullPath.startsWith(projectRoot)) {
            String relative = fullPath.substring(projectRoot.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return currentProject.getProjectName() + "  /  " + relative;
        }
        return fullPath;
    }

    public File resolveIssueFile(ProjectEntry currentProject, String path) {
        if (path == null || path.length() == 0 || currentProject == null) {
            return null;
        }
        File direct = new File(path);
        if (direct.exists()) {
            return direct;
        }
        File relative = new File(currentProject.getProjectDir(), path);
        if (relative.exists()) {
            return relative;
        }
        return findFileBySuffix(new File(currentProject.getProjectDir()), new File(path).getName());
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

    private void flattenDirectory(File dir, int depth, ArrayList<String> expandedDirs, ArrayList<FileTreeItem> items) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        items.add(new FileTreeItem(dir, depth, true));
        if (!expandedDirs.contains(dir.getAbsolutePath())) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        ArrayList<File> directories = new ArrayList<File>();
        ArrayList<File> normalFiles = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (shouldIgnore(file)) {
                continue;
            }
            if (file.isDirectory()) {
                directories.add(file);
            } else if (isTextEditableFile(file)) {
                normalFiles.add(file);
            }
        }
        Collections.sort(directories, new FileNameComparator());
        Collections.sort(normalFiles, new FileNameComparator());
        for (int i = 0; i < directories.size(); i++) {
            flattenDirectory(directories.get(i), depth + 1, expandedDirs, items);
        }
        for (int i = 0; i < normalFiles.size(); i++) {
            items.add(new FileTreeItem(normalFiles.get(i), depth + 1, false));
        }
    }

    private boolean shouldIgnore(File file) {
        String name = file.getName();
        return ".git".equals(name) || ".gradle".equals(name) || "build".equals(name) || ".lmproject".equals(name);
    }

    private static class FileNameComparator implements Comparator<File> {
        @Override
        public int compare(File a, File b) {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    }
}
