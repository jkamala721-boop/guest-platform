package com.guest_platform.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PaystackPaymentProviderTest {

    @Test
    void convertsKesAmountsToLowestCurrencyUnitWithoutRounding() {
        assertThat(PaystackPaymentProvider.toMinorUnits(new BigDecimal("3675.00"))).isEqualTo(367500L);
    }
}
