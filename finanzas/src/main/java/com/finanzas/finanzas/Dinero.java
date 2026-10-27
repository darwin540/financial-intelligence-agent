package com.finanzas.finanzas;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Shared BigDecimal helpers for amounts and rates. Binary floating types are forbidden for money/rates.
 */
public final class Dinero {

  public static final MathContext MC34 = new MathContext(34, RoundingMode.HALF_UP);
  public static final int RATE_SCALE = 8;

  private Dinero() {}

  public static BigDecimal monto(String value) {
    return new BigDecimal(value).setScale(Money.SCALE, Money.ROUNDING);
  }

  public static BigDecimal tasa(String value) {
    return new BigDecimal(value).setScale(RATE_SCALE, Money.ROUNDING);
  }

  public static BigDecimal cuantizarMonto(BigDecimal value) {
    return value.setScale(Money.SCALE, Money.ROUNDING);
  }

  public static BigDecimal cuantizarTasa(BigDecimal value) {
    return value.setScale(RATE_SCALE, Money.ROUNDING);
  }

  /**
   * Period interest = quantize(saldoInicial * tasaMensualEfectiva, scale 2, HALF_UP).
   * Intermediate multiplication uses at least 34 digit precision.
   */
  public static BigDecimal interesPeriodo(BigDecimal saldoInicial, BigDecimal tasaMensualEfectiva) {
    BigDecimal producto = saldoInicial.multiply(tasaMensualEfectiva, MC34);
    return cuantizarMonto(producto);
  }
}
