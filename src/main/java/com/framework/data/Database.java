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

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(sql, mapper, params);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private void bindParams(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }
}
