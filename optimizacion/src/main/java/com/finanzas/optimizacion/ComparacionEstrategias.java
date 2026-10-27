package com.finanzas.optimizacion;

import com.finanzas.finanzas.CurrencyCode;
import java.util.List;

public record ComparacionEstrategias(
    String supuestosHash,
    String algoritmo,
    String amortizacion,
    CurrencyCode moneda,
    boolean esEstimacion,
    List<ResultadoEstrategia> estrategias,
    String justificacion) {}
