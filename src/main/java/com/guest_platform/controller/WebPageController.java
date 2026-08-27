package com.guest_platform.controller;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the static Hostvero interface. Host data remains behind authenticated
 * API endpoints; public guest URLs are token-scoped only after client-side
 * resolution through the existing public API.
 */
@Controller
public class WebPageController {
    private final String adminHost;

    public WebPageController(@Value("${app.admin.public-base-url:http://localhost:8080}") String adminBaseUrl) {
        this.adminHost = URI.create(adminBaseUrl).getHost();
    }

    @GetMapping("/")
    public String application(HttpServletRequest request) {
        if (adminHost != null && !"localhost".equalsIgnoreCase(adminHost)
                && adminHost.equalsIgnoreCase(request.getServerName())) {
            return "forward:/admin/index.html";
        }
        return "forward:/index.html";
    }

    @GetMapping("/guest/{token}")
    public String guest() { return "forward:/index.html"; }

    @GetMapping({"/admin", "/admin/"})
    public String admin() { return "forward:/admin/index.html"; }
}
