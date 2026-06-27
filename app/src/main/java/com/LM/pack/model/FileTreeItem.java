package com.LM.pack.model;

import java.io.File;

public class FileTreeItem {
    private final File file;
    private final int depth;
    private final boolean directory;

    public FileTreeItem(File file, int depth, boolean directory) {
        this.file = file;
        this.depth = depth;
        this.directory = directory;
    }

    public File getFile() {
        return file;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isDirectory() {
        return directory;
    }
}
