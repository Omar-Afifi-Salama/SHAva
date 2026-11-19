package com.framework.data;

public final class NamingUtils {

    private NamingUtils() {}

    public static String toSnakeCase(String camelCase) {
        // Turns "createdAt" -> "created_at", "userID" -> "user_id"
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
}
