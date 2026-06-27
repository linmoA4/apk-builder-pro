package com.LM.pack.model;

public class ProjectSigningConfig {

    private final boolean enabled;
    private final String storeFilePath;
    private final String storePassword;
    private final String keyAlias;
    private final String keyPassword;

    public ProjectSigningConfig(
        boolean enabled,
        String storeFilePath,
        String storePassword,
        String keyAlias,
        String keyPassword
    ) {
        this.enabled = enabled;
        this.storeFilePath = safeText(storeFilePath);
        this.storePassword = safeText(storePassword);
        this.keyAlias = safeText(keyAlias);
        this.keyPassword = safeText(keyPassword);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getStoreFilePath() {
        return storeFilePath;
    }

    public String getStorePassword() {
        return storePassword;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public boolean isComplete() {
        return enabled
            && storeFilePath.length() > 0
            && storePassword.length() > 0
            && keyAlias.length() > 0
            && keyPassword.length() > 0;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
