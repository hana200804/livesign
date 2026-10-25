package vn.livesign.core.reliability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gom cặp (creator, năm), tính α/β/điểm, tuổi bằng chứng và dấu hiệu mâu thuẫn (a)(b).
 *
 * <p>Theo docs/algorithms.md, mục Điểm tin cậy. Dấu hiệu (c) cần hai sign_id nên do bước gộp cung
 * cấp, không tính ở đây.
 */
public final class ReliabilityCalculator {

  private ReliabilityCalculator() {}

  /** Cutoff độc quyền của năm đánh giá: 00:00 UTC ngày 01/01 năm sau. */
  public static Instant cutoffOf(int evaluationYear) {
    return LocalDate.of(evaluationYear + 1, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  /** Một cặp bằng chứng; [earliest, latest] là khoảng chứa thời điểm cuối của cặp. */
  record Pair(String creatorId, int year, boolean positive, Instant earliest, Instant latest) {}

  /**
   * @param evidence lần thấy của một tấm; dòng từ cutoff trở đi bị bỏ qua
   * @param wasConfirmed tấm từng confirmed_auto/confirmed_manual trước đó, điều kiện của (b)
   */
  public static Reliability compute(
      Collection<Evidence> evidence,
      int evaluationYear,
      ReliabilityParams params,
      boolean wasConfirmed) {
    Instant cutoff = cutoffOf(evaluationYear);
    List<Evidence> valid =
        evidence.stream()
            .filter(Evidence::isValidEvidence)
            .filter(e -> e.capturedAt().isBefore(cutoff))
            .toList();
    List<Pair> pairs = pairs(valid);

    double alpha = params.alphaPrior();
    double beta = params.betaPrior();
    int positives = 0;
    int negatives = 0;
    for (Pair p : pairs) {
      double w = Math.pow(params.decayBase(), evaluationYear - p.year());
      if (p.positive()) {
        alpha += w;
        positives++;
      } else {
        beta += w;
        negatives++;
      }
    }
    double score = alpha / (alpha + beta);

    Instant lastSeen =
        valid.stream()
            .filter(e -> e.outcome() == Evidence.Outcome.SEEN)
            .map(Evidence::capturedAt)
            .max(Comparator.naturalOrder())
            .orElse(null);
    Instant lastEvidence =
        valid.stream().map(Evidence::capturedAt).max(Comparator.naturalOrder()).orElse(null);

    List<String> conflicts = new ArrayList<>();
    List<String> unevaluated = new ArrayList<>();
    if (latestKCertainlyNegative(pairs, params.k())) {
      conflicts.add(Reliability.LATEST_K_PAIRS_NEGATIVE);
    }
    if (wasConfirmed) {
      if (params.thetaLow() == null) {
        unevaluated.add(Reliability.AGREEMENT_BELOW_LOW);
      } else if (score < params.thetaLow()) {
        conflicts.add(Reliability.AGREEMENT_BELOW_LOW);
      }
    }

    return new Reliability(
        evaluationYear,
        alpha,
        beta,
        score,
        positives,
        negatives,
        lastSeen,
        ageYears(lastSeen, evaluationYear),
        lastEvidence,
        ageYears(lastEvidence, evaluationYear),
        conflicts,
        unevaluated);
  }

  /** Có seen thì dương; chỉ not_seen thì âm. Nhiều sequence cùng người/năm vẫn một cặp. */
  static List<Pair> pairs(List<Evidence> valid) {
    Map<String, List<Evidence>> byKey = new LinkedHashMap<>();
    for (Evidence e : valid) {
      byKey.computeIfAbsent(e.creatorId() + "\u0000" + e.year(), x -> new ArrayList<>()).add(e);
    }
    List<Pair> pairs = new ArrayList<>();
    for (List<Evidence> group : byKey.values()) {
      Evidence first = group.get(0);
      boolean positive = group.stream().anyMatch(e -> e.outcome() == Evidence.Outcome.SEEN);
      Instant earliest =
          group.stream().map(Evidence::capturedAt).max(Comparator.naturalOrder()).orElseThrow();
      Instant latest =
          group.stream().map(Evidence::latestPossible).max(Comparator.naturalOrder()).orElseThrow();
      pairs.add(new Pair(first.creatorId(), first.year(), positive, earliest, latest));
    }
    return pairs;
  }

  /**
   * (a): K cặp mới nhất chắc chắn đều âm, với mọi cách xếp phù hợp khoảng thời gian.
   *
   * <p>Một cặp dương còn có thể nằm trong K cặp cuối khi số cặp chắc chắn muộn hơn nó nhỏ hơn K.
   * Không bẻ hòa bằng creator_id hay observation_id.
   */
  static boolean latestKCertainlyNegative(List<Pair> pairs, int k) {
    if (pairs.size() < k) {
      return false;
    }
    for (Pair p : pairs) {
      if (!p.positive()) {
        continue;
      }
      long certainlyLater = pairs.stream().filter(q -> q.earliest().isAfter(p.latest())).count();
      if (certainlyLater < k) {
        return false;
      }
    }
    return true;
  }

  private static Integer ageYears(Instant t, int evaluationYear) {
    return t == null ? null : evaluationYear - t.atZone(ZoneOffset.UTC).getYear();
  }
}
