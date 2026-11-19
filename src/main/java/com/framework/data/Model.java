package com.framework.data;

import java.util.Optional;

public abstract class Model<T extends Model<T>> {

    private static Database defaultDb;

    public static void use(Database db) {
        defaultDb = db;
    }

    protected static Database db() {
        if (defaultDb == null) {
            throw new DatabaseException(
                "Database not initialized. Call Model.use(db) at startup.",
                null
            );
        }
        return defaultDb;
    }

    public static <M> Query<M> query(Class<M> type) {
        return db().from(type);
    }

    public static <M> Optional<M> find(Class<M> type, Object id) {
        return db().findById(type, id);
    }

    @SuppressWarnings("unchecked")
    public T save() {
        return db().save((T) this);
    }

    public boolean delete() {
        return db().delete(this);
    }
}
