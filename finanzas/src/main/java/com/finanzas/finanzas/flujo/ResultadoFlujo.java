package com.finanzas.finanzas.flujo;

import com.finanzas.finanzas.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ResultadoFlujo(
    CurrencyCode moneda,
    boolean definitiva,
    boolean esEstimacion,
    boolean esencialesNoRegistrados,
    boolean viable,
    BigDecimal variacionDelMes,
    BigDecimal disponibleParaComprometer,
    List<String> motivosBloqueo,
    List<PeriodoDia> dias,
    List<Integer> reglasAplicadas) {

  public PeriodoDia dia(LocalDate fecha) {
    return dias.stream().filter(d -> d.fecha().equals(fecha)).findFirst().orElse(null);
  }
}
