package com.framework.data;

/**
 * A single column definition. Config methods mutate and return this column,
 * so definitions read top-to-bottom inside the schema lambda.
 */
public class Column {

    private final String name;
    private final String sqlType;
    private boolean notNull;
    private boolean unique;
    private Object defaultValue;

    Column(String name, String sqlType) {
        if (!NamingUtils.isSafeIdentifier(name)) {
            throw new DatabaseException("Invalid column name: " + name, null);
        }
        this.name = name;
        this.sqlType = sqlType;
    }

    public Column notNull() {
        this.notNull = true;
        return this;
    }

    public Column unique() {
        this.unique = true;
        return this;
    }

    public Column defaultValue(Object value) {
        this.defaultValue = value;
        return this;
    }

    String toSql() {
        StringBuilder sql = new StringBuilder(name).append(" ").append(sqlType);
        if (defaultValue != null) {
            sql.append(" DEFAULT ").append(formatValue(defaultValue));
        }
        if (unique) {
            sql.append(" UNIQUE");
        }
        if (notNull) {
            sql.append(" NOT NULL");
        }
        return sql.toString();
    }

    private String formatValue(Object value) {
        if (value instanceof String s) {
            return "'" + s.replace("'", "''") + "'";
        }
        if (value instanceof Boolean b) {
            return b ? "1" : "0";
        }
        return String.valueOf(value);
    }
}
