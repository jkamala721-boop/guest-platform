package com.guest_platform.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the static Hostvero interface. Host data remains behind authenticated
 * API endpoints; public guest URLs are token-scoped only after client-side
 * resolution through the existing public API.
 */
@Controller
public class WebPageController {

    @GetMapping({ "/", "/guest/{token}" })
    public String application() {
        return "forward:/index.html";
    }
}
