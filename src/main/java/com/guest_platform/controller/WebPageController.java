package com.guest_platform.controller;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

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
    public ModelAndView application(HttpServletRequest request) {
        if (adminHost != null && !"localhost".equalsIgnoreCase(adminHost)
                && adminHost.equalsIgnoreCase(request.getServerName())) {
            return forward("/admin/index.html");
        }
        ModelAndView redirect = canonicalRedirect(request);
        if (redirect != null) return redirect;
        if (publicHosts.contains(request.getServerName().toLowerCase())) {
            return forward("/site/index.html");
        }
        return forward("/index.html");
    }

    @GetMapping({"/for-hosts", "/for-guests", "/pricing", "/safety", "/contact", "/privacy", "/terms",
            "/host-agreement"})
    public ModelAndView publicPage(HttpServletRequest request) {
        ModelAndView redirect = canonicalRedirect(request);
        return redirect != null ? redirect : forward("/site" + request.getRequestURI() + ".html");
    }

    @GetMapping("/robots.txt")
    public ModelAndView robots(HttpServletRequest request) {
        ModelAndView redirect = canonicalRedirect(request);
        if (redirect != null) return redirect;
        return forward(publicHosts.contains(request.getServerName().toLowerCase())
                ? "/site/public-robots.txt" : "/site/private-robots.txt");
    }

    @GetMapping("/sitemap.xml")
    public ModelAndView sitemap(HttpServletRequest request) {
        ModelAndView redirect = canonicalRedirect(request);
        return redirect != null ? redirect : forward("/site/sitemap.xml");
    }

    @GetMapping("/guest/{token}")
    public String guest() { return "forward:/index.html"; }

    @GetMapping({"/admin", "/admin/"})
    public String admin() { return "forward:/admin/index.html"; }

    private ModelAndView canonicalRedirect(HttpServletRequest request) {
        if (!"www.hostvero.net".equalsIgnoreCase(request.getServerName())) return null;
        String target = "https://hostvero.net" + request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            target += "?" + request.getQueryString();
        }
        RedirectView view = new RedirectView(target);
        view.setStatusCode(HttpStatus.PERMANENT_REDIRECT);
        view.setExposeModelAttributes(false);
        return new ModelAndView(view);
    }

    private static ModelAndView forward(String path) {
        return new ModelAndView("forward:" + path);
    }
}
