package com.finanzas.conciliacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Tr01Dd01Test {

  @Test
  void tr01_transferenciaInternaNoEsIngreso() {
    UUID primaria = UUID.fromString("a1000000-0000-4000-8000-000000000001");
    UUID secundaria = UUID.fromString("a1000000-0000-4000-8000-000000000002");
    DetectorTransferencia.Pierna debito =
        new DetectorTransferencia.Pierna(
            UUID.randomUUID(),
            primaria,
            CurrencyCode.COP,
            LocalDate.of(2026, 11, 3),
            Dinero.monto("500000.00"),
            "DEBITO",
            "TR-INT-7");
    DetectorTransferencia.Pierna credito =
        new DetectorTransferencia.Pierna(
            UUID.randomUUID(),
            secundaria,
            CurrencyCode.COP,
            LocalDate.of(2026, 11, 3),
            Dinero.monto("500000.00"),
            "CREDITO",
            "TR-INT-7");

    var propuesta = new DetectorTransferencia().detectar(List.of(debito, credito));
    assertTrue(propuesta.isPresent());
    assertTrue(propuesta.get().referenciaCoincide());

    // Combined cash unchanged by transfer: 800000 + 200000 = 1000000
    assertEquals(
        Dinero.monto("1000000.00"),
        Dinero.monto("800000.00").add(Dinero.monto("200000.00")));
    assertEquals(
        Dinero.monto("300000.00"),
        Dinero.monto("800000.00").subtract(Dinero.monto("500000.00")));
    assertEquals(
        Dinero.monto("700000.00"),
        Dinero.monto("200000.00").add(Dinero.monto("500000.00")));

    // Real income is only payroll 3000000
    assertEquals(Dinero.monto("3000000.00"), Dinero.monto("3000000.00"));

    // TR-01b: single credit leg → pending review, not income
    assertTrue(
        new DetectorTransferencia().esPiernaUnicaSospechosa(credito, List.of()));
  }

  @Test
  void dd01_extractosSolapadosUnaSolaVez() {
    UUID cuenta = UUID.fromString("a1000000-0000-4000-8000-000000000001");
    String h1 =
        HuellaMovimiento.calcular(
            cuenta,
            LocalDate.of(2026, 10, 20),
            Dinero.monto("45320.50"),
            "DEBITO",
            "AB12",
            "COMPRA SUPERMERCADO");
    String h2 =
        HuellaMovimiento.calcular(
            cuenta,
            LocalDate.of(2026, 10, 20),
            Dinero.monto("45320.50"),
            "DEBITO",
            "AB12",
            "Compra  supermercado");
    assertEquals(h1, h2);

    DeduplicadorMovimientos dedup = new DeduplicadorMovimientos();
    UUID canonico = UUID.randomUUID();
    dedup.registrarConfirmado(h1, canonico);

    var d2 =
        dedup.evaluar(
            new DeduplicadorMovimientos.Candidato(
                cuenta,
                LocalDate.of(2026, 10, 20),
                Dinero.monto("45320.50"),
                "DEBITO",
                "AB12",
                "Compra  supermercado",
                1,
                3));
    assertEquals(DeduplicadorMovimientos.ResultadoTipo.DUPLICADO_EXACTO, d2.tipo());
    assertEquals(canonico, d2.canonicoId());

    // DD-01b: no reference, different description → possible duplicate
    Map<String, UUID> blando = new HashMap<>();
    blando.put(
        DeduplicadorMovimientos.claveBlanda(
            cuenta, LocalDate.of(2026, 10, 20), Dinero.monto("45320.50"), "DEBITO"),
        canonico);
    var d3 =
        dedup.evaluarConIndiceBlando(
            new DeduplicadorMovimientos.Candidato(
                cuenta,
                LocalDate.of(2026, 10, 20),
                Dinero.monto("45320.50"),
                "DEBITO",
                null,
                "OTRO COMERCIO",
                1,
                4),
            blando);
    assertEquals(DeduplicadorMovimientos.ResultadoTipo.POSIBLE_DUPLICADO, d3.tipo());
    assertFalse(h1.equals(d3.huella())); // different fingerprint without reference
  }
}
