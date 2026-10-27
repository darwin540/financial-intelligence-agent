package com.finanzas.finanzas.flujo;

import com.finanzas.finanzas.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodoDia(
    LocalDate fecha,
    CurrencyCode moneda,
    BigDecimal saldoInicial,
    BigDecimal entradas,
    BigDecimal salidas,
    BigDecimal saldoFinal,
    BigDecimal piso,
    BigDecimal reservas,
    BigDecimal libre,
    boolean viable,
    BigDecimal faltante) {}
