package com.finanzas.finanzas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Monetary amount with currency. Scale 2, HALF_UP. Binary floating types are forbidden.
 */
public final class Money {

  public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
  public static final int SCALE = 2;

  private final BigDecimal amount;
  private final CurrencyCode currency;

  private Money(BigDecimal amount, CurrencyCode currency) {
    this.amount = Objects.requireNonNull(amount, "amount").setScale(SCALE, ROUNDING);
    this.currency = Objects.requireNonNull(currency, "currency");
  }

  public static Money of(String amount, CurrencyCode currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public static Money of(BigDecimal amount, CurrencyCode currency) {
    return new Money(amount, currency);
  }

  public static Money zero(CurrencyCode currency) {
    return of("0.00", currency);
  }

  public BigDecimal amount() {
    return amount;
  }

  public CurrencyCode currency() {
    return currency;
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return of(amount.add(other.amount), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return of(amount.subtract(other.amount), currency);
  }

  public Money negate() {
    return of(amount.negate(), currency);
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  public boolean greaterThan(Money other) {
    return compareTo(other) > 0;
  }

  public boolean greaterOrEqual(Money other) {
    return compareTo(other) >= 0;
  }

  public boolean lessThan(Money other) {
    return compareTo(other) < 0;
  }

  public Money min(Money other) {
    return compareTo(other) <= 0 ? this : other;
  }

  public Money max(Money other) {
    return compareTo(other) >= 0 ? this : other;
  }

  private void requireSameCurrency(Money other) {
    if (currency != other.currency) {
      throw new CurrencyMismatchException(currency, other.currency);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Money money)) {
      return false;
    }
    return amount.compareTo(money.amount) == 0 && currency == money.currency;
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount.stripTrailingZeros(), currency);
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency;
  }
}
