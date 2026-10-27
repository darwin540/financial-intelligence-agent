package com.finanzas.finanzas.flujo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.ObligacionSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Cu01CupoTest {

  @Test
  void cu01_cupoNoEntraEnIngresosNiDisponible() {
    ObligacionSnapshot tarjeta =
        new ObligacionSnapshot(
            UUID.randomUUID(),
            "Tarjeta",
            CurrencyCode.COP,
            Dinero.monto("1800000.00"),
            Dinero.monto("75000.00"),
            null,
            null,
            null,
            null,
            20,
            null,
            null,
            Dinero.monto("8000000.00"),
            Dinero.monto("6200000.00"));

    List<EventoFlujo> eventos =
        List.of(
            EventoFlujo.salida(
                LocalDate.of(2026, 11, 15),
                CurrencyCode.COP,
                Dinero.monto("100000.00"),
                "Esencial",
                EventoFlujo.TipoEvento.ESENCIAL),
            EventoFlujo.salida(
                LocalDate.of(2026, 11, 20),
                CurrencyCode.COP,
                tarjeta.pagoMinimo(),
                "Minimo tarjeta",
                EventoFlujo.TipoEvento.PAGO_OBLIGACION),
            EventoFlujo.entrada(
                LocalDate.of(2026, 11, 30),
                CurrencyCode.COP,
                Dinero.monto("3200000.00"),
                "Nomina",
                EventoFlujo.TipoEvento.INGRESO));

    ResultadoFlujo r =
        new FlujoDiarioEngine()
            .calcular(
                CurrencyCode.COP,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                Dinero.monto("400000.00"),
                LocalDate.of(2026, 11, 1),
                Dinero.monto("50000.00"),
                Dinero.monto("0.00"),
                true,
                eventos);

    assertEquals(Dinero.monto("400000.00"), r.dia(LocalDate.of(2026, 11, 1)).saldoFinal());
    assertEquals(Dinero.monto("350000.00"), r.dia(LocalDate.of(2026, 11, 1)).libre());
    assertEquals(Dinero.monto("300000.00"), r.dia(LocalDate.of(2026, 11, 15)).saldoFinal());
    assertEquals(Dinero.monto("225000.00"), r.dia(LocalDate.of(2026, 11, 20)).saldoFinal());
    assertEquals(Dinero.monto("3425000.00"), r.dia(LocalDate.of(2026, 11, 30)).saldoFinal());
    assertEquals(Dinero.monto("3375000.00"), r.dia(LocalDate.of(2026, 11, 30)).libre());

    BigDecimal entradasMes =
        r.dias().stream().map(PeriodoDia::entradas).reduce(Dinero.monto("0.00"), BigDecimal::add);
    assertEquals(Dinero.monto("3200000.00"), entradasMes);
    // Wrong closing that would appear if cupo were treated as cash — must not be the saldo
    assertNotEquals(Dinero.monto("9625000.00"), r.dia(LocalDate.of(2026, 11, 30)).saldoFinal());
    assertEquals(
        0,
        r.dias().stream()
            .mapToInt(d -> d.entradas().compareTo(Dinero.monto("6200000.00")) == 0 ? 1 : 0)
            .sum());
  }
}
