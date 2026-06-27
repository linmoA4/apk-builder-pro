package com.LM.pack.env;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public final class IntegrityVerifier {

    private IntegrityVerifier() {
    }

    public static boolean matchesSha256(File file, String expectedSha256) {
        String expected = normalize(expectedSha256);
        if (file == null || !file.exists() || expected.length() == 0) {
            return false;
        }
        try {
            return expected.equals(sha256(file));
        } catch (Exception e) {
            return false;
        }
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        BufferedInputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        byte[] result = digest.digest();
        StringBuilder builder = new StringBuilder(result.length * 2);
        for (int i = 0; i < result.length; i++) {
            String hex = Integer.toHexString(result[i] & 0xFF);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
