package com.framework.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects column definitions for one table, then renders them as SQL.
 * Columns are registered as soon as their method is called; later config
 * calls (notNull, defaultValue, ...) mutate the same Column object.
 */
public class TableBuilder {

    private String primaryKeySql;
    private final List<Column> columns = new ArrayList<>();
    private final List<String> foreignKeys = new ArrayList<>();

    // --- Column definitions ---

    public TableBuilder id(String name) {
        this.primaryKeySql = NamingUtils.toSnakeCase(name) + " INTEGER PRIMARY KEY AUTOINCREMENT";
        return this;
    }

    public TableBuilder id() {
        return id("id");
    }

    public Column integer(String name) {
        return addColumn(name, "INTEGER");
    }

    public Column longInteger(String name) {
        return addColumn(name, "BIGINT");
    }

    public Column string(String name) {
        return addColumn(name, "VARCHAR(255)");
    }

    public Column text(String name) {
        return addColumn(name, "TEXT");
    }

    public Column bool(String name) {
        return addColumn(name, "BOOLEAN");
    }

    public Column doubleNumber(String name) {
        return addColumn(name, "DOUBLE");
    }

    public Column timestamp(String name) {
        return addColumn(name, "TIMESTAMP");
    }

    public TableBuilder foreignKey(String columnName, String refTable, String refColumn) {
        String col = NamingUtils.toSnakeCase(columnName);
        columns.add(new Column(col, "INTEGER"));
        foreignKeys.add(
            "FOREIGN KEY (%s) REFERENCES %s(%s)".formatted(
                col,
                NamingUtils.toSnakeCase(refTable),
                NamingUtils.toSnakeCase(refColumn)
            )
        );
        return this;
    }

    // --- SQL rendering ---

    String buildColumns() {
        if (columns.isEmpty() && primaryKeySql == null) {
            throw new DatabaseException("Table has no columns defined", null);
        }

        List<String> parts = new ArrayList<>();
        if (primaryKeySql != null) {
            parts.add(primaryKeySql);
        }
        for (Column column : columns) {
            parts.add(column.toSql());
        }
        parts.addAll(foreignKeys);

        return String.join(", ", parts);
    }

    private Column addColumn(String name, String sqlType) {
        Column column = new Column(NamingUtils.toSnakeCase(name.trim()), sqlType);
        columns.add(column);
        return column;
    }
}
