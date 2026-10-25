package vn.livesign.core.reliability;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Một lần thấy accepted đã gán vào tấm biển đang tính, theo docs/contracts/observation.md.
 *
 * @param creatorId người chụp gốc; null thì không tạo cặp
 * @param capturedAt thời điểm chụp gốc UTC; precision=day lưu 00:00 của ngày đó
 */
public record Evidence(
    String observationId,
    String creatorId,
    Instant capturedAt,
    TimePrecision precision,
    Outcome outcome) {

  public enum Outcome {
    SEEN,
    NOT_SEEN,
    UNKNOWN
  }

  public enum TimePrecision {
    INSTANT,
    DAY
  }

  public Evidence {
    Objects.requireNonNull(observationId, "observationId");
    Objects.requireNonNull(capturedAt, "capturedAt");
    Objects.requireNonNull(precision, "precision");
    Objects.requireNonNull(outcome, "outcome");
  }

  /** Năm UTC của lần chụp, dùng làm t trong w=base^(T−t) và khóa cặp. */
  public int year() {
    return capturedAt.atZone(ZoneOffset.UTC).getYear();
  }

  /** Thời điểm muộn nhất có thể của lần chụp: cuối ngày nếu chỉ biết ngày. */
  Instant latestPossible() {
    return precision == TimePrecision.DAY
        ? capturedAt.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS).minusNanos(1)
        : capturedAt;
  }

  /** seen/not_seen có người chụp; unknown không bao giờ là bằng chứng. */
  boolean isValidEvidence() {
    return creatorId != null && outcome != Outcome.UNKNOWN;
  }
}
