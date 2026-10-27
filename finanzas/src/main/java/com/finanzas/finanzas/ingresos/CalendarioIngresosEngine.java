package com.finanzas.finanzas.ingresos;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Income calendar per architecture §5.3: fixed monthly confirmed uses net in all scenarios;
 * variable / unconfirmed projects 0.00 forward.
 */
public final class CalendarioIngresosEngine {

  public enum Escenario {
    CONSERVADOR,
    BASE,
    OPTIMISTA
  }

  public enum ClaseIngreso {
    FIJO,
    VARIABLE
  }

  public enum Recurrencia {
    UNICA,
    MENSUAL_CONFIRMADA,
    NO_CONFIRMADA
  }

  public record IngresoDefinicion(
      String concepto,
      ClaseIngreso clase,
      Recurrencia recurrencia,
      CurrencyCode moneda,
      BigDecimal bruto,
      BigDecimal neto,
      BigDecimal descuentos,
      Integer diaEsperado) {}

  public record Ocurrencia(
      String concepto,
      LocalDate fecha,
      CurrencyCode moneda,
      BigDecimal montoNeto,
      String naturaleza) {}

  public record VariableNoProyectado(
      String concepto, CurrencyCode moneda, BigDecimal montoFuturo, String motivo) {}

  public record CalendarioResultado(
      LocalDate desde,
      LocalDate hasta,
      Map<Escenario, List<Ocurrencia>> escenarios,
      List<VariableNoProyectado> variablesNoProyectados) {}

  public CalendarioResultado proyectar(
      LocalDate desde, LocalDate hasta, List<IngresoDefinicion> ingresos) {
    Map<Escenario, List<Ocurrencia>> escenarios = new EnumMap<>(Escenario.class);
    for (Escenario e : Escenario.values()) {
      escenarios.put(e, new ArrayList<>());
    }
    List<VariableNoProyectado> variables = new ArrayList<>();

    for (IngresoDefinicion ing : ingresos) {
      if (ing.clase() == ClaseIngreso.FIJO
          && ing.recurrencia() == Recurrencia.MENSUAL_CONFIRMADA
          && ing.neto() != null
          && ing.diaEsperado() != null) {
        for (YearMonth ym = YearMonth.from(desde);
            !ym.isAfter(YearMonth.from(hasta));
            ym = ym.plusMonths(1)) {
          LocalDate fecha = fechaDelDia(ym, ing.diaEsperado());
          if (!fecha.isBefore(desde) && !fecha.isAfter(hasta)) {
            Ocurrencia o =
                new Ocurrencia(
                    ing.concepto(),
                    fecha,
                    ing.moneda(),
                    Dinero.cuantizarMonto(ing.neto()),
                    "DATO_USUARIO");
            for (Escenario e : Escenario.values()) {
              escenarios.get(e).add(o);
            }
          }
        }
      } else if (ing.clase() == ClaseIngreso.VARIABLE
          || ing.recurrencia() == Recurrencia.NO_CONFIRMADA) {
        variables.add(
            new VariableNoProyectado(
                ing.concepto(),
                ing.moneda(),
                Dinero.monto("0.00"),
                "RECURRENCIA_NO_CONFIRMADA"));
      }
    }
    return new CalendarioResultado(desde, hasta, Map.copyOf(escenarios), List.copyOf(variables));
  }

  public static LocalDate fechaDelDia(YearMonth ym, int diaEsperado) {
    int dia = Math.min(diaEsperado, ym.lengthOfMonth());
    return ym.atDay(dia);
  }

  public static void validarCoherenciaMontos(
      BigDecimal bruto, BigDecimal neto, BigDecimal descuentos) {
    if (bruto != null && neto != null && descuentos != null) {
      BigDecimal suma = Dinero.cuantizarMonto(neto.add(descuentos));
      if (Dinero.cuantizarMonto(bruto).compareTo(suma) != 0) {
        throw new IngresoIncoherenteException();
      }
    }
  }

  public static final class IngresoIncoherenteException extends RuntimeException {
    public IngresoIncoherenteException() {
      super("MONTOS_INGRESO_INCOHERENTES");
    }
  }
}
