package com.finanzas.optimizacion;

import com.finanzas.finanzas.CurrencyCode;
import java.math.BigDecimal;
import java.util.UUID;

public record DeudaEstrategia(
    UUID identidadId,
    String alias,
    CurrencyCode moneda,
    BigDecimal saldoInicial,
    BigDecimal tasaMensualEfectiva,
    BigDecimal cuotaContractual) {}
