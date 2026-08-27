package com.guest_platform.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.guest_platform.dto.CountryVerificationOption;
import com.guest_platform.entity.HostIdentityType;

@Service
public class CountryVerificationRegistry {
    private static final Set<String> NATIONAL_ID_COUNTRIES = Set.of("KE");
    private static final Map<String, String> DISPLAY_NAME_OVERRIDES = Map.of(
            "CD", "Democratic Republic of the Congo");

    private final Map<String, CountryVerificationOption> byCode;
    private final List<CountryVerificationOption> countries;

    public CountryVerificationRegistry() {
        countries = Arrays.stream(Locale.getISOCountries())
                .map(code -> new CountryVerificationOption(
                        code,
                        DISPLAY_NAME_OVERRIDES.getOrDefault(code,
                                new Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)),
                        true,
                        NATIONAL_ID_COUNTRIES.contains(code)))
                .sorted(Comparator.comparing(CountryVerificationOption::name))
                .toList();
        byCode = countries.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                CountryVerificationOption::code,
                country -> country));
    }

    public List<CountryVerificationOption> countries() {
        return countries;
    }

    public boolean contains(String countryCode) {
        return byCode.containsKey(countryCode);
    }

    public boolean supports(String countryCode, HostIdentityType identityType) {
        CountryVerificationOption country = byCode.get(countryCode);
        if (country == null || identityType == null) {
            return false;
        }
        return identityType == HostIdentityType.PASSPORT
                ? country.passportSupported()
                : country.nationalIdSupported();
    }
}
