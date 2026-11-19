package com.demo.util;

public final class Html {

    private Html() {}

    /** Escapes user-provided text before embedding it in HTML output. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    /** Formats the createdAt timestamp for display: "2026-11-19T14:30" -> "2026-11-19 14:30". */
    public static String formatDate(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.length() < 16) {
            return "";
        }
        return isoTimestamp.substring(0, 16).replace("T", " ");
    }
}
