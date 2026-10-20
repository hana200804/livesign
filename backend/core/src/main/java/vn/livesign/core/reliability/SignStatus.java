package vn.livesign.core.reliability;

import java.util.Locale;

/** Trạng thái tấm biển, cùng enum sign.status trong db-schema.md. */
public enum SignStatus {
  CANDIDATE,
  CONFIRMED_AUTO,
  CONFIRMED_MANUAL,
  UNCLEAR,
  SUSPECTED_CHANGE,
  REMOVED,
  REPLACED;

  public String dbValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  boolean isTerminal() {
    return this == REMOVED || this == REPLACED;
  }

  boolean isConfirmed() {
    return this == CONFIRMED_AUTO || this == CONFIRMED_MANUAL;
  }
}
