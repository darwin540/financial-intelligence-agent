package com.finanzas.proyecciones;

import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.MotivoBloqueo;
import com.finanzas.finanzas.TasaBase;
import com.finanzas.finanzas.TasaPeriodo;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Converts supported rate bases to monthly effective rate.
 * EA annual → monthly: {@code (1+EA)^(1/12)-1} with DECIMAL128+ precision, quantize scale 8 HALF_UP.
 */
public final class TasaMensualConverter {

  private static final MathContext MC = Dinero.MC34;
  private static final BigDecimal DOCE = BigDecimal.valueOf(12);

  private TasaMensualConverter() {}

  public sealed interface Resultado permits Usable, Bloqueada {
    boolean usable();
  }

  public record Usable(BigDecimal tasaMensualEfectiva, TasaPeriodo origen) implements Resultado {
    @Override
    public boolean usable() {
      return true;
    }
  }

  public record Bloqueada(String motivo) implements Resultado {
    @Override
    public boolean usable() {
      return false;
    }
  }

  public static Resultado convertir(
      BigDecimal tasaValor, TasaPeriodo periodo, TasaBase base) {
    if (tasaValor == null || periodo == null || base == null) {
      return new Bloqueada(MotivoBloqueo.TASA_AUSENTE);
    }
    if (periodo == TasaPeriodo.MENSUAL && base == TasaBase.EFECTIVA) {
      return new Usable(Dinero.cuantizarTasa(tasaValor), periodo);
    }
    if (periodo == TasaPeriodo.ANUAL_EFECTIVA && base == TasaBase.EFECTIVA) {
      return new Usable(eaAnualAMensual(tasaValor), periodo);
    }
    return new Bloqueada(MotivoBloqueo.TASA_BASE_NO_SOPORTADA);
  }

  /**
   * {@code tasa_mensual = (1 + EA)^(1/12) - 1}, scale 8 HALF_UP.
   */
  public static BigDecimal eaAnualAMensual(BigDecimal ea) {
    BigDecimal unoMasEa = BigDecimal.ONE.add(ea, MC);
    // a^(1/12) = exp(ln(a)/12); BigDecimal has no ln — use double-free approach via
    // BigDecimal.pow with fractional exponent via MathContext + binary search / Newton's method.
    BigDecimal raiz = raizEnesima(unoMasEa, 12, MC);
    return Dinero.cuantizarTasa(raiz.subtract(BigDecimal.ONE, MC));
  }

  /**
   * nth root via Newton: x_{k+1} = ((n-1)*x + a/x^(n-1)) / n
   */
  static BigDecimal raizEnesima(BigDecimal a, int n, MathContext mc) {
    if (a.signum() < 0) {
      throw new IllegalArgumentException("EA no puede ser < -1");
    }
    if (a.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal nBd = BigDecimal.valueOf(n);
    BigDecimal nMenos1 = BigDecimal.valueOf(n - 1);
    BigDecimal x = BigDecimal.ONE.add(a.subtract(BigDecimal.ONE, mc).divide(nBd, mc), mc);
    for (int i = 0; i < 80; i++) {
      BigDecimal xPow = x.pow(n - 1, mc);
      BigDecimal next =
          x.multiply(nMenos1, mc)
              .add(a.divide(xPow, mc), mc)
              .divide(nBd, mc);
      if (next.subtract(x, mc).abs().compareTo(new BigDecimal("1e-40")) < 0) {
        return next;
      }
      x = next;
    }
    return x;
  }

  public static Optional<BigDecimal> tasaMensualOEmpty(
      BigDecimal tasaValor, TasaPeriodo periodo, TasaBase base) {
    Resultado r = convertir(tasaValor, periodo, base);
    if (r instanceof Usable u) {
      return Optional.of(u.tasaMensualEfectiva());
    }
    return Optional.empty();
  }
}
