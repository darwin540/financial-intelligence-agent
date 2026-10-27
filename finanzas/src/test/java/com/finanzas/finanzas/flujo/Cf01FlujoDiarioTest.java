package com.finanzas.finanzas.flujo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class Cf01FlujoDiarioTest {

  @Test
  void cf01_mesPositivoConFechaIntermediaInviable() {
    LocalDate desde = LocalDate.of(2026, 11, 1);
    LocalDate hasta = LocalDate.of(2026, 11, 30);
    List<EventoFlujo> eventos =
        List.of(
            EventoFlujo.salida(
                LocalDate.of(2026, 11, 5),
                CurrencyCode.COP,
                Dinero.monto("800000.00"),
                "Arriendo",
                EventoFlujo.TipoEvento.ESENCIAL),
            EventoFlujo.salida(
                LocalDate.of(2026, 11, 12),
                CurrencyCode.COP,
                Dinero.monto("300000.00"),
                "Pago minimo",
                EventoFlujo.TipoEvento.PAGO_OBLIGACION),
            EventoFlujo.entrada(
                LocalDate.of(2026, 11, 28),
                CurrencyCode.COP,
                Dinero.monto("2500000.00"),
                "Nomina",
                EventoFlujo.TipoEvento.INGRESO));

    ResultadoFlujo r =
        new FlujoDiarioEngine()
            .calcular(
                CurrencyCode.COP,
                desde,
                hasta,
                Dinero.monto("1000000.00"),
                desde,
                Dinero.monto("250000.00"),
                Dinero.monto("0.00"),
                true,
                eventos);

    assertFalse(r.viable());
    assertEquals(Dinero.monto("1400000.00"), r.variacionDelMes());
    assertEquals(Dinero.monto("0.00"), r.disponibleParaComprometer());

    assertDia(r, "2026-11-01", "1000000.00", "750000.00", true, "0.00");
    assertDia(r, "2026-11-05", "200000.00", "-50000.00", false, "50000.00");
    assertDia(r, "2026-11-12", "-100000.00", "-350000.00", false, "350000.00");
    assertDia(r, "2026-11-28", "2400000.00", "2150000.00", true, "0.00");
  }

  private static void assertDia(
      ResultadoFlujo r,
      String fecha,
      String saldo,
      String libre,
      boolean viable,
      String faltante) {
    PeriodoDia d = r.dia(LocalDate.parse(fecha));
    assertEquals(Dinero.monto(saldo), d.saldoFinal(), fecha + " saldo");
    assertEquals(Dinero.monto(libre), d.libre(), fecha + " libre");
    assertEquals(viable, d.viable(), fecha + " viable");
    assertEquals(Dinero.monto(faltante), d.faltante(), fecha + " faltante");
  }
}
