package com.framework.routing;

import com.framework.http.Request;
import com.framework.http.Response;

public interface Filter {
    Response doFilter(Request req, FilterChain chain);
}
