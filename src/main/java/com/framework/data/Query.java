package com.framework.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Query<T> {

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
        "=",
        "!=",
        "<>",
        "<",
        "<=",
        ">",
        ">=",
        "LIKE",
        "ILIKE"
    );

    private final Database db;
    private final Class<T> type;
    private final EntityMeta meta;

    private String selectColumns = "*";
    private final List<String> whereClauses = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();
    private final List<String> orderClauses = new ArrayList<>();
    private Integer limitValue;
    private Integer offsetValue;

    public Query(Database db, Class<T> type) {
        this.db = db;
        this.type = type;
        this.meta = EntityMeta.of(type);
    }

    // --- Chaining Methods ---

    public Query<T> select(String... columns) {
        if (columns.length > 0) {
            this.selectColumns = String.join(", ", columns);
        }
        return this;
    }

    // Shorthand for equality: .where("active", true)
    public Query<T> where(String column, Object value) {
        return where(column, "=", value);
    }

    // Full comparison: .where("age", ">=", 18)
    public Query<T> where(String column, String operator, Object value) {
        String op = operator.trim().toUpperCase();
        if (!ALLOWED_OPERATORS.contains(op)) {
            throw new DatabaseException("Unsupported SQL operator: " + operator, null);
        }
        whereClauses.add(sanitizeIdentifier(column) + " " + op + " ?");
        params.add(value);
        return this;
    }

    // IN clause: .whereIn("role", List.of("ADMIN", "MOD"))
    public Query<T> whereIn(String column, List<?> values) {
        if (values == null || values.isEmpty()) {
            whereClauses.add("1 = 0"); // Always false if list is empty
            return this;
        }
        String placeholders = String.join(
            ", ",
            values
                .stream()
                .map(v -> "?")
                .toList()
        );
        whereClauses.add(sanitizeIdentifier(column) + " IN (" + placeholders + ")");
        params.addAll(values);
        return this;
    }

    public Query<T> whereNull(String column) {
        whereClauses.add(sanitizeIdentifier(column) + " IS NULL");
        return this;
    }

    public Query<T> whereNotNull(String column) {
        whereClauses.add(sanitizeIdentifier(column) + " IS NOT NULL");
        return this;
    }

    public Query<T> orderBy(String column) {
        return orderBy(column, "ASC");
    }

    public Query<T> orderByDesc(String column) {
        return orderBy(column, "DESC");
    }

    public Query<T> orderBy(String column, String direction) {
        String dir = direction.trim().toUpperCase();
        if (!dir.equals("ASC") && !dir.equals("DESC")) {
            throw new DatabaseException("Invalid sort direction: " + direction, null);
        }
        orderClauses.add(sanitizeIdentifier(column) + " " + dir);
        return this;
    }

    public Query<T> limit(int limit) {
        this.limitValue = limit;
        return this;
    }

    public Query<T> offset(int offset) {
        this.offsetValue = offset;
        return this;
    }

    // --- Terminal Execution Methods ---

    public List<T> get() {
        String sql = compileSelect();
        return db.query(sql, type, params.toArray());
    }

    public Optional<T> first() {
        this.limitValue = 1;
        return db.queryOne(compileSelect(), type, params.toArray());
    }

    public long count() {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(meta.tableName());
        appendWhere(sql);

        return db.queryOne(sql.toString(), rs -> rs.getLong(1), params.toArray()).orElse(0L);
    }

    public boolean exists() {
        return count() > 0;
    }

    public int delete() {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(meta.tableName());
        appendWhere(sql);

        return db.execute(sql.toString(), params.toArray());
    }

    // --- SQL Compilation & Safety Helpers ---

    private String compileSelect() {
        StringBuilder sql = new StringBuilder("SELECT ")
            .append(selectColumns)
            .append(" FROM ")
            .append(meta.tableName());

        appendWhere(sql);

        if (!orderClauses.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderClauses));
        }
        if (limitValue != null) {
            sql.append(" LIMIT ").append(limitValue);
        }
        if (offsetValue != null) {
            sql.append(" OFFSET ").append(offsetValue);
        }
        return sql.toString();
    }

    private void appendWhere(StringBuilder sql) {
        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }
    }

    // Prevents SQL injection via column names while auto-converting camelCase -> snake_case
    private String sanitizeIdentifier(String identifier) {
        String snake = NamingUtils.toSnakeCase(identifier.trim());
        if (!NamingUtils.isSafeIdentifier(snake)) {
            throw new DatabaseException("Invalid column identifier: " + identifier, null);
        }
        return snake;
    }
}
