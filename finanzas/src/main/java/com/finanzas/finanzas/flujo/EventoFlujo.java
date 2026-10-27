package com.finanzas.finanzas.flujo;

import com.finanzas.finanzas.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EventoFlujo(
    LocalDate fecha,
    CurrencyCode moneda,
    Direccion direccion,
    BigDecimal monto,
    String concepto,
    TipoEvento tipo) {

  public enum Direccion {
    ENTRADA,
    SALIDA
  }

  public enum TipoEvento {
    APERTURA,
    ESENCIAL,
    PAGO_OBLIGACION,
    INGRESO,
    RESERVA,
    ABONO_EXTRA,
    OTRO
  }

  public static EventoFlujo entrada(
      LocalDate fecha, CurrencyCode moneda, BigDecimal monto, String concepto, TipoEvento tipo) {
    return new EventoFlujo(fecha, moneda, Direccion.ENTRADA, monto, concepto, tipo);
  }

  public static EventoFlujo salida(
      LocalDate fecha, CurrencyCode moneda, BigDecimal monto, String concepto, TipoEvento tipo) {
    return new EventoFlujo(fecha, moneda, Direccion.SALIDA, monto, concepto, tipo);
  }
}
