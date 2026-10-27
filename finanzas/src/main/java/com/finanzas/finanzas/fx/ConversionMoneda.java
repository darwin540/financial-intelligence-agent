package com.finanzas.finanzas.fx;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.CurrencyMismatchException;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.MotivoBloqueo;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Point-in-time COP↔USD conversion confirmed by Darwin.
 * The rate lives on this operation only — there is no reusable FX rate in configuration.
 */
public record ConversionMoneda(
    UUID identidadId,
    LocalDate fechaValor,
    BigDecimal montoOrigen,
    CurrencyCode monedaOrigen,
    BigDecimal montoDestino,
    CurrencyCode monedaDestino,
    /**
     * Explicit rate for this operation: units of destino per 1 unit of origen
     * (destino/origen), scale 8. Optional if both amounts are provided directly;
     * when present it must reconcile with the two amounts.
     */
    BigDecimal tipoCambioOperacion,
    UUID cuentaOrigenId,
    UUID cuentaDestinoId,
    String nota,
    Estado estado) {

  public enum Estado {
    PENDIENTE,
    CONFIRMADO,
    RECHAZADO
  }

  public ConversionMoneda {
    Objects.requireNonNull(fechaValor, "fechaValor");
    Objects.requireNonNull(montoOrigen, "montoOrigen");
    Objects.requireNonNull(monedaOrigen, "monedaOrigen");
    Objects.requireNonNull(montoDestino, "montoDestino");
    Objects.requireNonNull(monedaDestino, "monedaDestino");
    Objects.requireNonNull(estado, "estado");
    if (monedaOrigen == monedaDestino) {
      throw new IllegalArgumentException(MotivoBloqueo.MONEDA_MIXTA_SIN_CONVERSION
          + ": origen y destino deben ser monedas distintas");
    }
    if ((monedaOrigen != CurrencyCode.COP && monedaOrigen != CurrencyCode.USD)
        || (monedaDestino != CurrencyCode.COP && monedaDestino != CurrencyCode.USD)) {
      throw new IllegalArgumentException("Solo COP y USD en el MVP");
    }
  }

  /**
   * Builds a conversion from explicit amounts Darwin typed. Optionally records the rate
   * used for that single operation (not stored as app-wide config).
   */
  public static ConversionMoneda deMontos(
      UUID identidadId,
      LocalDate fechaValor,
      BigDecimal montoOrigen,
      CurrencyCode monedaOrigen,
      BigDecimal montoDestino,
      CurrencyCode monedaDestino,
      BigDecimal tipoCambioOperacion,
      UUID cuentaOrigenId,
      UUID cuentaDestinoId,
      String nota,
      Estado estado) {
    BigDecimal origen = Dinero.cuantizarMonto(montoOrigen);
    BigDecimal destino = Dinero.cuantizarMonto(montoDestino);
    BigDecimal tipo = tipoCambioOperacion == null ? null : Dinero.cuantizarTasa(tipoCambioOperacion);
    if (tipo != null && origen.signum() > 0) {
      BigDecimal esperado =
          Dinero.cuantizarMonto(origen.multiply(tipo, Dinero.MC34));
      if (esperado.compareTo(destino) != 0) {
        throw new IllegalArgumentException(
            "tipoCambioOperacion no concuerda con montoOrigen/montoDestino de esta operación");
      }
    }
    return new ConversionMoneda(
        identidadId,
        fechaValor,
        origen,
        monedaOrigen,
        destino,
        monedaDestino,
        tipo,
        cuentaOrigenId,
        cuentaDestinoId,
        nota,
        estado);
  }

  /**
   * Derives destino from origen × tipo of this operation (still point-in-time; no config lookup).
   */
  public static ConversionMoneda deTipoIndicado(
      UUID identidadId,
      LocalDate fechaValor,
      BigDecimal montoOrigen,
      CurrencyCode monedaOrigen,
      CurrencyCode monedaDestino,
      BigDecimal tipoCambioOperacion,
      UUID cuentaOrigenId,
      UUID cuentaDestinoId,
      String nota,
      Estado estado) {
    Objects.requireNonNull(tipoCambioOperacion, "tipoCambioOperacion requerido en esta operación");
    BigDecimal origen = Dinero.cuantizarMonto(montoOrigen);
    BigDecimal tipo = Dinero.cuantizarTasa(tipoCambioOperacion);
    BigDecimal destino = Dinero.cuantizarMonto(origen.multiply(tipo, Dinero.MC34));
    return new ConversionMoneda(
        identidadId,
        fechaValor,
        origen,
        monedaOrigen,
        destino,
        monedaDestino,
        tipo,
        cuentaOrigenId,
        cuentaDestinoId,
        nota,
        estado);
  }

  public boolean confirmada() {
    return estado == Estado.CONFIRMADO;
  }

  /**
   * Financing an obligation/payment in {@code monedaPago} from cash in {@code monedaCaja}
   * is only allowed when a confirmed conversion links them. No market FX, no config rate.
   */
  public static void exigirConversionConfirmada(
      CurrencyCode monedaCaja, CurrencyCode monedaPago, ConversionMoneda conversion) {
    if (monedaCaja == monedaPago) {
      return;
    }
    if (conversion == null || !conversion.confirmada()) {
      throw new CurrencyMismatchException(monedaCaja, monedaPago);
    }
    boolean enlaza =
        (conversion.monedaOrigen() == monedaCaja && conversion.monedaDestino() == monedaPago)
            || (conversion.monedaOrigen() == monedaPago && conversion.monedaDestino() == monedaCaja);
    if (!enlaza) {
      throw new CurrencyMismatchException(monedaCaja, monedaPago);
    }
  }

  /** Implied rate destino/origen for this row only (scale 8). */
  public BigDecimal tipoCambioImplicito() {
    if (tipoCambioOperacion != null) {
      return tipoCambioOperacion;
    }
    if (montoOrigen.signum() == 0) {
      return Dinero.tasa("0.00000000");
    }
    return Dinero.cuantizarTasa(
        montoDestino.divide(montoOrigen, new MathContext(34, java.math.RoundingMode.HALF_UP)));
  }
}
