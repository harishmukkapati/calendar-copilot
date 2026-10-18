package dev.calendarcopilot.common;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {
    }

    /**
     * The visitor's IP. Behind CloudFront the socket address is CloudFront's, so use
     * CloudFront-Viewer-Address ("ip:port"), else the LAST X-Forwarded-For entry (the one
     * CloudFront appended; earlier entries can be forged by the client).
     */
    public static String of(HttpServletRequest request) {
        String viewer = request.getHeader("CloudFront-Viewer-Address");
        if (viewer != null && viewer.lastIndexOf(':') > 0) {
            return viewer.substring(0, viewer.lastIndexOf(':'));
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].strip();
        }
        return request.getRemoteAddr();
    }
}
