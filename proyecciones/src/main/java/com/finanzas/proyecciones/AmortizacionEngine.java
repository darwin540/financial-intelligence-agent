package com.finanzas.proyecciones;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.EfectoAbono;
import com.finanzas.finanzas.MotivoBloqueo;
import com.finanzas.finanzas.ObligacionSnapshot;
import com.finanzas.finanzas.TasaBase;
import com.finanzas.finanzas.TasaPeriodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Monthly amortization v1. Definitive only when inputs are complete and rate base is supported.
 */
public final class AmortizacionEngine {

  public static final String ALGORITMO = "amortizacion.mensual.v1";
  public static final int HORIZONTE_MAX_PERIODOS = 360;

  public record Periodo(
      LocalDate fecha,
      CurrencyCode moneda,
      BigDecimal saldoInicial,
      BigDecimal interes,
      BigDecimal pago,
      BigDecimal saldoFinal) {}

  public record Resultado(
      UUID obligacionId,
      CurrencyCode moneda,
      boolean definitiva,
      boolean esEstimacion,
      BigDecimal tasaMensualEfectivaUsada,
      BigDecimal interesTotal,
      LocalDate fechaLiquidacion,
      List<String> motivosBloqueo,
      List<Periodo> periodos) {

    public static Resultado bloqueada(UUID id, CurrencyCode moneda, String... motivos) {
      return new Resultado(
          id, moneda, false, true, null, null, null, List.of(motivos), List.of());
    }
  }

  public Resultado proyectar(
      ObligacionSnapshot obl,
      BigDecimal pagoPeriodo,
      LocalDate primerPago,
      EfectoAbono efectoAbonoOverride) {
    CurrencyCode moneda = obl.moneda();
    UUID id = obl.identidadId();

    if (obl.saldoTotal() == null) {
      return Resultado.bloqueada(id, moneda, MotivoBloqueo.PAGO_A_USAR_AUSENTE);
    }
    EfectoAbono efecto =
        efectoAbonoOverride != null ? efectoAbonoOverride : obl.efectoAbono();
    if (efecto == null || efecto == EfectoAbono.DESCONOCIDO) {
      return Resultado.bloqueada(id, moneda, MotivoBloqueo.EFECTO_ABONO_DESCONOCIDO);
    }
    if (efecto == EfectoAbono.REDUCE_CUOTA
        && (obl.reglaRecalculoCuota() == null || obl.reglaRecalculoCuota().isBlank())) {
      return Resultado.bloqueada(id, moneda, MotivoBloqueo.REGLA_RECALCULO_AUSENTE);
    }
    if (primerPago == null && obl.diaVencimiento() == null) {
      return Resultado.bloqueada(id, moneda, MotivoBloqueo.VENCIMIENTO_AUSENTE);
    }
    BigDecimal pago =
        pagoPeriodo != null
            ? pagoPeriodo
            : (obl.cuotaContractual() != null ? obl.cuotaContractual() : obl.pagoMinimo());
    if (pago == null) {
      return Resultado.bloqueada(id, moneda, MotivoBloqueo.PAGO_A_USAR_AUSENTE);
    }

    TasaMensualConverter.Resultado tasa =
        TasaMensualConverter.convertir(obl.tasaValor(), obl.tasaPeriodo(), obl.tasaBase());
    if (tasa instanceof TasaMensualConverter.Bloqueada b) {
      return Resultado.bloqueada(id, moneda, b.motivo());
    }
    BigDecimal tasaMensual = ((TasaMensualConverter.Usable) tasa).tasaMensualEfectiva();

    // REDUCE_PLAZO: contractual installment stays until balance hits zero; surplus cuts principal.
    BigDecimal saldo = Dinero.cuantizarMonto(obl.saldoTotal());
    BigDecimal interesTotal = Dinero.monto("0.00");
    List<Periodo> periodos = new ArrayList<>();
    LocalDate fecha = primerPago;
    LocalDate liquidacion = null;

    for (int i = 0; i < HORIZONTE_MAX_PERIODOS && saldo.signum() > 0; i++) {
      BigDecimal interes = Dinero.interesPeriodo(saldo, tasaMensual);
      BigDecimal deuda = Dinero.cuantizarMonto(saldo.add(interes));
      BigDecimal pagoAplicado = pago.min(deuda);
      // On last possible payment within budget semantics, pay what's owed if pago covers it
      if (pagoAplicado.compareTo(deuda) >= 0) {
        pagoAplicado = deuda;
      }
      BigDecimal saldoFinal = Dinero.cuantizarMonto(saldo.add(interes).subtract(pagoAplicado));
      if (saldoFinal.signum() < 0) {
        saldoFinal = Dinero.monto("0.00");
      }
      periodos.add(new Periodo(fecha, moneda, saldo, interes, pagoAplicado, saldoFinal));
      interesTotal = Dinero.cuantizarMonto(interesTotal.add(interes));
      saldo = saldoFinal;
      if (saldo.signum() == 0) {
        liquidacion = fecha;
        break;
      }
      fecha = fecha.plusMonths(1);
    }

    if (liquidacion == null) {
      return new Resultado(
          id,
          moneda,
          false,
          true,
          tasaMensual,
          null,
          null,
          List.of(MotivoBloqueo.NO_LIQUIDA_EN_HORIZONTE),
          List.copyOf(periodos));
    }

    return new Resultado(
        id,
        moneda,
        true,
        true,
        tasaMensual,
        interesTotal,
        liquidacion,
        List.of(),
        List.copyOf(periodos));
  }

  public Resultado proyectarUnPeriodo(
      CurrencyCode moneda,
      BigDecimal saldo,
      BigDecimal tasaMensualEfectiva,
      BigDecimal pago,
      LocalDate fecha) {
    BigDecimal interes = Dinero.interesPeriodo(saldo, tasaMensualEfectiva);
    BigDecimal saldoFinal = Dinero.cuantizarMonto(saldo.add(interes).subtract(pago));
    if (saldoFinal.signum() < 0) {
      saldoFinal = Dinero.monto("0.00");
    }
    Periodo p = new Periodo(fecha, moneda, saldo, interes, pago, saldoFinal);
    return new Resultado(
        null,
        moneda,
        true,
        true,
        Dinero.cuantizarTasa(tasaMensualEfectiva),
        interes,
        saldoFinal.signum() == 0 ? fecha : null,
        List.of(),
        List.of(p));
  }

  public static ObligacionSnapshot obligacionConTasa(
      UUID id,
      String alias,
      CurrencyCode moneda,
      BigDecimal saldo,
      BigDecimal pagoMinimo,
      BigDecimal cuota,
      BigDecimal tasaValor,
      TasaPeriodo periodo,
      TasaBase base,
      Integer diaVencimiento,
      EfectoAbono efecto) {
    return new ObligacionSnapshot(
        id,
        alias,
        moneda,
        saldo,
        pagoMinimo,
        cuota,
        tasaValor,
        periodo,
        base,
        diaVencimiento,
        efecto,
        null,
        null,
        null);
  }
}
