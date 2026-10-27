package com.finanzas.finanzas;

public enum CurrencyCode {
  COP,
  USD;

  public static CurrencyCode parse(String value) {
    if (value == null) {
      throw new IllegalArgumentException("moneda requerida");
    }
    return CurrencyCode.valueOf(value.trim().toUpperCase());
  }
}
