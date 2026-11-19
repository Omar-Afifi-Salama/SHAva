package com.demo.filters;

import com.demo.auth.Session;
import com.framework.http.Request;
import com.framework.http.Response;
import com.framework.routing.Filter;
import com.framework.routing.FilterChain;

/**
 * Requires a logged-in user; redirects visitors to the login page.
 */
public class AuthFilter implements Filter {

    @Override
    public Response doFilter(Request req, FilterChain chain) {
        if (Session.userId(req) != null) {
            return chain.next(req);
        }

        return Response.redirect("/login");
    }
}
