package vn.livesign.core.reliability;

import java.time.Instant;
import java.util.Objects;

/**
 * Quyết định admin đã ghi cho một tấm. Chỉ vào run năm T khi cả effective_at và decided_at đều
 * trước cutoff (API 1.1), nên quyết định làm năm 2026 không đổi bản đồ 2023–2025.
 */
public record AdminDecision(
    Decision decision, Instant effectiveAt, Instant decidedAt, String replacementSignId) {

  public enum Decision {
    CONFIRM,
    KEEP,
    REMOVE,
    REPLACE,
    DEFER
  }

  public AdminDecision {
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    Objects.requireNonNull(decidedAt, "decidedAt");
    if (decision == Decision.REPLACE && replacementSignId == null) {
      throw new IllegalArgumentException("replace cần replacementSignId");
    }
  }

  boolean appliesBefore(Instant cutoff) {
    return effectiveAt.isBefore(cutoff) && decidedAt.isBefore(cutoff);
  }
}
