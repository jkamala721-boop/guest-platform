package com.guest_platform;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** Extracts the opaque test session from the HttpOnly Set-Cookie response without changing public API contracts. */
public final class TestSessionTokens {

    private TestSessionTokens() {
    }

    public static String from(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        if (setCookie == null || !setCookie.startsWith("HOSTVERO_SESSION=")) {
            throw new AssertionError("Expected Hostvero session cookie");
        }
        return setCookie.substring("HOSTVERO_SESSION=".length(), setCookie.indexOf(';'));
    }
}
