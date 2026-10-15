package com.peter_gerdzhikov.signal_flow_api_gateway.utilities;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Finds the caller's address behind our own reverse proxies. Each proxy appends the peer it saw to
 * {@code X-Forwarded-For}, so only the entries our proxies added can be trusted: the entry
 * {@code indexFromRight} places from the right is the client as seen by the outermost proxy, and anything
 * further left is whatever the client chose to send. A missing or too short header means the request did
 * not come through the proxies, so the socket peer is used.
 */
public final class ClientAddressResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private ClientAddressResolver() {
    }

    public static String resolve(HttpServletRequest request, int indexFromRight) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] addresses = forwardedFor.split(",");
        int index = addresses.length - 1 - indexFromRight;
        if (index < 0) {
            return request.getRemoteAddr();
        }

        return addresses[index].strip();
    }
}
