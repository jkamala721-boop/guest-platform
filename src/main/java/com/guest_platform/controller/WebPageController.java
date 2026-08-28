package com.guest_platform.controller;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final Set<String> publicHosts;

    public WebPageController(@Value("${app.admin.public-base-url:http://localhost:8080}") String adminBaseUrl,
            @Value("${app.site.public-hosts:hostvero.net,www.hostvero.net}") String publicHostNames) {
        this.adminHost = URI.create(adminBaseUrl).getHost();
        this.publicHosts = Arrays.stream(publicHostNames.split(",")).map(String::trim).map(String::toLowerCase)
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    @GetMapping("/")
    public String application(HttpServletRequest request) {
        if (adminHost != null && !"localhost".equalsIgnoreCase(adminHost)
                && adminHost.equalsIgnoreCase(request.getServerName())) {
            return "forward:/admin/index.html";
        }
        if (publicHosts.contains(request.getServerName().toLowerCase())) {
            return "forward:/site/index.html";
        }
        return "forward:/index.html";
    }

    @GetMapping({"/for-hosts", "/for-guests", "/pricing", "/safety", "/contact", "/privacy", "/terms",
            "/host-agreement"})
    public String publicPage(HttpServletRequest request) {
        return "forward:/site" + request.getRequestURI() + ".html";
    }

    @GetMapping("/robots.txt")
    public String robots(HttpServletRequest request) {
        return publicHosts.contains(request.getServerName().toLowerCase())
                ? "forward:/site/public-robots.txt" : "forward:/site/private-robots.txt";
    }

    @GetMapping("/sitemap.xml")
    public String sitemap() { return "forward:/site/sitemap.xml"; }

    @GetMapping("/guest/{token}")
    public String guest() { return "forward:/index.html"; }

    @GetMapping({"/admin", "/admin/"})
    public String admin() { return "forward:/admin/index.html"; }
}
