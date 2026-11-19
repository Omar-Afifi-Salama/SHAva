package com.framework.data;

import com.framework.annotations.data.Id;
import com.framework.annotations.data.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached reflection metadata for one entity class: table name, id field,
 * and all regular columns. Built once per class, then reused for every query.
 */
public class EntityMeta {

    private static final Map<Class<?>, EntityMeta> CACHE = new ConcurrentHashMap<>();

    public record ColumnField(Field field, String columnName) {}

    private final String tableName;
    private final ColumnField idField;
    private final boolean autoIncrement;
    private final List<ColumnField> regularFields;

    public static EntityMeta of(Class<?> type) {
        return CACHE.computeIfAbsent(type, EntityMeta::new);
    }

    private EntityMeta(Class<?> type) {
        // 1. Resolve table name from @Table or fallback to snake_case class name
        Table tableAnn = type.getAnnotation(Table.class);
        if (tableAnn != null && !tableAnn.value().isBlank()) {
            this.tableName = tableAnn.value();
        } else {
            this.tableName = NamingUtils.toSnakeCase(type.getSimpleName());
        }

        // 2. Scan fields for @Id and regular columns
        ColumnField foundId = null;
        boolean isAutoInc = true;
        List<ColumnField> cols = new ArrayList<>();

        for (Field field : type.getDeclaredFields()) {
            if (
                Modifier.isStatic(field.getModifiers()) ||
                Modifier.isTransient(field.getModifiers())
            ) {
                continue;
            }
            field.setAccessible(true);
            String colName = NamingUtils.toSnakeCase(field.getName());

            Id idAnn = field.getAnnotation(Id.class);
            if (idAnn != null) {
                foundId = new ColumnField(field, colName);
                isAutoInc = idAnn.autoIncrement();
            } else {
                cols.add(new ColumnField(field, colName));
            }
        }

        if (foundId == null) {
            throw new DatabaseException(
                type.getName() + " must have a field annotated with @Id",
                null
            );
        }

        this.idField = foundId;
        this.autoIncrement = isAutoInc;
        this.regularFields = List.copyOf(cols);
    }

    // --- Field value accessors ---

    public Object getIdValue(Object entity) {
        try {
            return idField.field().get(entity);
        } catch (IllegalAccessException e) {
            throw new DatabaseException("Failed to read @Id field", e);
        }
    }

    public void setIdValue(Object entity, Object value) {
        try {
            idField.field().set(entity, value);
        } catch (IllegalAccessException e) {
            throw new DatabaseException("Failed to write @Id field", e);
        }
    }

    public Object getFieldValue(ColumnField col, Object entity) {
        try {
            return col.field().get(entity);
        } catch (IllegalAccessException e) {
            throw new DatabaseException("Failed to read field: " + col.columnName(), e);
        }
    }

    // --- Metadata getters ---

    public String tableName() {
        return tableName;
    }

    public ColumnField idField() {
        return idField;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public List<ColumnField> regularFields() {
        return regularFields;
    }
}
