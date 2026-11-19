package com.framework.routing;

import com.framework.annotations.http.Controller;
import com.framework.annotations.http.Delete;
import com.framework.annotations.http.Get;
import com.framework.annotations.http.Options;
import com.framework.annotations.http.Patch;
import com.framework.annotations.http.Post;
import com.framework.annotations.http.Put;
import com.framework.annotations.http.Use;
import com.framework.http.Request;
import com.framework.http.Response;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouteRegistry {

    private final List<Route> routes = new ArrayList<>();
    private final StaticFileHandler staticFileHandler = new StaticFileHandler("public");

    /**
     * Accepts one or more controller classes and registers their annotated routes.
     */
    public void register(Class<?>... controllerClasses) {
        for (Class<?> controllerClass : controllerClasses) {
            registerController(controllerClass);
        }
    }

    /**
     * Scans a single controller class for @Controller, route annotations (@Get, @Post),
     * and filters (@Use).
     */
    public void registerController(Class<?> ControllerClass) {
        if (!ControllerClass.isAnnotationPresent(Controller.class)) {
            return;
        }

        try {
            // 1. Instantiate the controller using its default constructor
            Object controllerInstance = ControllerClass.getDeclaredConstructor().newInstance();

            // 2. Read base path from @Controller(value = "...")
            Controller controllerAnno = ControllerClass.getAnnotation(Controller.class);
            String basePath = controllerAnno.value();

            // 3. Instantiate class-level filters defined via @Use
            List<Filter> classFilters = instantiateFilters(
                ControllerClass.getAnnotation(Use.class)
            );

            // 4. Inspect methods for route annotations
            for (Method method : ControllerClass.getDeclaredMethods()) {
                String httpMethod = null;
                String methodPath = null;

                if (method.isAnnotationPresent(Get.class)) {
                    httpMethod = "GET";
                    methodPath = method.getAnnotation(Get.class).value();
                } else if (method.isAnnotationPresent(Post.class)) {
                    httpMethod = "POST";
                    methodPath = method.getAnnotation(Post.class).value();
                } else if (method.isAnnotationPresent(Put.class)) {
                    httpMethod = "PUT";
                    methodPath = method.getAnnotation(Put.class).value();
                } else if (method.isAnnotationPresent(Delete.class)) {
                    httpMethod = "DELETE";
                    methodPath = method.getAnnotation(Delete.class).value();
                } else if (method.isAnnotationPresent(Patch.class)) {
                    httpMethod = "PATCH";
                    methodPath = method.getAnnotation(Patch.class).value();
                } else if (method.isAnnotationPresent(Options.class)) {
                    httpMethod = "OPTIONS";
                    methodPath = method.getAnnotation(Options.class).value();
                }

                // If method has a valid HTTP route annotation, process it
                if (httpMethod != null && methodPath != null) {
                    String fullPath = normalizePath(basePath, methodPath);

                    // Parse path variable names (:id) and build matching regex Pattern
                    List<String> paramNames = new ArrayList<>();
                    Pattern pathPattern = compilePathToRegex(fullPath, paramNames);

                    // Merge class-level filters with method-level filters
                    List<Filter> methodFilters = instantiateFilters(
                        method.getAnnotation(Use.class)
                    );
                    List<Filter> combinedFilters = new ArrayList<>(classFilters);
                    combinedFilters.addAll(methodFilters);

                    // Build and store internal Route record
                    routes.add(
                        new Route(
                            httpMethod,
                            pathPattern,
                            paramNames,
                            controllerInstance,
                            method,
                            combinedFilters
                        )
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to register controller: " + ControllerClass.getName(),
                e
            );
        }
    }

    /**
     * Matches incoming HTTP requests to a registered route, populates path parameters,
     * and executes the route pipeline.
     */
    public Response dispatch(Request req) {
        for (Route route : routes) {
            if (route.matches(req.getMethod(), req.getPath())) {
                // Extract parameters from path (e.g. /users/42 -> {"id": "42"})
                Map<String, String> pathParams = route.extractPathParams(req.getPath());
                req.setPathParams(pathParams);

                // Create a fresh FilterChain instance for this request and execute
                FilterChain chain = new FilterChain(
                    route.getFilters(),
                    route.getControllerInstance(),
                    route.getTargetMethod()
                );

                return chain.next(req);
            }
        }

        if ("GET".equalsIgnoreCase(req.getMethod())) {
            Response staticRes = staticFileHandler.serve(req.getPath());
            if (staticRes != null) {
                return staticRes;
            }
        }

        return Response.error(404, "Page Not Found");
    }

    // --- Helper Methods ---

    private String normalizePath(String basePath, String methodPath) {
        String combined = (basePath + "/" + methodPath).replaceAll("/+", "/");
        if (combined.length() > 1 && combined.endsWith("/")) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined.isEmpty() ? "/" : combined;
    }

    private Pattern compilePathToRegex(String path, List<String> paramNamesOut) {
        // Find path param tokens starting with ':' e.g. :id or :userId
        Pattern tokenPattern = Pattern.compile(":([a-zA-Z0-9_]+)");
        Matcher matcher = tokenPattern.matcher(path);

        StringBuffer regexBuffer = new StringBuffer("^");
        while (matcher.find()) {
            paramNamesOut.add(matcher.group(1)); // Save parameter name ("id")
            matcher.appendReplacement(regexBuffer, "([^/]+)"); // Replace with capture group regex
        }
        matcher.appendTail(regexBuffer);

        // Convert wildcards * to regex .* and end anchor $
        String finalRegex = regexBuffer.toString().replace("*", ".*") + "$";
        return Pattern.compile(finalRegex);
    }

    private List<Filter> instantiateFilters(Use useAnno) {
        List<Filter> filterInstances = new ArrayList<>();
        if (useAnno == null) {
            return filterInstances;
        }

        for (Class<? extends Filter> filterClass : useAnno.value()) {
            try {
                filterInstances.add(filterClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(
                    "Failed to instantiate filter: " + filterClass.getName(),
                    e
                );
            }
        }
        return filterInstances;
    }
}
