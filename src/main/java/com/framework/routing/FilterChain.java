package com.framework.routing;

import com.framework.http.Request;
import com.framework.http.Response;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public class FilterChain {

    private final List<Filter> filters;
    private final Object controllerInstance;
    private final Method targetMethod;
    private int index = 0;

    public FilterChain(List<Filter> filters, Object controllerInstance, Method targetMethod) {
        this.filters = filters != null ? filters : Collections.emptyList();
        this.controllerInstance = controllerInstance;
        this.targetMethod = targetMethod;
    }

    public Response next(Request req) {
        // Execute filters sequentially if any remain
        if (index < filters.size()) {
            Filter currentFilter = filters.get(index++);
            return currentFilter.doFilter(req, this);
        }

        // Once filters are exhausted, invoke the target controller method
        try {
            Object result = targetMethod.invoke(controllerInstance, req);

            if (result instanceof Response res) {
                return res;
            } else if (result instanceof String html) {
                return Response.html(html);
            }

            return Response.error(500, "Unsupported Controller Return Type");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return Response.error(500, "Controller Error: " + cause.getMessage());
        }
    }
}
