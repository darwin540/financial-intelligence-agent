package com.finanzas.finanzas;

public final class CurrencyMismatchException extends RuntimeException {

  private final CurrencyCode left;
  private final CurrencyCode right;

  public CurrencyMismatchException(CurrencyCode left, CurrencyCode right) {
    super("MONEDA_MIXTA_SIN_CONVERSION: " + left + " vs " + right);
    this.left = left;
    this.right = right;
  }

  public CurrencyCode left() {
    return left;
  }

  public CurrencyCode right() {
    return right;
  }

  public String codigo() {
    return "MONEDA_MIXTA_SIN_CONVERSION";
  }
}
