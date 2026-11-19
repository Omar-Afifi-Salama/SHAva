package com.demo.auth;

import com.framework.http.Request;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store: session tokens (held in a browser cookie)
 * map to logged-in user ids. Tokens are lost on server restart.
 */
public class Session {

    public static final String COOKIE_NAME = "session";

    private static final Map<String, Integer> LOGGED_IN_USERS = new ConcurrentHashMap<>();

    public static String start(int userId) {
        String token = UUID.randomUUID().toString();
        LOGGED_IN_USERS.put(token, userId);
        return token;
    }

    public static Integer userId(Request req) {
        String token = req.getCookie(COOKIE_NAME);
        if (token == null) {
            return null;
        }
        return LOGGED_IN_USERS.get(token);
    }

    public static void destroy(String token) {
        LOGGED_IN_USERS.remove(token);
    }

    public static String cookie(String token) {
        return COOKIE_NAME + "=" + token + "; Path=/; HttpOnly";
    }

    public static String expiredCookie() {
        return COOKIE_NAME + "=; Path=/; Max-Age=0";
    }
}
