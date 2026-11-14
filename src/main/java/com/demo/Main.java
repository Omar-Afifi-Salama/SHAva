package com.demo;

import com.demo.controllers.IndexController;
import com.demo.controllers.NoteController;
import com.framework.http.Server;

public class Main {

    public static void main(String[] args) {
        try {
            Server server = new Server(3000);

            server.registerControllers(IndexController.class);
            server.registerControllers(NoteController.class);

            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
