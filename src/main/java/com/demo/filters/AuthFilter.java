package com.demo.filters;

import com.framework.http.Request;
import com.framework.http.Response;
import com.framework.routing.Filter;
import com.framework.routing.FilterChain;

public class AuthFilter implements Filter {

    @Override
    public Response doFilter(Request req, FilterChain chain) {
        String secret = req.getQueryParams().get("secret");

        if ("admin".equals(secret)) {
            return chain.next(req);
        }

        return Response.error(
            401,
            "Unauthorized: Append '?secret=admin' to access protected routes."
        );
    }
}
