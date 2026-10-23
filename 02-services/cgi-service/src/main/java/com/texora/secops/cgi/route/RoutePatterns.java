package com.texora.secops.cgi.route;

import java.util.regex.Pattern;

/**
 * The ONE definition of a legal route. Routes are EXACT paths (no wildcards, no regex, no templates): the
 * allowlist is strict by construction. The same check validates a submitted pattern and an incoming request path,
 * so anything odd (traversal, encoding tricks, double slashes, trailing slash, path parameters) can never match.
 */
public final class RoutePatterns {

    private static final Pattern LEGAL = Pattern.compile("^/[A-Za-z0-9._~-]+(?:/[A-Za-z0-9._~-]+)*$");
    private static final int MAX_LENGTH = 255;

    private RoutePatterns() {
    }

    public static boolean isValid(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_LENGTH || !LEGAL.matcher(path).matches()) {
            return false;
        }
        for (String segment : path.substring(1).split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
