package vn.livesign.core.reliability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Vòng đời một tấm biển đến cuối năm đánh giá T (docs/architecture.md, Vòng đời tấm biển).
 *
 * <p>Trạng thái phụ thuộc trạng thái trước (giữ nguồn xác nhận, phục hồi), nên tính lần lượt từng
 * năm từ năm có bằng chứng đầu tiên tới T. Mỗi năm y chỉ đọc bằng chứng và quyết định trước
 * cutoff(y). Không có nhánh tự động vào removed/replaced; hai trạng thái này không tự hồi sinh.
 *
 * <p>Các luật còn là đề xuất "Cần nhóm quyết" trong algorithms.md, được cài như đề xuất: mâu thuẫn
 * ưu tiên trước cũ; candidate cũng có thể bị nghi; phục hồi về nguồn xác nhận trước đó khi có cặp
 * dương mới sau lần chuyển và hết mâu thuẫn; sau confirm/keep, mâu thuẫn chỉ xét lại khi có
 * seen/not_seen hợp lệ chụp sau effective_at của quyết định (watermark).
 */
public final class SignLifecycle {

  public enum ConfirmationSource {
    AUTO,
    MANUAL
  }

  /** Một lần đổi trạng thái tại cuối năm {@code year}; oldStatus null là lúc tấm xuất hiện. */
  public record StatusChange(int year, SignStatus oldStatus, SignStatus newStatus) {}

  /**
   * @param status null nếu trước cutoff chưa có cặp dương nào (chưa có tấm)
   * @param changes các lần đổi theo năm, để ghi sign_history
   */
  public record Result(
      SignStatus status,
      ConfirmationSource lastConfirmationSource,
      String replacementSignId,
      Reliability reliability,
      List<StatusChange> changes) {

    public Result {
      changes = List.copyOf(changes);
    }
  }

  private SignLifecycle() {}

  public static Result evaluate(
      Collection<Evidence> evidence, int evaluationYear, ReliabilityParams params) {
    return evaluate(evidence, List.of(), evaluationYear, params);
  }

  public static Result evaluate(
      Collection<Evidence> evidence,
      Collection<AdminDecision> decisions,
      int evaluationYear,
      ReliabilityParams params) {
    return evaluate(evidence, decisions, evaluationYear, params, List.of());
  }

  public static Result evaluate(
      Collection<Evidence> evidence,
      Collection<AdminDecision> decisions,
      int evaluationYear,
      ReliabilityParams params,
      Collection<Instant> typeChangeTimes) {
    List<Evidence> valid = evidence.stream().filter(Evidence::isValidEvidence).toList();
    List<AdminDecision> ordered =
        decisions.stream()
            .sorted(
                Comparator.comparing(AdminDecision::effectiveAt)
                    .thenComparing(AdminDecision::decidedAt))
            .toList();

    int firstYear =
        Stream.concat(
                valid.stream().map(Evidence::year),
                ordered.stream()
                    .map(d -> d.effectiveAt().atZone(java.time.ZoneOffset.UTC).getYear()))
            .min(Integer::compare)
            .orElse(evaluationYear);

    SignStatus status = null;
    ConfirmationSource source = null;
    String replacement = null;
    boolean everConfirmed = false;
    Instant transitionCutoff = null;
    Instant watermark = null;
    int applied = 0;
    Reliability reliability = null;
    List<StatusChange> changes = new ArrayList<>();

    for (int year = Math.min(firstYear, evaluationYear); year <= evaluationYear; year++) {
      Instant cutoff = ReliabilityCalculator.cutoffOf(year);
      SignStatus before = status;

      // Quyết định admin đủ cutoff áp trước, rồi mới xét luật tự động với watermark.
      while (applied < ordered.size() && ordered.get(applied).appliesBefore(cutoff)) {
        AdminDecision d = ordered.get(applied++);
        if (status != null && status.isTerminal()) {
          continue;
        }
        switch (d.decision()) {
          case CONFIRM, KEEP -> {
            status = SignStatus.CONFIRMED_MANUAL;
            source = ConfirmationSource.MANUAL;
            everConfirmed = true;
            watermark = d.effectiveAt();
          }
          case REMOVE -> status = SignStatus.REMOVED;
          case REPLACE -> {
            status = SignStatus.REPLACED;
            replacement = d.replacementSignId();
          }
          case DEFER -> {}
        }
      }

      reliability = ReliabilityCalculator.compute(valid, year, params, everConfirmed);
      if (typeChangeTimes.stream().anyMatch(t -> t.isBefore(cutoff))) {
        var reasons = new ArrayList<>(reliability.conflictReasons());
        reasons.add("type_change_evidence");
        reliability =
            new Reliability(
                year,
                reliability.alpha(),
                reliability.beta(),
                reliability.agreementScore(),
                reliability.positivePairCount(),
                reliability.negativePairCount(),
                reliability.lastSeenAt(),
                reliability.positiveAgeYears(),
                reliability.lastEvidenceAt(),
                reliability.evidenceAgeYears(),
                reasons,
                reliability.unevaluatedSignals());
      }

      if (status == null || !status.isTerminal()) {
        status =
            next(
                status,
                source,
                everConfirmed,
                reliability,
                params,
                valid,
                transitionCutoff,
                watermark,
                cutoff);
        if (status == SignStatus.CONFIRMED_AUTO) {
          source = ConfirmationSource.AUTO;
        }
        if (status != null && status.isConfirmed()) {
          everConfirmed = true;
        }
      }

      if (status != before) {
        changes.add(new StatusChange(year, before, status));
        if (status == SignStatus.UNCLEAR || status == SignStatus.SUSPECTED_CHANGE) {
          transitionCutoff = cutoff;
        }
      }
    }
    return new Result(status, source, replacement, reliability, changes);
  }

  private static SignStatus next(
      SignStatus status,
      ConfirmationSource source,
      boolean everConfirmed,
      Reliability r,
      ReliabilityParams params,
      List<Evidence> valid,
      Instant transitionCutoff,
      Instant watermark,
      Instant cutoff) {
    if (status == null && r.positivePairCount() == 0) {
      return null; // không tạo tấm từ âm hoặc unknown
    }

    boolean conflict = !r.conflictReasons().isEmpty();
    if (conflict && watermark != null) {
      conflict =
          valid.stream()
              .anyMatch(e -> e.capturedAt().isAfter(watermark) && e.capturedAt().isBefore(cutoff));
    }
    if (conflict) {
      return SignStatus.SUSPECTED_CHANGE;
    }
    if (everConfirmed && r.evidenceAgeYears() != null && r.evidenceAgeYears() > params.h()) {
      return SignStatus.UNCLEAR;
    }
    if (status == SignStatus.UNCLEAR || status == SignStatus.SUSPECTED_CHANGE) {
      boolean newPositive =
          transitionCutoff != null
              && valid.stream()
                  .anyMatch(
                      e ->
                          e.outcome() == Evidence.Outcome.SEEN
                              && !e.capturedAt().isBefore(transitionCutoff)
                              && e.capturedAt().isBefore(cutoff));
      if (!newPositive) {
        return status;
      }
      if (source == ConfirmationSource.MANUAL) {
        return SignStatus.CONFIRMED_MANUAL;
      }
      if (source == ConfirmationSource.AUTO) {
        return SignStatus.CONFIRMED_AUTO;
      }
      // Bị nghi khi còn candidate: xét lại như candidate.
    } else if (status != null && status.isConfirmed()) {
      return status;
    }
    return autoConfirms(r, params) ? SignStatus.CONFIRMED_AUTO : SignStatus.CANDIDATE;
  }

  /** count≥N và score≥θ, chỉ khi N/θ đã có value; đúng bằng θ thì được vào. */
  private static boolean autoConfirms(Reliability r, ReliabilityParams params) {
    return params.autoConfirmEnabled()
        && r.positivePairCount() >= params.n()
        && r.agreementScore() >= params.theta();
  }
}
