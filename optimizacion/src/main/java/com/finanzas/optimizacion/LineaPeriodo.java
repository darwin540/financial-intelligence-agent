package com.finanzas.optimizacion;

import com.finanzas.finanzas.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LineaPeriodo(
    UUID obligacionId,
    int numeroPeriodo,
    LocalDate fechaPago,
    CurrencyCode moneda,
    BigDecimal saldoInicial,
    BigDecimal interes,
    BigDecimal pago,
    BigDecimal saldoFinal,
    BigDecimal cuotaContractualVigente,
    boolean liquidadaEnElPeriodo,
    BigDecimal presupuestoNoUsado) {}
