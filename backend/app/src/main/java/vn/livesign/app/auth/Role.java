package vn.livesign.app.auth;

import java.util.Locale;

/** Ba vai trò của cột user.role. */
public enum Role {
  USER,
  IMPORTER,
  ADMIN;

  public String dbValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Role fromDb(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
