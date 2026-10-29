package com.bankmanagement.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the caller IP. Services sit behind the gateway, which sets X-Forwarded-For. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
