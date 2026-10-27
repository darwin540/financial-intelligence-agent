package com.finanzas.finanzas;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable snapshot of an obligation used by cash-flow, strategies and amortization.
 */
public record ObligacionSnapshot(
    UUID identidadId,
    String alias,
    CurrencyCode moneda,
    BigDecimal saldoTotal,
    BigDecimal pagoMinimo,
    BigDecimal cuotaContractual,
    BigDecimal tasaValor,
    TasaPeriodo tasaPeriodo,
    TasaBase tasaBase,
    Integer diaVencimiento,
    EfectoAbono efectoAbono,
    String reglaRecalculoCuota,
    BigDecimal cupoTotal,
    BigDecimal cupoDisponible) {

  public BigDecimal salidaParaEvitarMora() {
    return pagoMinimo;
  }

  public BigDecimal salidaPactada() {
    return cuotaContractual;
  }

  /**
   * Payment demand for the period: contractual installment if present, else minimum.
   */
  public BigDecimal exigencia() {
    if (cuotaContractual != null) {
      return cuotaContractual;
    }
    return pagoMinimo;
  }

  public Optional<BigDecimal> cupoDisponibleOpt() {
    return Optional.ofNullable(cupoDisponible);
  }
}
