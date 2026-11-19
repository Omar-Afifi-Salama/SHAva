package com.demo;

import com.demo.controllers.AuthController;
import com.demo.controllers.IndexController;
import com.demo.controllers.NoteController;
import com.demo.models.User;
import com.framework.data.Database;
import com.framework.data.Model;
import com.framework.data.Schema;
import com.framework.http.Server;
import org.sqlite.SQLiteDataSource;

public class Main {

    public static void main(String[] args) {
        try {
            Database db = createDatabase();
            Model.use(db);
            createTables(db);
            seedDemoUser(db);

            Server server = new Server(3000);

            server.registerControllers(IndexController.class);
            server.registerControllers(AuthController.class);
            server.registerControllers(NoteController.class);

            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Database createDatabase() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:shava.db"); // file created next to pom.xml
        return new Database(dataSource);
    }

    private static void createTables(Database db) {
        Schema schema = new Schema(db);

        schema.createTableIfNotExists("users", t -> {
            t.id();
            t.string("username").notNull().unique();
            t.string("password").notNull();
        });

        schema.createTableIfNotExists("notes", t -> {
            t.id();
            t.integer("userId").notNull();
            t.string("title").notNull();
            t.text("body");
            t.timestamp("createdAt");
        });
    }

    private static void seedDemoUser(Database db) {
        boolean shadyExists = Model.query(User.class).where("username", "shady").exists();

        if (!shadyExists) {
            new User("shady", "password123").save();
            System.out.println("Seeded demo user: shady / password123");
        }
    }
}
