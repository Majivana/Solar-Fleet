package za.co.solar.fleet.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyMathTest {
    @Test
    void calculatesQuantityTimesUnitCostAndRoundsHalfUpToCents() {
        assertEquals(new BigDecimal("13.34"),
                MoneyMath.costTotal(new BigDecimal("1.333"), new BigDecimal("10.01")));
    }

    @Test
    void roundsHalfUpForFractionalQuantity() {
        assertEquals(new BigDecimal("0.03"),
                MoneyMath.costTotal(new BigDecimal("1.25"), new BigDecimal("0.02")));
    }
}
