package com.framework.routing;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Route {

    private final String httpMethod;
    private final Pattern pathPattern;
    private final List<String> paramNames;
    private final Object controllerInstance;
    private final Method targetMethod;
    private final List<Filter> filters;

    public Route(
        String httpMethod,
        Pattern pathPattern,
        List<String> paramNames,
        Object controllerInstance,
        Method targetMethod,
        List<Filter> filters
    ) {
        this.httpMethod = httpMethod != null ? httpMethod.toUpperCase() : "GET";
        this.pathPattern = pathPattern;
        this.paramNames = paramNames != null ? paramNames : Collections.emptyList();
        this.controllerInstance = controllerInstance;
        this.targetMethod = targetMethod;
        this.filters = filters != null ? filters : Collections.emptyList();
    }

    /**
     * Checks if the incoming request method and URL path match this route.
     */
    public boolean matches(String method, String requestPath) {
        return (
            this.httpMethod.equalsIgnoreCase(method) &&
            this.pathPattern.matcher(requestPath).matches()
        );
    }

    /**
     * Extracts dynamic path parameters from the URL matching capture groups.
     * e.g., for pattern "/users/([^/]+)" and path "/users/42", extracts {"id": "42"}.
     */
    public Map<String, String> extractPathParams(String requestPath) {
        Map<String, String> params = new HashMap<>();
        Matcher matcher = this.pathPattern.matcher(requestPath);

        if (matcher.matches()) {
            for (int i = 0; i < paramNames.size(); i++) {
                // Matcher group 0 is the full URL; captured sub-groups start at index 1
                params.put(paramNames.get(i), matcher.group(i + 1));
            }
        }
        return params;
    }

    // Getters
    public String getHttpMethod() {
        return httpMethod;
    }

    public Pattern getPathPattern() {
        return pathPattern;
    }

    public List<String> getParamNames() {
        return paramNames;
    }

    public Object getControllerInstance() {
        return controllerInstance;
    }

    public Method getTargetMethod() {
        return targetMethod;
    }

    public List<Filter> getFilters() {
        return filters;
    }
}
