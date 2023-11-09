package com.github.manu156.jpqltosql.Util;

import org.jetbrains.annotations.NotNull;

public class CStringUtil {
    public static String strip(@NotNull String t, @NotNull String s) {
        return stripTrailing(stripLeading(t, s), s);
    }

    public static String stripLeading(@NotNull String t, @NotNull String s) {
        return t.startsWith(s) ? t.substring(s.length()) : t;
    }

    public static String stripTrailing(@NotNull String t, @NotNull String s) {
        return t.endsWith(s) ? t.substring(0, t.length()-s.length()) : t;
    }

    public static String strip(@NotNull String t, char c) {
        return stripTrailing(stripLeading(t, c), c);
    }

    public static String stripLeading(@NotNull String t, char c) {
        return t.charAt(0) == c ? t.substring(1) : t;
    }

    public static String stripTrailing(@NotNull String t, char c) {
        return t.charAt(t.length()-1) == c ? t.substring(0, t.length()-1) : t;
    }
}
