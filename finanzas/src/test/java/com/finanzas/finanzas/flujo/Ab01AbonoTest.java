package com.finanzas.finanzas.flujo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ab01AbonoTest {

  @Test
  void ab01_abonoQueAbreDeficitNoSeRecomienda() {
    LocalDate desde = LocalDate.of(2026, 11, 1);
    LocalDate hasta = LocalDate.of(2026, 11, 30);
    List<EventoFlujo> base =
        List.of(
            EventoFlujo.salida(
                LocalDate.of(2026, 11, 6),
                CurrencyCode.COP,
                Dinero.monto("300000.00"),
                "Esencial1",
                EventoFlujo.TipoEvento.ESENCIAL),
            EventoFlujo.salida(
                LocalDate.of(2026, 11, 10),
                CurrencyCode.COP,
                Dinero.monto("200000.00"),
                "Minimo",
                EventoFlujo.TipoEvento.PAGO_OBLIGACION),
            EventoFlujo.salida(
                LocalDate.of(2026, 11, 18),
                CurrencyCode.COP,
                Dinero.monto("350000.00"),
                "Esencial2",
                EventoFlujo.TipoEvento.ESENCIAL),
            EventoFlujo.entrada(
                LocalDate.of(2026, 11, 30),
                CurrencyCode.COP,
                Dinero.monto("2000000.00"),
                "Ingreso",
                EventoFlujo.TipoEvento.INGRESO));

    FlujoDiarioEngine engine = new FlujoDiarioEngine();
    ResultadoFlujo sinAbono =
        engine.calcular(
            CurrencyCode.COP,
            desde,
            hasta,
            Dinero.monto("950000.00"),
            desde,
            Dinero.monto("50000.00"),
            Dinero.monto("0.00"),
            true,
            base);
    assertTrue(sinAbono.viable());
    assertEquals(Dinero.monto("50000.00"), sinAbono.dia(LocalDate.of(2026, 11, 18)).libre());

    ResultadoFlujo con150 =
        engine.conAbonoExtra(
            CurrencyCode.COP,
            desde,
            hasta,
            Dinero.monto("950000.00"),
            desde,
            Dinero.monto("50000.00"),
            Dinero.monto("0.00"),
            true,
            new ArrayList<>(base),
            LocalDate.of(2026, 11, 10),
            Dinero.monto("150000.00"));
    assertFalse(con150.viable());
    assertEquals(Dinero.monto("-50000.00"), con150.dia(LocalDate.of(2026, 11, 18)).saldoFinal());
    assertEquals(Dinero.monto("-100000.00"), con150.dia(LocalDate.of(2026, 11, 18)).libre());

    ResultadoFlujo con50 =
        engine.conAbonoExtra(
            CurrencyCode.COP,
            desde,
            hasta,
            Dinero.monto("950000.00"),
            desde,
            Dinero.monto("50000.00"),
            Dinero.monto("0.00"),
            true,
            new ArrayList<>(base),
            LocalDate.of(2026, 11, 10),
            Dinero.monto("50000.00"));
    assertTrue(con50.viable());
    assertEquals(Dinero.monto("50000.00"), con50.dia(LocalDate.of(2026, 11, 18)).saldoFinal());
    assertEquals(Dinero.monto("0.00"), con50.dia(LocalDate.of(2026, 11, 18)).libre());

    // disponible after programming minimum on day 10 = 50000
    assertEquals(Dinero.monto("50000.00"), sinAbono.disponibleParaComprometer());
  }
}
