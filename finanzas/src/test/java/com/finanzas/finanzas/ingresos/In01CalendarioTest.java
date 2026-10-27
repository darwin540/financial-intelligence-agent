package com.finanzas.finanzas.ingresos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.ingresos.CalendarioIngresosEngine.ClaseIngreso;
import com.finanzas.finanzas.ingresos.CalendarioIngresosEngine.Escenario;
import com.finanzas.finanzas.ingresos.CalendarioIngresosEngine.IngresoDefinicion;
import com.finanzas.finanzas.ingresos.CalendarioIngresosEngine.Recurrencia;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class In01CalendarioTest {

  @Test
  void in01_variableNoSeProyecta_brutoNoEsEntrada() {
    IngresoDefinicion nomina =
        new IngresoDefinicion(
            "Nomina",
            ClaseIngreso.FIJO,
            Recurrencia.MENSUAL_CONFIRMADA,
            CurrencyCode.COP,
            Dinero.monto("3800000.00"),
            Dinero.monto("3200000.00"),
            Dinero.monto("600000.00"),
            30);
    IngresoDefinicion ocasional =
        new IngresoDefinicion(
            "Trabajo ocasional",
            ClaseIngreso.VARIABLE,
            Recurrencia.NO_CONFIRMADA,
            CurrencyCode.COP,
            null,
            Dinero.monto("900000.00"),
            null,
            null);

    var cal =
        new CalendarioIngresosEngine()
            .proyectar(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                List.of(nomina, ocasional));

    for (Escenario e : Escenario.values()) {
      assertEquals(1, cal.escenarios().get(e).size());
      assertEquals(Dinero.monto("3200000.00"), cal.escenarios().get(e).getFirst().montoNeto());
      assertEquals(LocalDate.of(2026, 11, 30), cal.escenarios().get(e).getFirst().fecha());
    }
    assertEquals(1, cal.variablesNoProyectados().size());
    assertEquals(Dinero.monto("0.00"), cal.variablesNoProyectados().getFirst().montoFuturo());
    assertTrue(
        cal.escenarios().values().stream()
            .flatMap(List::stream)
            .noneMatch(o -> o.montoNeto().compareTo(Dinero.monto("3800000.00")) == 0));
    assertTrue(
        cal.escenarios().values().stream()
            .flatMap(List::stream)
            .noneMatch(o -> o.montoNeto().compareTo(Dinero.monto("525000.00")) == 0));
  }
}
