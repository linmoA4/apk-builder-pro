package com.LM.pack.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EnvironmentState {

    private final Map<String, String> installedJdks;
    private final String androidSdkDir;
    private final Map<String, String> installedNdks;

    public EnvironmentState(
        Map<String, String> installedJdks,
        String androidSdkDir,
        Map<String, String> installedNdks
    ) {
        this.installedJdks = freeze(installedJdks);
        this.androidSdkDir = androidSdkDir == null ? "" : androidSdkDir.trim();
        this.installedNdks = freeze(installedNdks);
    }

    public Map<String, String> getInstalledJdks() {
        return installedJdks;
    }

    public String getAndroidSdkDir() {
        return androidSdkDir;
    }

    public Map<String, String> getInstalledNdks() {
        return installedNdks;
    }

    public String getInstalledJdkName() {
        return firstKey(installedJdks);
    }

    public String getInstalledJdkDir() {
        return firstValue(installedJdks);
    }

    public String getInstalledJdkDir(String name) {
        return getToolDir(installedJdks, name);
    }

    public String getInstalledNdkName() {
        return firstKey(installedNdks);
    }

    public String getInstalledNdkDir() {
        return firstValue(installedNdks);
    }

    public String getInstalledNdkDir(String name) {
        return getToolDir(installedNdks, name);
    }

    private Map<String, String> freeze(Map<String, String> source) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                String value = entry.getValue() == null ? "" : entry.getValue().trim();
                if (key.length() == 0 || value.length() == 0) {
                    continue;
                }
                copy.put(key, value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private String getToolDir(Map<String, String> tools, String name) {
        if (name == null) {
            return "";
        }
        String value = tools.get(name.trim());
        return value == null ? "" : value;
    }

    private String firstKey(Map<String, String> tools) {
        for (Map.Entry<String, String> entry : tools.entrySet()) {
            return entry.getKey();
        }
        return "";
    }

    private String firstValue(Map<String, String> tools) {
        for (Map.Entry<String, String> entry : tools.entrySet()) {
            return entry.getValue();
        }
        return "";
    }
}
