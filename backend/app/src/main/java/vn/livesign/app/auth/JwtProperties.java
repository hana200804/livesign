package vn.livesign.app.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Khóa ký đọc từ LIVESIGN_JWT_SECRET (decisions.md 19/10: HS256, khóa ≥32 byte). */
@ConfigurationProperties("livesign.jwt")
public record JwtProperties(String secret, Duration ttl) {

  static final int MIN_SECRET_BYTES = 32;

  public JwtProperties {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "LIVESIGN_JWT_SECRET phải có ít nhất " + MIN_SECRET_BYTES + " byte");
    }
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalStateException("livesign.jwt.ttl phải dương");
    }
  }

  SecretKey key() {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Override
  public String toString() {
    return "JwtProperties[secret=<ẩn>, ttl=" + ttl + "]";
  }
}
