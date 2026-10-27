package com.finanzas.conciliacion;

import com.finanzas.finanzas.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal transfer detection: same amount, currency and value date on two own accounts.
 * Different currencies are never an internal transfer — they require ConversionMoneda.
 */
public final class DetectorTransferencia {

  public record Pierna(
      UUID movimientoId,
      UUID cuentaId,
      CurrencyCode moneda,
      LocalDate fechaValor,
      BigDecimal monto,
      String direccion,
      String referencia) {}

  public record Propuesta(
      Pierna debito,
      Pierna credito,
      CurrencyCode moneda,
      BigDecimal monto,
      LocalDate fechaValor,
      boolean referenciaCoincide) {}

  public Optional<Propuesta> detectar(List<Pierna> piernas) {
    List<Pierna> debitos =
        piernas.stream().filter(p -> "DEBITO".equals(p.direccion())).toList();
    List<Pierna> creditos =
        piernas.stream().filter(p -> "CREDITO".equals(p.direccion())).toList();
    List<Propuesta> propuestas = new ArrayList<>();
    for (Pierna d : debitos) {
      for (Pierna c : creditos) {
        if (d.cuentaId().equals(c.cuentaId())) {
          continue;
        }
        if (d.moneda() != c.moneda()) {
          continue; // FX → conversion_moneda, not internal transfer
        }
        if (!d.fechaValor().equals(c.fechaValor())) {
          continue;
        }
        if (d.monto().compareTo(c.monto()) != 0) {
          continue;
        }
        boolean refOk =
            d.referencia() != null
                && c.referencia() != null
                && HuellaMovimiento.normalizar(d.referencia())
                    .equals(HuellaMovimiento.normalizar(c.referencia()));
        propuestas.add(new Propuesta(d, c, d.moneda(), d.monto(), d.fechaValor(), refOk));
      }
    }
    return propuestas.stream().findFirst();
  }

  /**
   * Single-leg credit must not be classified as real income until the other leg appears.
   */
  public boolean esPiernaUnicaSospechosa(Pierna pierna, List<Pierna> otras) {
    if (!"CREDITO".equals(pierna.direccion())) {
      return false;
    }
    return detectar(concat(pierna, otras)).isEmpty();
  }

  private static List<Pierna> concat(Pierna p, List<Pierna> otras) {
    List<Pierna> all = new ArrayList<>(otras);
    all.add(p);
    return all;
  }
}
