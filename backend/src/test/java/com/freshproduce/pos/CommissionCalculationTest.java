package com.freshproduce.pos;

import com.freshproduce.pos.service.CommissionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-04 / FR-10 acceptance criteria for the commission formula. */
class CommissionCalculationTest {

    @Test
    void fivePercentOfR1000IsR50() {
        assertThat(CommissionService.calculate(new BigDecimal("1000.00"), new BigDecimal("5.00")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void sixPercentOfR1000IsR60() {
        assertThat(CommissionService.calculate(new BigDecimal("1000.00"), new BigDecimal("6.00")))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void roundsToCents() {
        assertThat(CommissionService.calculate(new BigDecimal("189.00"), new BigDecimal("5.00")))
                .isEqualByComparingTo("9.45");
    }
}
