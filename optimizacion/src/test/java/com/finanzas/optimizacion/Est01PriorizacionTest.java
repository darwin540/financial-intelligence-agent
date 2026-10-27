package com.finanzas.optimizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Oracle EST-01 — four strategies, interest to the cent, zero tolerance.
 */
class Est01PriorizacionTest {

  private static final UUID D1 = UUID.fromString("d1000000-0000-4000-8000-000000000001");
  private static final UUID D2 = UUID.fromString("d1000000-0000-4000-8000-000000000002");
  private static final UUID D3 = UUID.fromString("d1000000-0000-4000-8000-000000000003");

  @Test
  void est01_cuatroEstrategias_interesYFechasAlCentavo() {
    List<DeudaEstrategia> deudas =
        List.of(
            new DeudaEstrategia(
                D1,
                "Consumo largo",
                CurrencyCode.COP,
                Dinero.monto("2000000.00"),
                Dinero.tasa("0.03000000"),
                Dinero.monto("100000.00")),
            new DeudaEstrategia(
                D2,
                "Saldo menor",
                CurrencyCode.COP,
                Dinero.monto("250000.00"),
                Dinero.tasa("0.01000000"),
                Dinero.monto("100000.00")),
            new DeudaEstrategia(
                D3,
                "Cuota alta",
                CurrencyCode.COP,
                Dinero.monto("500000.00"),
                Dinero.tasa("0.02500000"),
                Dinero.monto("200000.00")));

    ComparacionEstrategias cmp =
        new PriorizacionEngine()
            .comparar(
                deudas,
                Dinero.monto("550000.00"),
                LocalDate.of(2026, 11, 28),
                true);

    Map<EstrategiaNombre, ResultadoEstrategia> porNombre =
        cmp.estrategias().stream().collect(Collectors.toMap(ResultadoEstrategia::nombre, r -> r));

    assertInteresYFechas(
        porNombre.get(EstrategiaNombre.AVALANCHA),
        "260294.38",
        "2027-04-28",
        "2027-04-28",
        "2027-01-28",
        "2027-01-28",
        true);
    assertInteresYFechas(
        porNombre.get(EstrategiaNombre.BOLA_DE_NIEVE),
        "265508.95",
        "2027-04-28",
        "2027-04-28",
        "2026-12-28",
        "2026-12-28",
        false);
    assertInteresYFechas(
        porNombre.get(EstrategiaNombre.LIBERACION_FLUJO),
        "262976.55",
        "2027-04-28",
        "2027-04-28",
        "2026-12-28",
        "2026-12-28",
        false);
    assertInteresYFechas(
        porNombre.get(EstrategiaNombre.HIBRIDA),
        "260951.73",
        "2027-04-28",
        "2027-04-28",
        "2027-01-28",
        "2026-12-28",
        false);

    ResultadoEstrategia av = porNombre.get(EstrategiaNombre.AVALANCHA);
    assertEquals(Dinero.monto("289705.62"), av.presupuestoNoUsadoFinal());

    List<LineaPeriodo> m1 =
        av.lineas().stream().filter(l -> l.numeroPeriodo() == 1).toList();
    assertEquals(Dinero.monto("250000.00"), pagoDe(m1, D1));
    assertEquals(Dinero.monto("100000.00"), pagoDe(m1, D2));
    assertEquals(Dinero.monto("200000.00"), pagoDe(m1, D3));

    assertTrue(cmp.justificacion().contains("260294.38"));
    assertTrue(cmp.justificacion().contains("AVALANCHA"));
  }

  private static java.math.BigDecimal pagoDe(List<LineaPeriodo> lineas, UUID id) {
    return lineas.stream()
        .filter(l -> l.obligacionId().equals(id))
        .findFirst()
        .orElseThrow()
        .pago();
  }

  private static void assertInteresYFechas(
      ResultadoEstrategia r,
      String interes,
      String finPortafolio,
      String finD1,
      String finD2,
      String finD3,
      boolean recomendada) {
    assertEquals(Dinero.monto(interes), r.interesTotal(), r.nombre() + " interes");
    assertEquals(LocalDate.parse(finPortafolio), r.fechaLiquidacionPortafolio());
    assertEquals(LocalDate.parse(finD1), r.liquidacionPorDeuda().get(D1), r.nombre() + " D1");
    assertEquals(LocalDate.parse(finD2), r.liquidacionPorDeuda().get(D2), r.nombre() + " D2");
    assertEquals(LocalDate.parse(finD3), r.liquidacionPorDeuda().get(D3), r.nombre() + " D3");
    assertEquals(recomendada, r.esRecomendada(), r.nombre() + " recomendada");
    assertEquals("COMPLETA", r.estado());
  }
}
