package com.framework.data;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoMapper<T> implements RowMapper<T> {

    private static final Map<Class<?>, AutoMapper<?>> CACHE = new ConcurrentHashMap<>();

    private record FieldMeta(Field field, String columnName) {}

    private final Class<T> type;
    private final Constructor<T> constructor;
    private final FieldMeta[] fields;

    @SuppressWarnings("unchecked")
    public static <T> AutoMapper<T> of(Class<T> type) {
        return (AutoMapper<T>) CACHE.computeIfAbsent(type, AutoMapper::new);
    }

    private AutoMapper(Class<T> type) {
        this.type = type;

        try {
            this.constructor = type.getDeclaredConstructor();
            this.constructor.setAccessible(true);
            this.fields = Arrays.stream(type.getDeclaredFields())
                .filter(
                    f ->
                        !Modifier.isStatic(f.getModifiers()) &&
                        !Modifier.isTransient(f.getModifiers())
                )
                .peek(f -> f.setAccessible(true))
                .map(f -> new FieldMeta(f, NamingUtils.toSnakeCase(f.getName())))
                .toArray(FieldMeta[]::new);
        } catch (NoSuchMethodException e) {
            throw new DatabaseException(type.getName() + " must have a no-arg constructor", e);
        }
    }

    @Override
    public T map(ResultSet rs) throws SQLException {
        Map<String, Integer> availableColumns = getAvailableColumns(rs);

        try {
            T instance = constructor.newInstance();
            for (FieldMeta meta : fields) {
                Integer colIndex = availableColumns.get(meta.columnName());

                if (colIndex != null) {
                    Object val = readColumn(rs, colIndex, meta.field().getType());
                    if (val != null || !meta.field().getType().isPrimitive()) {
                        meta.field().set(instance, val);
                    }
                }
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new DatabaseException("Failed to hydrate " + type.getName(), e);
        }
    }

    private Map<String, Integer> getAvailableColumns(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        Map<String, Integer> map = new HashMap<>(count);
        for (int i = 1; i <= count; i++) {
            map.put(meta.getColumnLabel(i).toLowerCase(), i);
        }
        return map;
    }

    private Object readColumn(ResultSet rs, int index, Class<?> targetType) throws SQLException {
        if (targetType == boolean.class || targetType == Boolean.class) {
            Object raw = rs.getObject(index);
            if (raw == null) return null;
            if (raw instanceof Number num) return num.intValue() != 0;
            return rs.getBoolean(index);
        }
        return rs.getObject(index, boxType(targetType));
    }

    private Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
