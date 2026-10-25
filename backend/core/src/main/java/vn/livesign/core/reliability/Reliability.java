package vn.livesign.core.reliability;

import java.time.Instant;
import java.util.List;

/**
 * Điểm tin cậy của một tấm tại năm đánh giá T, dựng từ bằng chứng trước cutoff.
 *
 * <p>Số cặp là số thật, không trọng số, không tính prior. {@code agreementScore} là quy ước, chưa
 * hiệu chỉnh thành xác suất, và không liên quan detector_confidence.
 *
 * @param conflictReasons dấu hiệu mâu thuẫn đã kích hoạt, mã như conflict_reasons của API
 * @param unevaluatedSignals dấu hiệu chưa tính được vì tham số null
 */
public record Reliability(
    int evaluationYear,
    double alpha,
    double beta,
    double agreementScore,
    int positivePairCount,
    int negativePairCount,
    Instant lastSeenAt,
    Integer positiveAgeYears,
    Instant lastEvidenceAt,
    Integer evidenceAgeYears,
    List<String> conflictReasons,
    List<String> unevaluatedSignals) {

  public static final String LATEST_K_PAIRS_NEGATIVE = "latest_k_pairs_negative";
  public static final String AGREEMENT_BELOW_LOW = "agreement_below_low";

  public Reliability {
    conflictReasons = List.copyOf(conflictReasons);
    unevaluatedSignals = List.copyOf(unevaluatedSignals);
  }
}
