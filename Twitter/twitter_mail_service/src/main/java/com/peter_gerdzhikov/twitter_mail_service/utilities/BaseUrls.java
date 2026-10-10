package com.peter_gerdzhikov.twitter_mail_service.utilities;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class BaseUrls {

    private static final String HTTP = "http";

    private static final String HTTPS = "https";

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private BaseUrls() {
    }

    public static String requireAbsoluteHttpUrl(String url, String propertyName) {
        URI uri = parse(url);

        if (uri == null || !isAbsoluteHttpUrl(uri)) {
            throw new IllegalStateException(propertyName
                    + " must be an absolute http or https URL without a query or fragment, such as https://example.com.");
        }

        return url;
    }

    public static boolean isPlainHttpOutsideLocalhost(String url) {
        URI uri = parse(url);

        return uri != null
                && HTTP.equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && !LOCAL_HOSTS.contains(uri.getHost().toLowerCase());
    }

    private static URI parse(String url) {
        try {
            return new URI(url);

        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isAbsoluteHttpUrl(URI uri) {
        boolean isHttpOrHttps = HTTP.equalsIgnoreCase(uri.getScheme()) || HTTPS.equalsIgnoreCase(uri.getScheme());

        return isHttpOrHttps
                && uri.getHost() != null
                && uri.getQuery() == null
                && uri.getFragment() == null;
    }
}
