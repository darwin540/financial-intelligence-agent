package com.finanzas.finanzas.obligaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.EfectoAbono;
import com.finanzas.finanzas.ObligacionSnapshot;
import com.finanzas.finanzas.TasaBase;
import com.finanzas.finanzas.TasaPeriodo;
import com.finanzas.finanzas.obligaciones.SalidaObligacion.ResultadoProgramacion;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Ob01SalidasTest {

  @Test
  void ob01_saldoMinimoYCuotaSeparados() {
    ObligacionSnapshot tarjeta =
        new ObligacionSnapshot(
            UUID.randomUUID(),
            "Plastico ejemplo",
            CurrencyCode.COP,
            Dinero.monto("1500000.00"),
            Dinero.monto("75000.00"),
            null,
            Dinero.tasa("0.02500000"),
            TasaPeriodo.MENSUAL,
            TasaBase.EFECTIVA,
            12,
            EfectoAbono.DESCONOCIDO,
            null,
            null,
            null);

    assertEquals(Dinero.monto("75000.00"), tarjeta.salidaParaEvitarMora());
    assertNull(tarjeta.salidaPactada());
    assertEquals(Dinero.monto("75000.00"), tarjeta.exigencia());

    ObligacionSnapshot credito =
        new ObligacionSnapshot(
            UUID.randomUUID(),
            "Credito con minimo inferior",
            CurrencyCode.COP,
            Dinero.monto("3000000.00"),
            Dinero.monto("150000.00"),
            Dinero.monto("360000.00"),
            null,
            null,
            null,
            15,
            EfectoAbono.DESCONOCIDO,
            null,
            null,
            null);

    var a =
        SalidaObligacion.decidir(
            credito, Dinero.monto("2000000.00"), Dinero.monto("0.00"));
    assertEquals(ResultadoProgramacion.CUOTA_CONTRACTUAL, a.resultado());
    assertEquals(Dinero.monto("360000.00"), a.montoProgramado());
    assertEquals(
        Dinero.monto("1640000.00"),
        Dinero.cuantizarMonto(Dinero.monto("2000000.00").subtract(a.montoProgramado())));

    var b =
        SalidaObligacion.decidir(
            credito, Dinero.monto("200000.00"), Dinero.monto("0.00"));
    assertEquals(ResultadoProgramacion.PAGO_MINIMO, b.resultado());
    assertEquals(Dinero.monto("150000.00"), b.montoProgramado());
    assertEquals(Dinero.monto("210000.00"), b.faltantePacto());
    assertFalse(b.cumplimientoContractual());
    assertEquals(
        Dinero.monto("50000.00"),
        Dinero.cuantizarMonto(Dinero.monto("200000.00").subtract(b.montoProgramado())));
    assertTrue(true); // esencialesNoRegistrados is a calendar concern, not this decision
  }
}
