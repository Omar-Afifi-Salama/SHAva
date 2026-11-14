package com.demo.filters;

import com.framework.http.Request;
import com.framework.http.Response;
import com.framework.routing.Filter;
import com.framework.routing.FilterChain;

public class LoggingFilter implements Filter {

    @Override
    public Response doFilter(Request req, FilterChain chain) {
        long start = System.currentTimeMillis();
        System.out.println("--> [" + req.getMethod() + "] " + req.getPath());

        // Pass control down the onion pipeline
        Response res = chain.next(req);

        long duration = System.currentTimeMillis() - start;
        System.out.println("<-- [" + res.getStatusCode() + "] Completed in " + duration + "ms");

        return res;
    }
}
