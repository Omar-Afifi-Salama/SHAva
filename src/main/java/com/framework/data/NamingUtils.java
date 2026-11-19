package com.framework.data;

public final class NamingUtils {

    private NamingUtils() {}

    public static String toSnakeCase(String camelCase) {
        // Turns "createdAt" -> "created_at", "userID" -> "user_id"
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    // Column/table names get concatenated into SQL, so they must be plain
    // identifiers to prevent SQL injection
    public static boolean isSafeIdentifier(String identifier) {
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}
