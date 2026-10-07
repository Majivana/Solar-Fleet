package za.co.solar.fleet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {
    private MoneyMath() {}

    public static BigDecimal costTotal(BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal persistedUnitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
        return quantity.multiply(persistedUnitCost).setScale(2, RoundingMode.HALF_UP);
    }
}
