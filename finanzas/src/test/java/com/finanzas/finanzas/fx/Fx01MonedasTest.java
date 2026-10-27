package com.finanzas.finanzas.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.CurrencyMismatchException;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.MotivoBloqueo;
import com.finanzas.finanzas.flujo.EventoFlujo;
import com.finanzas.finanzas.flujo.FlujoDiarioEngine;
import com.finanzas.finanzas.flujo.ResultadoFlujo;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Fx01MonedasTest {

  @Test
  void fx01_copYUsdNoSeMezclanSinConversionPuntual() {
    ResultadoFlujo flujoCop =
        new FlujoDiarioEngine()
            .calcular(
                CurrencyCode.COP,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                Dinero.monto("500000.00"),
                LocalDate.of(2026, 11, 1),
                Dinero.monto("50000.00"),
                Dinero.monto("0.00"),
                true,
                List.of());

    // USD balance does not increase COP available
    assertEquals(Dinero.monto("450000.00"), flujoCop.dia(LocalDate.of(2026, 11, 1)).libre());
    assertTrue(flujoCop.disponibleParaComprometer().compareTo(Dinero.monto("800000.00")) < 0);

    ResultadoFlujo flujoUsd =
        new FlujoDiarioEngine()
            .calcular(
                CurrencyCode.USD,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                Dinero.monto("80.00"),
                LocalDate.of(2026, 11, 1),
                null, // floor not configured
                Dinero.monto("0.00"),
                true,
                List.of(
                    EventoFlujo.salida(
                        LocalDate.of(2026, 11, 8),
                        CurrencyCode.USD,
                        Dinero.monto("25.00"),
                        "Minimo USD",
                        EventoFlujo.TipoEvento.PAGO_OBLIGACION)));

    assertFalse(flujoUsd.definitiva());
    assertTrue(
        flujoUsd.motivosBloqueo().contains(MotivoBloqueo.MINIMO_SEGURIDAD_NO_CONFIGURADO));
    assertEquals(Dinero.monto("55.00"), flujoUsd.dia(LocalDate.of(2026, 11, 8)).saldoFinal());

    CurrencyMismatchException ex =
        assertThrows(
            CurrencyMismatchException.class,
            () ->
                ConversionMoneda.exigirConversionConfirmada(
                    CurrencyCode.COP, CurrencyCode.USD, null));
    assertEquals(MotivoBloqueo.MONEDA_MIXTA_SIN_CONVERSION, ex.codigo());
  }

  @Test
  void fx01_conversionPuntualConTipoDeLaOperacion() {
    ConversionMoneda c =
        ConversionMoneda.deTipoIndicado(
            UUID.randomUUID(),
            LocalDate.of(2026, 11, 8),
            Dinero.monto("100000.00"),
            CurrencyCode.COP,
            CurrencyCode.USD,
            Dinero.tasa("0.00025000"), // 25 USD per 100000 COP for this op only
            UUID.randomUUID(),
            UUID.randomUUID(),
            "cambio puntual de prueba",
            ConversionMoneda.Estado.CONFIRMADO);

    assertEquals(Dinero.monto("25.00"), c.montoDestino());
    assertEquals(Dinero.tasa("0.00025000"), c.tipoCambioOperacion());
    ConversionMoneda.exigirConversionConfirmada(CurrencyCode.COP, CurrencyCode.USD, c);
  }
}
