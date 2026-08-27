package com.guest_platform.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.CountryVerificationOption;
import com.guest_platform.service.CountryVerificationRegistry;

@RestController
@RequestMapping("/api/reference")
public class ReferenceDataController {
    private final CountryVerificationRegistry countries;

    public ReferenceDataController(CountryVerificationRegistry countries) {
        this.countries = countries;
    }

    @GetMapping("/countries")
    public List<CountryVerificationOption> countries() {
        return countries.countries();
    }
}
