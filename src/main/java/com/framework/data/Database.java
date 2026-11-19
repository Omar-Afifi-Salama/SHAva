package com.framework.data;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class Database {

    private final DataSource dataSource;

    public Database(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // --- Entity operations (SQL is generated from the entity's metadata) ---

    /**
     * Inserts the entity if its @Id is null/0, otherwise updates the row.
     * With auto-increment IDs, the generated key is written back into the entity.
     */
    public <T> T save(T entity) {
        EntityMeta meta = EntityMeta.of(entity.getClass());
        Object idVal = meta.getIdValue(entity);
        boolean isNew = idVal == null || (idVal instanceof Number n && n.longValue() == 0L);

        if (isNew) {
            insert(entity, meta);
        } else {
            update(entity, meta, idVal);
        }
        return entity;
    }

    public boolean delete(Object entity) {
        EntityMeta meta = EntityMeta.of(entity.getClass());
        Object idVal = meta.getIdValue(entity);
        if (idVal == null) {
            throw new DatabaseException("Cannot delete entity with null @Id", null);
        }

        String sql = "DELETE FROM %s WHERE %s = ?".formatted(
            meta.tableName(),
            meta.idField().columnName()
        );
        return execute(sql, idVal) > 0;
    }

    public <T> Optional<T> findById(Class<T> type, Object id) {
        EntityMeta meta = EntityMeta.of(type);
        String sql = "SELECT * FROM %s WHERE %s = ?".formatted(
            meta.tableName(),
            meta.idField().columnName()
        );
        return queryOne(sql, type, id);
    }

    public <T> Query<T> from(Class<T> type) {
        return new Query<>(this, type);
    }

    // --- Raw SQL operations (callers supply the statements) ---

    public int execute(String sql, Object... params) {
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            bindParams(stmt, params);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to execute: " + sql, e);
        }
    }

    public long insertAndReturnId(String sql, Object... params) {
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            bindParams(stmt, params);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new DatabaseException("No generated key returned for: " + sql, null);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to insert: " + sql, e);
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            bindParams(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query: " + sql, e);
        }
    }

    public <T> List<T> query(String sql, Class<T> type, Object... params) {
        return query(sql, AutoMapper.of(type), params);
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(sql, mapper, params);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public <T> Optional<T> queryOne(String sql, Class<T> type, Object... params) {
        return queryOne(sql, AutoMapper.of(type), params);
    }

    // --- Private helpers ---

    private void insert(Object entity, EntityMeta meta) {
        List<EntityMeta.ColumnField> fields = meta.regularFields();
        StringBuilder cols = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < fields.size(); i++) {
            EntityMeta.ColumnField col = fields.get(i);
            if (i > 0) {
                cols.append(", ");
                placeholders.append(", ");
            }
            cols.append(col.columnName());
            placeholders.append("?");
            params.add(meta.getFieldValue(col, entity));
        }

        String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(
            meta.tableName(),
            cols,
            placeholders
        );

        if (meta.isAutoIncrement()) {
            long generatedId = insertAndReturnId(sql, params.toArray());
            Class<?> idType = meta.idField().field().getType();
            if (idType == int.class || idType == Integer.class) {
                meta.setIdValue(entity, (int) generatedId);
            } else {
                meta.setIdValue(entity, generatedId);
            }
        } else {
            execute(sql, params.toArray());
        }
    }

    private void update(Object entity, EntityMeta meta, Object idVal) {
        List<EntityMeta.ColumnField> fields = meta.regularFields();
        StringBuilder setClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < fields.size(); i++) {
            EntityMeta.ColumnField col = fields.get(i);
            if (i > 0) setClause.append(", ");
            setClause.append(col.columnName()).append(" = ?");
            params.add(meta.getFieldValue(col, entity));
        }

        params.add(idVal); // Bind WHERE id = ?

        String sql = "UPDATE %s SET %s WHERE %s = ?".formatted(
            meta.tableName(),
            setClause,
            meta.idField().columnName()
        );

        execute(sql, params.toArray());
    }

    private void bindParams(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }
}
