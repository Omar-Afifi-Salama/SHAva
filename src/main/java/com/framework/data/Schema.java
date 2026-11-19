package com.framework.data;

import java.util.function.Consumer;

/**
 * Creates and drops database tables from fluent definitions.
 *
 * Usage:
 * <pre>
 * Schema schema = new Schema(db);
 * schema.createTableIfNotExists("notes", t -> {
 *     t.id();
 *     t.string("title").notNull();
 *     t.text("body");
 *     t.bool("archived").defaultValue(false);
 *     t.timestamp("createdAt");
 * });
 * </pre>
 */
public class Schema {

    private final Database db;

    public Schema(Database db) {
        this.db = db;
    }

    public void createTable(String tableName, Consumer<TableBuilder> definition) {
        executeCreate(tableName, definition, false);
    }

    public void createTableIfNotExists(String tableName, Consumer<TableBuilder> definition) {
        executeCreate(tableName, definition, true);
    }

    public void dropTable(String tableName) {
        db.execute("DROP TABLE " + safeName(tableName));
    }

    public void dropTableIfExists(String tableName) {
        db.execute("DROP TABLE IF EXISTS " + safeName(tableName));
    }

    private void executeCreate(
        String tableName,
        Consumer<TableBuilder> definition,
        boolean ifNotExists
    ) {
        TableBuilder table = new TableBuilder();
        definition.accept(table);

        String ifNotExistsSql = ifNotExists ? "IF NOT EXISTS " : "";
        String sql = "CREATE TABLE %s%s (%s)".formatted(
            ifNotExistsSql,
            safeName(tableName),
            table.buildColumns()
        );

        db.execute(sql);
    }

    private String safeName(String tableName) {
        String snake = NamingUtils.toSnakeCase(tableName.trim());
        if (!NamingUtils.isSafeIdentifier(snake)) {
            throw new DatabaseException("Invalid table name: " + tableName, null);
        }
        return snake;
    }
}
