package com.xeon;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppVersion {
    public static final String UNKNOWN = "0.0.0";

    private static final String VERSION_RESOURCE = "/com/xeon/app.properties";
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private AppVersion() {
    }

    public static String current() {
        Package pkg = AppVersion.class.getPackage();
        String implementationVersion = pkg == null ? null : pkg.getImplementationVersion();
        if (hasText(implementationVersion)) {
            return implementationVersion.trim();
        }

        try (InputStream in = AppVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version");
                if (hasText(version)) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
        }
        return UNKNOWN;
    }

    public static boolean isNewerThan(String current, String previous) {
        if (!hasText(previous)) {
            return true;
        }
        return compare(current, previous) > 0;
    }

    public static int compare(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.equals(normalizedRight)) {
            return 0;
        }

        List<Integer> leftParts = numericParts(normalizedLeft);
        List<Integer> rightParts = numericParts(normalizedRight);
        if (!leftParts.isEmpty() && !rightParts.isEmpty()) {
            int size = Math.max(leftParts.size(), rightParts.size());
            for (int i = 0; i < size; i++) {
                int leftValue = i < leftParts.size() ? leftParts.get(i) : 0;
                int rightValue = i < rightParts.size() ? rightParts.get(i) : 0;
                int compared = Integer.compare(leftValue, rightValue);
                if (compared != 0) {
                    return compared;
                }
            }
        }
        return normalizedLeft.compareToIgnoreCase(normalizedRight);
    }

    private static List<Integer> numericParts(String version) {
        List<Integer> parts = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(version);
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                parts.add(Integer.MAX_VALUE);
            }
        }
        return parts;
    }

    private static String normalize(String version) {
        if (!hasText(version)) {
            return UNKNOWN;
        }
        String normalized = version.trim();
        if (normalized.length() > 1 && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')
                && Character.isDigit(normalized.charAt(1))) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
