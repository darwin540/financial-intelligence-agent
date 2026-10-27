package com.finanzas.finanzas.obligaciones;

import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.ObligacionSnapshot;
import java.math.BigDecimal;

/**
 * Selects the cash outflow for an obligation in a period (architecture §5.1).
 */
public final class SalidaObligacion {

  public enum ResultadoProgramacion {
    CUOTA_CONTRACTUAL,
    PAGO_MINIMO,
    INSUFICIENTE,
    SIN_SALIDA
  }

  public record Decision(
      ResultadoProgramacion resultado,
      BigDecimal montoProgramado,
      BigDecimal faltantePacto,
      boolean cumplimientoContractual) {}

  private SalidaObligacion() {}

  /**
   * @param saldoCajaAntes saldo available on due date before programming this obligation
   * @param piso floor that must remain after payment (may be zero)
   */
  public static Decision decidir(ObligacionSnapshot obl, BigDecimal saldoCajaAntes, BigDecimal piso) {
    BigDecimal minimo = obl.pagoMinimo();
    BigDecimal cuota = obl.cuotaContractual();
    BigDecimal pisoSafe = piso == null ? Dinero.monto("0.00") : piso;
    BigDecimal disponible = Dinero.cuantizarMonto(saldoCajaAntes.subtract(pisoSafe));

    if (cuota != null && disponible.compareTo(cuota) >= 0) {
      return new Decision(ResultadoProgramacion.CUOTA_CONTRACTUAL, cuota, Dinero.monto("0.00"), true);
    }
    if (minimo != null && disponible.compareTo(minimo) >= 0) {
      BigDecimal faltante =
          cuota == null ? Dinero.monto("0.00") : Dinero.cuantizarMonto(cuota.subtract(minimo));
      return new Decision(
          ResultadoProgramacion.PAGO_MINIMO, minimo, faltante, cuota == null);
    }
    if (minimo != null) {
      return new Decision(ResultadoProgramacion.INSUFICIENTE, Dinero.monto("0.00"), minimo, false);
    }
    if (cuota != null) {
      return new Decision(ResultadoProgramacion.INSUFICIENTE, Dinero.monto("0.00"), cuota, false);
    }
    return new Decision(ResultadoProgramacion.SIN_SALIDA, Dinero.monto("0.00"), Dinero.monto("0.00"), false);
  }
}
