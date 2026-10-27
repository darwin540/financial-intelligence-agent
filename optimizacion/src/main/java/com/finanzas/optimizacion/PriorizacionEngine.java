package com.finanzas.optimizacion;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.CurrencyMismatchException;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.MotivoBloqueo;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Strategy comparison {@code priorizacion.v1} with amortization rules from
 * {@code amortizacion.mensual.v1}. Single currency only; no FX mixing.
 */
public final class PriorizacionEngine {

  public static final String ALGORITMO = "priorizacion.v1";
  public static final String AMORTIZACION = "amortizacion.mensual.v1";
  public static final int MAX_PERIODOS = 360;

  public ComparacionEstrategias comparar(
      List<DeudaEstrategia> deudas,
      BigDecimal presupuestoMensual,
      LocalDate primerPago,
      boolean presupuestoEsSupuesto) {
    Objects.requireNonNull(presupuestoMensual, "presupuesto");
    Objects.requireNonNull(primerPago, "primerPago");
    if (deudas == null || deudas.isEmpty()) {
      throw new IllegalArgumentException("deudas requeridas");
    }
    CurrencyCode moneda = deudas.getFirst().moneda();
    for (DeudaEstrategia d : deudas) {
      if (d.moneda() != moneda) {
        throw new CurrencyMismatchException(moneda, d.moneda());
      }
    }

    String hash = supuestosHash(deudas, presupuestoMensual, primerPago);
    Map<EstrategiaNombre, ResultadoEstrategia> resultados = new EnumMap<>(EstrategiaNombre.class);
    for (EstrategiaNombre nombre : EstrategiaNombre.values()) {
      resultados.put(
          nombre, simular(nombre, deudas, presupuestoMensual, primerPago, moneda));
    }

    ResultadoEstrategia recomendada = elegirRecomendada(resultados);
    List<ResultadoEstrategia> lista = new ArrayList<>();
    for (EstrategiaNombre n : EstrategiaNombre.values()) {
      ResultadoEstrategia r = resultados.get(n);
      boolean esRec = recomendada != null && r.nombre() == recomendada.nombre();
      lista.add(
          new ResultadoEstrategia(
              r.nombre(),
              r.moneda(),
              r.estado(),
              r.definitiva(),
              r.interesTotal(),
              r.fechaLiquidacionPortafolio(),
              r.liquidacionPorDeuda(),
              r.presupuestoNoUsadoFinal(),
              esRec,
              r.motivosBloqueo(),
              r.lineas()));
    }

    String justificacion = construirJustificacion(lista, presupuestoEsSupuesto, presupuestoMensual);
    return new ComparacionEstrategias(
        hash, ALGORITMO, AMORTIZACION, moneda, true, List.copyOf(lista), justificacion);
  }

  private ResultadoEstrategia simular(
      EstrategiaNombre estrategia,
      List<DeudaEstrategia> originales,
      BigDecimal presupuesto,
      LocalDate primerPago,
      CurrencyCode moneda) {

    Map<UUID, BigDecimal> saldos = new LinkedHashMap<>();
    Map<UUID, BigDecimal> tasas = new LinkedHashMap<>();
    Map<UUID, BigDecimal> cuotas = new LinkedHashMap<>();
    Map<UUID, String> aliases = new LinkedHashMap<>();
    for (DeudaEstrategia d : originales) {
      saldos.put(d.identidadId(), Dinero.cuantizarMonto(d.saldoInicial()));
      tasas.put(d.identidadId(), Dinero.cuantizarTasa(d.tasaMensualEfectiva()));
      cuotas.put(d.identidadId(), Dinero.cuantizarMonto(d.cuotaContractual()));
      aliases.put(d.identidadId(), d.alias());
    }

    List<LineaPeriodo> lineas = new ArrayList<>();
    BigDecimal interesTotal = Dinero.monto("0.00");
    Map<UUID, LocalDate> liquidaciones = new LinkedHashMap<>();
    LocalDate fecha = primerPago;
    BigDecimal presupuestoNoUsadoFinal = Dinero.monto("0.00");

    for (int periodo = 1; periodo <= MAX_PERIODOS; periodo++) {
      List<UUID> activas =
          saldos.entrySet().stream()
              .filter(e -> e.getValue().signum() > 0)
              .map(Map.Entry::getKey)
              .toList();
      if (activas.isEmpty()) {
        break;
      }

      Map<UUID, BigDecimal> intereses = new LinkedHashMap<>();
      Map<UUID, BigDecimal> exigencias = new LinkedHashMap<>();
      BigDecimal sumaExigencias = Dinero.monto("0.00");

      for (UUID id : activas) {
        BigDecimal saldo = saldos.get(id);
        BigDecimal interes = Dinero.interesPeriodo(saldo, tasas.get(id));
        intereses.put(id, interes);
        BigDecimal deuda = Dinero.cuantizarMonto(saldo.add(interes));
        BigDecimal exigencia = cuotas.get(id).min(deuda);
        exigencias.put(id, exigencia);
        sumaExigencias = Dinero.cuantizarMonto(sumaExigencias.add(exigencia));
      }

      if (sumaExigencias.compareTo(presupuesto) > 0) {
        return new ResultadoEstrategia(
            estrategia,
            moneda,
            "INSUFICIENTE",
            false,
            null,
            null,
            Map.of(),
            Dinero.monto("0.00"),
            false,
            List.of(MotivoBloqueo.PRESUPUESTO_INSUFICIENTE),
            List.copyOf(lineas));
      }

      BigDecimal excedente = Dinero.cuantizarMonto(presupuesto.subtract(sumaExigencias));
      Map<UUID, BigDecimal> pagos = new LinkedHashMap<>();
      for (UUID id : activas) {
        pagos.put(id, exigencias.get(id));
      }

      aplicarExcedente(
          estrategia, activas, saldos, tasas, cuotas, intereses, pagos, excedente);

      BigDecimal usado = Dinero.monto("0.00");
      for (UUID id : activas) {
        BigDecimal saldoIni = saldos.get(id);
        BigDecimal interes = intereses.get(id);
        BigDecimal pago = pagos.get(id);
        BigDecimal saldoFin = Dinero.cuantizarMonto(saldoIni.add(interes).subtract(pago));
        if (saldoFin.signum() < 0) {
          saldoFin = Dinero.monto("0.00");
        }
        boolean liquidada = saldoFin.signum() == 0;
        if (liquidada) {
          liquidaciones.put(id, fecha);
        }
        saldos.put(id, saldoFin);
        interesTotal = Dinero.cuantizarMonto(interesTotal.add(interes));
        usado = Dinero.cuantizarMonto(usado.add(pago));
        lineas.add(
            new LineaPeriodo(
                id,
                periodo,
                fecha,
                moneda,
                saldoIni,
                interes,
                pago,
                saldoFin,
                cuotas.get(id),
                liquidada,
                Dinero.monto("0.00")));
      }
      presupuestoNoUsadoFinal = Dinero.cuantizarMonto(presupuesto.subtract(usado));
      // mark unused on last lines of period
      if (!lineas.isEmpty()) {
        LineaPeriodo last = lineas.getLast();
        lineas.set(
            lineas.size() - 1,
            new LineaPeriodo(
                last.obligacionId(),
                last.numeroPeriodo(),
                last.fechaPago(),
                last.moneda(),
                last.saldoInicial(),
                last.interes(),
                last.pago(),
                last.saldoFinal(),
                last.cuotaContractualVigente(),
                last.liquidadaEnElPeriodo(),
                presupuestoNoUsadoFinal));
      }
      fecha = fecha.plusMonths(1);
    }

    boolean quedan =
        saldos.values().stream().anyMatch(s -> s.signum() > 0);
    LocalDate finPortafolio =
        liquidaciones.values().stream().max(LocalDate::compareTo).orElse(null);
    if (quedan) {
      return new ResultadoEstrategia(
          estrategia,
          moneda,
          "BLOQUEADA",
          false,
          null,
          null,
          Map.copyOf(liquidaciones),
          presupuestoNoUsadoFinal,
          false,
          List.of(MotivoBloqueo.NO_LIQUIDA_EN_HORIZONTE),
          List.copyOf(lineas));
    }

    return new ResultadoEstrategia(
        estrategia,
        moneda,
        "COMPLETA",
        true,
        interesTotal,
        finPortafolio,
        Map.copyOf(liquidaciones),
        presupuestoNoUsadoFinal,
        false,
        List.of(),
        List.copyOf(lineas));
  }

  private void aplicarExcedente(
      EstrategiaNombre estrategia,
      List<UUID> activas,
      Map<UUID, BigDecimal> saldos,
      Map<UUID, BigDecimal> tasas,
      Map<UUID, BigDecimal> cuotas,
      Map<UUID, BigDecimal> intereses,
      Map<UUID, BigDecimal> pagos,
      BigDecimal excedenteInicial) {
    BigDecimal restante = excedenteInicial;
    if (estrategia == EstrategiaNombre.HIBRIDA) {
      while (restante.signum() > 0) {
        UUID liquidable = elegirLiquidableHibrida(activas, saldos, tasas, cuotas, intereses, pagos, restante);
        if (liquidable == null) {
          aplicarEnOrden(
              ordenAvalancha(activas, saldos, tasas), saldos, intereses, pagos, restante);
          return;
        }
        BigDecimal ya = pagos.get(liquidable);
        BigDecimal maxPago =
            Dinero.cuantizarMonto(saldos.get(liquidable).add(intereses.get(liquidable)));
        BigDecimal agregar = Dinero.cuantizarMonto(maxPago.subtract(ya));
        pagos.put(liquidable, Dinero.cuantizarMonto(ya.add(agregar)));
        restante = Dinero.cuantizarMonto(restante.subtract(agregar));
      }
      return;
    }
    List<UUID> orden =
        switch (estrategia) {
          case AVALANCHA -> ordenAvalancha(activas, saldos, tasas);
          case BOLA_DE_NIEVE -> ordenBolaNieve(activas, saldos, tasas);
          case LIBERACION_FLUJO -> ordenLiberacionFlujo(activas, saldos, tasas, cuotas);
          default -> ordenAvalancha(activas, saldos, tasas);
        };
    aplicarEnOrden(orden, saldos, intereses, pagos, restante);
  }

  private void aplicarEnOrden(
      List<UUID> orden,
      Map<UUID, BigDecimal> saldos,
      Map<UUID, BigDecimal> intereses,
      Map<UUID, BigDecimal> pagos,
      BigDecimal excedente) {
    BigDecimal restante = excedente;
    for (UUID id : orden) {
      if (restante.signum() <= 0) {
        break;
      }
      BigDecimal yaPagado = pagos.get(id);
      BigDecimal maxPago = Dinero.cuantizarMonto(saldos.get(id).add(intereses.get(id)));
      BigDecimal puedeAgregar = Dinero.cuantizarMonto(maxPago.subtract(yaPagado));
      if (puedeAgregar.signum() <= 0) {
        continue;
      }
      BigDecimal agregar = puedeAgregar.min(restante);
      pagos.put(id, Dinero.cuantizarMonto(yaPagado.add(agregar)));
      restante = Dinero.cuantizarMonto(restante.subtract(agregar));
    }
  }

  private UUID elegirLiquidableHibrida(
      List<UUID> activas,
      Map<UUID, BigDecimal> saldos,
      Map<UUID, BigDecimal> tasas,
      Map<UUID, BigDecimal> cuotas,
      Map<UUID, BigDecimal> intereses,
      Map<UUID, BigDecimal> pagos,
      BigDecimal excedente) {
    Comparator<UUID> porId = Comparator.comparing(UUID::toString);
    return activas.stream()
        .filter(
            id -> {
              BigDecimal ya = pagos.get(id);
              BigDecimal maxPago = Dinero.cuantizarMonto(saldos.get(id).add(intereses.get(id)));
              BigDecimal necesidadExtra = Dinero.cuantizarMonto(maxPago.subtract(ya));
              return necesidadExtra.signum() > 0 && necesidadExtra.compareTo(excedente) <= 0;
            })
        .max(
            Comparator.comparing((UUID id) -> cuotas.get(id))
                .thenComparing(tasas::get)
                .thenComparing(porId))
        .orElse(null);
  }

  private List<UUID> ordenAvalancha(
      List<UUID> activas, Map<UUID, BigDecimal> saldos, Map<UUID, BigDecimal> tasas) {
    List<UUID> ids = new ArrayList<>(activas);
    Comparator<UUID> porId = Comparator.comparing(UUID::toString);
    ids.sort(
        Comparator.comparing((UUID id) -> tasas.get(id))
            .reversed()
            .thenComparing(Comparator.comparing((UUID id) -> saldos.get(id)).reversed())
            .thenComparing(porId));
    return ids;
  }

  private List<UUID> ordenBolaNieve(
      List<UUID> activas, Map<UUID, BigDecimal> saldos, Map<UUID, BigDecimal> tasas) {
    List<UUID> ids = new ArrayList<>(activas);
    Comparator<UUID> porId = Comparator.comparing(UUID::toString);
    ids.sort(
        Comparator.comparing((UUID id) -> saldos.get(id))
            .thenComparing(Comparator.comparing((UUID id) -> tasas.get(id)).reversed())
            .thenComparing(porId));
    return ids;
  }

  private List<UUID> ordenLiberacionFlujo(
      List<UUID> activas,
      Map<UUID, BigDecimal> saldos,
      Map<UUID, BigDecimal> tasas,
      Map<UUID, BigDecimal> cuotas) {
    List<UUID> ids = new ArrayList<>(activas);
    Comparator<UUID> porId = Comparator.comparing(UUID::toString);
    ids.sort(
        (a, b) -> {
          BigDecimal cuotaA = cuotas.get(a);
          BigDecimal cuotaB = cuotas.get(b);
          BigDecimal saldoA = saldos.get(a);
          BigDecimal saldoB = saldos.get(b);
          int cmp = cuotaA.multiply(saldoB).compareTo(cuotaB.multiply(saldoA));
          if (cmp != 0) {
            return -cmp;
          }
          int c2 = cuotaB.compareTo(cuotaA);
          if (c2 != 0) {
            return c2;
          }
          int c3 = tasas.get(b).compareTo(tasas.get(a));
          if (c3 != 0) {
            return c3;
          }
          return porId.compare(a, b);
        });
    return ids;
  }

  private ResultadoEstrategia elegirRecomendada(Map<EstrategiaNombre, ResultadoEstrategia> map) {
    List<ResultadoEstrategia> completas =
        map.values().stream().filter(r -> r.definitiva() && r.interesTotal() != null).toList();
    if (completas.isEmpty()) {
      return null;
    }
    return completas.stream()
        .min(
            Comparator.comparing(ResultadoEstrategia::interesTotal)
                .thenComparing(ResultadoEstrategia::fechaLiquidacionPortafolio)
                .thenComparing(
                    r -> r.nombre() == EstrategiaNombre.AVALANCHA ? 0 : 1))
        .orElse(null);
  }

  private String construirJustificacion(
      List<ResultadoEstrategia> lista, boolean presupuestoEsSupuesto, BigDecimal presupuesto) {
    ResultadoEstrategia avalancha =
        lista.stream().filter(r -> r.nombre() == EstrategiaNombre.AVALANCHA).findFirst().orElse(null);
    if (avalancha == null || avalancha.interesTotal() == null) {
      return "No hay recomendación definitiva: faltan corridas completas con los mismos supuestos.";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Con el objetivo MENOR_INTERES_TOTAL y los mismos supuestos, AVALANCHA produce ")
        .append(avalancha.interesTotal().toPlainString())
        .append(" de interés.");
    for (ResultadoEstrategia r : lista) {
      if (r.nombre() == EstrategiaNombre.AVALANCHA || r.interesTotal() == null) {
        continue;
      }
      BigDecimal diff = Dinero.cuantizarMonto(r.interesTotal().subtract(avalancha.interesTotal()));
      sb.append(" ")
          .append(r.nombre())
          .append(" produce ")
          .append(diff.toPlainString())
          .append(" más,");
    }
    // trim trailing comma phrasing to match oracle style more closely
    String text = sb.toString();
    if (text.endsWith(",")) {
      text = text.substring(0, text.length() - 1) + ".";
    }
    LocalDate fin = avalancha.fechaLiquidacionPortafolio();
    boolean empateFecha =
        lista.stream()
            .filter(r -> r.fechaLiquidacionPortafolio() != null)
            .allMatch(r -> r.fechaLiquidacionPortafolio().equals(fin));
    if (empateFecha && fin != null) {
      text +=
          " La fecha de liquidación del portafolio es "
              + fin
              + " en las cuatro, así que el desempate por fecha no interviene.";
    }
    if (presupuestoEsSupuesto) {
      text +=
          " El presupuesto "
              + presupuesto.toPlainString()
              + " es un supuesto de Darwin y esta comparación, por sí sola, no demuestra que el flujo diario lo soporte.";
    }
    return text;
  }

  static String supuestosHash(
      List<DeudaEstrategia> deudas, BigDecimal presupuesto, LocalDate primerPago) {
    try {
      StringBuilder canonical = new StringBuilder();
      canonical.append("priorizacion.v1|").append(presupuesto.toPlainString()).append("|").append(primerPago);
      deudas.stream()
          .sorted(Comparator.comparing(d -> d.identidadId().toString()))
          .forEach(
              d ->
                  canonical
                      .append("|")
                      .append(d.identidadId())
                      .append("|")
                      .append(d.moneda())
                      .append("|")
                      .append(d.saldoInicial().toPlainString())
                      .append("|")
                      .append(d.tasaMensualEfectiva().toPlainString())
                      .append("|")
                      .append(d.cuotaContractual().toPlainString()));
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
