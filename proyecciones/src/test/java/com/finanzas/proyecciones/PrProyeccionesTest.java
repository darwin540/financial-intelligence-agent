package com.finanzas.proyecciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.EfectoAbono;
import com.finanzas.finanzas.MotivoBloqueo;
import com.finanzas.finanzas.ObligacionSnapshot;
import com.finanzas.finanzas.TasaBase;
import com.finanzas.finanzas.TasaPeriodo;
import com.finanzas.finanzas.flujo.EventoFlujo;
import com.finanzas.finanzas.flujo.FlujoDiarioEngine;
import com.finanzas.finanzas.flujo.ResultadoFlujo;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrProyeccionesTest {

  @Test
  void pr01_sinTasaBloqueaAmortizacionPeroFlujoUsaCuota() {
    UUID id = UUID.fromString("0b110000-0000-4000-8000-000000000001");
    ObligacionSnapshot obl =
        new ObligacionSnapshot(
            id,
            "Credito de ejemplo",
            CurrencyCode.COP,
            Dinero.monto("4200000.00"),
            Dinero.monto("380000.00"),
            Dinero.monto("380000.00"),
            null,
            null,
            null,
            15,
            EfectoAbono.REDUCE_PLAZO,
            null,
            null,
            null);

    AmortizacionEngine.Resultado r =
        new AmortizacionEngine()
            .proyectar(obl, Dinero.monto("380000.00"), LocalDate.of(2026, 11, 15), null);
    assertFalse(r.definitiva());
    assertNull(r.interesTotal());
    assertNull(r.fechaLiquidacion());
    assertTrue(r.motivosBloqueo().contains(MotivoBloqueo.TASA_AUSENTE));

    ResultadoFlujo flujo =
        new FlujoDiarioEngine()
            .calcular(
                CurrencyCode.COP,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                Dinero.monto("1000000.00"),
                LocalDate.of(2026, 11, 1),
                Dinero.monto("100000.00"),
                Dinero.monto("0.00"),
                false,
                List.of(
                    EventoFlujo.salida(
                        LocalDate.of(2026, 11, 15),
                        CurrencyCode.COP,
                        Dinero.monto("380000.00"),
                        "Cuota",
                        EventoFlujo.TipoEvento.PAGO_OBLIGACION)));
    assertEquals(Dinero.monto("620000.00"), flujo.dia(LocalDate.of(2026, 11, 15)).saldoFinal());
    assertTrue(flujo.motivosBloqueo().contains(MotivoBloqueo.ESENCIALES_NO_REGISTRADOS));
    assertFalse(flujo.definitiva());
  }

  @Test
  void pr02_efectoAbonoDesconocidoBloquea() {
    ObligacionSnapshot obl =
        AmortizacionEngine.obligacionConTasa(
            UUID.randomUUID(),
            "Obl",
            CurrencyCode.COP,
            Dinero.monto("1000000.00"),
            Dinero.monto("100000.00"),
            Dinero.monto("100000.00"),
            Dinero.tasa("0.03000000"),
            TasaPeriodo.MENSUAL,
            TasaBase.EFECTIVA,
            15,
            EfectoAbono.DESCONOCIDO);
    AmortizacionEngine.Resultado r =
        new AmortizacionEngine()
            .proyectar(obl, Dinero.monto("100000.00"), LocalDate.of(2026, 11, 15), null);
    assertFalse(r.definitiva());
    assertNull(r.interesTotal());
    assertTrue(r.motivosBloqueo().contains(MotivoBloqueo.EFECTO_ABONO_DESCONOCIDO));
  }

  @Test
  void pr03_eaAnualConvierteYNominalBloquea() {
    assertEquals(
        Dinero.tasa("0.02000000"),
        TasaMensualConverter.eaAnualAMensual(Dinero.tasa("0.26824179")));

    ObligacionSnapshot ea =
        AmortizacionEngine.obligacionConTasa(
            UUID.randomUUID(),
            "Credito EA sintetico",
            CurrencyCode.COP,
            Dinero.monto("100000.00"),
            Dinero.monto("102000.00"),
            Dinero.monto("102000.00"),
            Dinero.tasa("0.26824179"),
            TasaPeriodo.ANUAL_EFECTIVA,
            TasaBase.EFECTIVA,
            28,
            EfectoAbono.REDUCE_PLAZO);

    AmortizacionEngine.Resultado ok =
        new AmortizacionEngine()
            .proyectar(ea, Dinero.monto("102000.00"), LocalDate.of(2026, 11, 28), null);
    assertTrue(ok.definitiva());
    assertEquals(Dinero.tasa("0.02000000"), ok.tasaMensualEfectivaUsada());
    assertEquals(Dinero.monto("2000.00"), ok.interesTotal());
    assertEquals(LocalDate.of(2026, 11, 28), ok.fechaLiquidacion());

    ObligacionSnapshot nominal =
        AmortizacionEngine.obligacionConTasa(
            UUID.randomUUID(),
            "Nominal",
            CurrencyCode.COP,
            Dinero.monto("100000.00"),
            Dinero.monto("102000.00"),
            Dinero.monto("102000.00"),
            Dinero.tasa("0.26824179"),
            TasaPeriodo.ANUAL_NOMINAL,
            TasaBase.NOMINAL,
            28,
            EfectoAbono.REDUCE_PLAZO);
    AmortizacionEngine.Resultado blocked =
        new AmortizacionEngine()
            .proyectar(nominal, Dinero.monto("102000.00"), LocalDate.of(2026, 11, 28), null);
    assertFalse(blocked.definitiva());
    assertNull(blocked.interesTotal());
    assertTrue(blocked.motivosBloqueo().contains(MotivoBloqueo.TASA_BASE_NO_SOPORTADA));
  }
}
