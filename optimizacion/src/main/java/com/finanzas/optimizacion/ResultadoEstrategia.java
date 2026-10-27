package com.finanzas.optimizacion;

import com.finanzas.finanzas.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ResultadoEstrategia(
    EstrategiaNombre nombre,
    CurrencyCode moneda,
    String estado,
    boolean definitiva,
    BigDecimal interesTotal,
    LocalDate fechaLiquidacionPortafolio,
    Map<UUID, LocalDate> liquidacionPorDeuda,
    BigDecimal presupuestoNoUsadoFinal,
    boolean esRecomendada,
    List<String> motivosBloqueo,
    List<LineaPeriodo> lineas) {}
