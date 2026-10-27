package com.finanzas.finanzas.flujo;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.MotivoBloqueo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Daily cash-flow engine {@code flujo.diario.v1}.
 * Same-day ordering: outflows before inflows within a currency.
 */
public final class FlujoDiarioEngine {

  public static final String ALGORITMO = "flujo.diario.v1";

  public ResultadoFlujo calcular(
      CurrencyCode moneda,
      LocalDate desde,
      LocalDate hasta,
      BigDecimal saldoApertura,
      LocalDate fechaApertura,
      BigDecimal piso,
      BigDecimal reservasFijas,
      boolean esencialesRegistrados,
      List<EventoFlujo> eventos) {
    Objects.requireNonNull(moneda, "moneda");
    Objects.requireNonNull(desde, "desde");
    Objects.requireNonNull(hasta, "hasta");

    List<String> motivos = new ArrayList<>();
    List<Integer> reglas = new ArrayList<>();
    reglas.add(1);
    reglas.add(3);

    boolean definitiva = true;
    if (piso == null) {
      definitiva = false;
      motivos.add(MotivoBloqueo.MINIMO_SEGURIDAD_NO_CONFIGURADO);
    }
    if (!esencialesRegistrados) {
      definitiva = false;
      motivos.add(MotivoBloqueo.ESENCIALES_NO_REGISTRADOS);
    }

    BigDecimal reservas = reservasFijas == null ? Dinero.monto("0.00") : Dinero.cuantizarMonto(reservasFijas);
    BigDecimal pisoUsado = piso;

    Map<LocalDate, List<EventoFlujo>> porFecha = new HashMap<>();
    for (EventoFlujo e : eventos) {
      if (e.moneda() != moneda) {
        continue;
      }
      if (e.fecha().isBefore(desde) || e.fecha().isAfter(hasta)) {
        continue;
      }
      if (e.tipo() == EventoFlujo.TipoEvento.APERTURA) {
        continue;
      }
      porFecha.computeIfAbsent(e.fecha(), k -> new ArrayList<>()).add(e);
    }

    BigDecimal saldo = saldoApertura == null ? Dinero.monto("0.00") : Dinero.cuantizarMonto(saldoApertura);
    if (fechaApertura != null && fechaApertura.isBefore(desde)) {
      // opening already applied
    } else if (fechaApertura != null && !fechaApertura.isAfter(hasta) && fechaApertura.isBefore(desde)) {
      // no-op
    }

    // If opening date is on or before desde, saldo is the opening balance at start of desde
    List<PeriodoDia> dias = new ArrayList<>();
    BigDecimal saldoInicialMes = saldo;
    boolean horizonteViable = true;
    BigDecimal minLibreAntesDeAbonoExtra = null;

    for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
      BigDecimal saldoInicial = saldo;
      BigDecimal entradas = Dinero.monto("0.00");
      BigDecimal salidas = Dinero.monto("0.00");

      List<EventoFlujo> delDia = porFecha.getOrDefault(d, List.of());
      List<EventoFlujo> salidasDia =
          delDia.stream()
              .filter(e -> e.direccion() == EventoFlujo.Direccion.SALIDA)
              .sorted(Comparator.comparing(EventoFlujo::concepto))
              .toList();
      List<EventoFlujo> entradasDia =
          delDia.stream()
              .filter(e -> e.direccion() == EventoFlujo.Direccion.ENTRADA)
              .sorted(Comparator.comparing(EventoFlujo::concepto))
              .toList();

      for (EventoFlujo e : salidasDia) {
        salidas = salidas.add(Dinero.cuantizarMonto(e.monto()));
        saldo = Dinero.cuantizarMonto(saldo.subtract(Dinero.cuantizarMonto(e.monto())));
      }
      for (EventoFlujo e : entradasDia) {
        entradas = entradas.add(Dinero.cuantizarMonto(e.monto()));
        saldo = Dinero.cuantizarMonto(saldo.add(Dinero.cuantizarMonto(e.monto())));
      }

      BigDecimal libre;
      BigDecimal faltante;
      boolean viable;
      if (pisoUsado == null) {
        libre = null;
        faltante = Dinero.monto("0.00");
        viable = saldo.signum() >= 0;
      } else {
        libre = Dinero.cuantizarMonto(saldo.subtract(pisoUsado).subtract(reservas));
        viable = saldo.signum() >= 0 && libre.signum() >= 0;
        if (viable) {
          faltante = Dinero.monto("0.00");
        } else {
          // missing amount to bring libre to zero (or cover negative saldo + floor)
          BigDecimal needForLibre = libre.negate().max(Dinero.monto("0.00"));
          BigDecimal needForSaldo =
              saldo.signum() < 0 ? saldo.negate() : Dinero.monto("0.00");
          faltante = Dinero.cuantizarMonto(needForLibre.max(needForSaldo));
          if (libre.signum() < 0) {
            faltante = libre.negate();
          }
        }
      }

      if (!viable) {
        horizonteViable = false;
      }

      dias.add(
          new PeriodoDia(
              d,
              moneda,
              saldoInicial,
              Dinero.cuantizarMonto(entradas),
              Dinero.cuantizarMonto(salidas),
              saldo,
              pisoUsado,
              reservas,
              libre,
              viable,
              faltante));
    }

    if (!horizonteViable) {
      reglas.add(11);
      if (!motivos.contains(MotivoBloqueo.PRESUPUESTO_INSUFICIENTE) && definitiva) {
        motivos.add(MotivoBloqueo.PRESUPUESTO_INSUFICIENTE);
      }
    }
    reglas.add(5);

    BigDecimal variacion = Dinero.cuantizarMonto(saldo.subtract(saldoInicialMes));
    BigDecimal disponible =
        calcularDisponibleParaComprometer(dias, pisoUsado, reservas, horizonteViable);

    return new ResultadoFlujo(
        moneda,
        definitiva,
        true,
        !esencialesRegistrados,
        horizonteViable,
        variacion,
        disponible,
        List.copyOf(motivos),
        List.copyOf(dias),
        List.copyOf(reglas));
  }

  /**
   * Largest extra payment E >= 0 on the first day such that all later days remain viable.
   * If the calendar is already inviabile without E, returns 0.00.
   */
  public BigDecimal calcularDisponibleParaComprometer(
      List<PeriodoDia> dias, BigDecimal piso, BigDecimal reservas, boolean horizonteYaViable) {
    if (piso == null || dias.isEmpty()) {
      return Dinero.monto("0.00");
    }
    if (!horizonteYaViable) {
      return Dinero.monto("0.00");
    }
    // Max E on first day: after paying E, every day's libre >= 0 and saldo >= 0
    // Reconstruct from first day libre: the tightest constraint is min over days of
    // (saldo_sin_E + adjustment). Simpler binary search on first-day outflow.
    PeriodoDia primero = dias.getFirst();
    BigDecimal lo = Dinero.monto("0.00");
    BigDecimal hi = primero.libre() == null ? Dinero.monto("0.00") : primero.libre().max(Dinero.monto("0.00"));
    if (hi.signum() == 0) {
      return Dinero.monto("0.00");
    }
    // Walk: available = min over remaining horizon of projected libre if we remove E on day 0
    // Since events after day 0 are fixed, removing E from day 0 reduces every subsequent saldo by E.
    BigDecimal minLibrePosterior = primero.libre();
    for (PeriodoDia d : dias) {
      if (d.libre() != null && d.libre().compareTo(minLibrePosterior) < 0) {
        minLibrePosterior = d.libre();
      }
    }
    return Dinero.cuantizarMonto(minLibrePosterior.max(Dinero.monto("0.00")));
  }

  /**
   * Evaluates an extra payment on a given date; returns whether the horizon stays viable and the
   * resulting flow.
   */
  public ResultadoFlujo conAbonoExtra(
      CurrencyCode moneda,
      LocalDate desde,
      LocalDate hasta,
      BigDecimal saldoApertura,
      LocalDate fechaApertura,
      BigDecimal piso,
      BigDecimal reservasFijas,
      boolean esencialesRegistrados,
      List<EventoFlujo> eventosBase,
      LocalDate fechaAbono,
      BigDecimal montoAbono) {
    List<EventoFlujo> eventos = new ArrayList<>(eventosBase);
    eventos.add(
        EventoFlujo.salida(
            fechaAbono, moneda, montoAbono, "abono-extra", EventoFlujo.TipoEvento.ABONO_EXTRA));
    return calcular(
        moneda,
        desde,
        hasta,
        saldoApertura,
        fechaApertura,
        piso,
        reservasFijas,
        esencialesRegistrados,
        eventos);
  }
}
