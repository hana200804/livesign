package vn.livesign.core.reliability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.livesign.core.reliability.Evidence.Outcome.NOT_SEEN;
import static vn.livesign.core.reliability.Evidence.Outcome.SEEN;
import static vn.livesign.core.reliability.Evidence.Outcome.UNKNOWN;
import static vn.livesign.core.reliability.Reliability.AGREEMENT_BELOW_LOW;
import static vn.livesign.core.reliability.Reliability.LATEST_K_PAIRS_NEGATIVE;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.livesign.core.reliability.Evidence.Outcome;
import vn.livesign.core.reliability.Evidence.TimePrecision;
import vn.livesign.core.reliability.SignLifecycle.ConfirmationSource;
import vn.livesign.core.reliability.SignLifecycle.Result;

/**
 * Bảng "Unit test điểm và vòng đời — bắt buộc" trong docs/testing.md.
 *
 * <p>Fixture là ví dụ bốn năm của algorithms.md: N=3, θ=0,75, θ_thấp=0,40, H=2, K=2, base=0,5. Đây
 * là cấu hình test, không phải giá trị vận hành (N/θ trong params.yaml còn null).
 */
class ReliabilityTest {

  static final double EPS = 1e-9;
  static final ReliabilityParams FIXTURE = new ReliabilityParams(3, 0.75, 0.40, 2, 2, 1, 1, 0.5);
  static final ReliabilityParams NULL_THRESHOLDS =
      new ReliabilityParams(null, null, null, 2, 2, 1, 1, 0.5);

  private int seq;

  private Evidence ev(String creator, String time, Outcome outcome) {
    return new Evidence("o-" + seq++, creator, Instant.parse(time), TimePrecision.INSTANT, outcome);
  }

  private Evidence day(String creator, String date, Outcome outcome) {
    return new Evidence(
        "o-" + seq++, creator, Instant.parse(date + "T00:00:00Z"), TimePrecision.DAY, outcome);
  }

  /** Bốn người khác nhau cùng thấy năm 2022. */
  private List<Evidence> nam2022() {
    List<Evidence> list = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      list.add(ev("mapillary:c" + i, "2022-05-0" + i + "T02:00:00Z", SEEN));
    }
    return list;
  }

  private List<Evidence> bonNam() {
    List<Evidence> list = nam2022();
    list.add(ev("mapillary:c5", "2024-06-01T02:00:00Z", NOT_SEEN));
    list.add(ev("mapillary:c6", "2025-07-01T02:00:00Z", NOT_SEEN));
    return list;
  }

  private static void assertScore(Reliability r, double alpha, double beta) {
    assertEquals(alpha, r.alpha(), EPS);
    assertEquals(beta, r.beta(), EPS);
    assertEquals(alpha / (alpha + beta), r.agreementScore(), EPS);
  }

  @Test
  void priorKhongLaBangChung() {
    Reliability r = ReliabilityCalculator.compute(List.of(), 2025, FIXTURE, false);
    assertScore(r, 1, 1);
    assertEquals(0.5, r.agreementScore(), EPS);
    assertEquals(0, r.positivePairCount());
    assertNull(SignLifecycle.evaluate(List.of(), 2025, FIXTURE).status());
  }

  @Test
  void namDauCuaViDu() {
    Result r = SignLifecycle.evaluate(nam2022(), 2022, FIXTURE);
    assertScore(r.reliability(), 5, 1);
    assertEquals(5.0 / 6.0, r.reliability().agreementScore(), EPS);
    assertEquals(4, r.reliability().positivePairCount());
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
    assertEquals(ConfirmationSource.AUTO, r.lastConfirmationSource());
  }

  @Test
  void giamMotNam() {
    Result r = SignLifecycle.evaluate(nam2022(), 2023, FIXTURE);
    assertScore(r.reliability(), 3, 1);
    assertEquals(1, r.reliability().positiveAgeYears());
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
  }

  @Test
  void coMotAmMoi() {
    List<Evidence> list = nam2022();
    list.add(ev("mapillary:c5", "2024-06-01T02:00:00Z", NOT_SEEN));
    Result r = SignLifecycle.evaluate(list, 2024, FIXTURE);
    assertScore(r.reliability(), 2, 2);
    assertEquals(2, r.reliability().positiveAgeYears());
    assertEquals(0, r.reliability().evidenceAgeYears());
    assertTrue(r.reliability().conflictReasons().isEmpty());
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
  }

  @Test
  void coHaiAmGanNhat() {
    Result r = SignLifecycle.evaluate(bonNam(), 2025, FIXTURE);
    assertScore(r.reliability(), 1.5, 2.5);
    assertEquals(0.375, r.reliability().agreementScore(), EPS);
    assertEquals(4, r.reliability().positivePairCount());
    assertEquals(
        List.of(LATEST_K_PAIRS_NEGATIVE, AGREEMENT_BELOW_LOW), r.reliability().conflictReasons());
    assertEquals(SignStatus.SUSPECTED_CHANGE, r.status());
  }

  @Test
  void phucHoiCungNam() {
    List<Evidence> list = bonNam();
    list.add(ev("mapillary:c7", "2025-08-01T02:00:00Z", SEEN));
    Result r = SignLifecycle.evaluate(list, 2025, FIXTURE);
    assertScore(r.reliability(), 2.5, 2.5);
    assertTrue(r.reliability().conflictReasons().isEmpty());
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
  }

  @Test
  void phucHoiSauKhiNghiVeNguonAuto() {
    List<Evidence> list = bonNam();
    // Nghi ở cuối 2025; năm sau có hai người mới thấy lại.
    list.add(ev("mapillary:c7", "2026-03-01T02:00:00Z", SEEN));
    list.add(ev("mapillary:c8", "2026-04-01T02:00:00Z", SEEN));
    Result r = SignLifecycle.evaluate(list, 2026, FIXTURE);
    assertTrue(r.reliability().conflictReasons().isEmpty());
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
    assertEquals(
        List.of(
            new SignLifecycle.StatusChange(2022, null, SignStatus.CONFIRMED_AUTO),
            new SignLifecycle.StatusChange(
                2025, SignStatus.CONFIRMED_AUTO, SignStatus.SUSPECTED_CHANGE),
            new SignLifecycle.StatusChange(
                2026, SignStatus.SUSPECTED_CHANGE, SignStatus.CONFIRMED_AUTO)),
        r.changes());
  }

  @Test
  void chiCuKhongMat() {
    Result r = SignLifecycle.evaluate(nam2022(), 2025, FIXTURE);
    assertScore(r.reliability(), 1.5, 1);
    assertEquals(0.6, r.reliability().agreementScore(), EPS);
    assertEquals(3, r.reliability().evidenceAgeYears());
    assertEquals(SignStatus.UNCLEAR, r.status());
  }

  @Test
  void bienHKhongUnclear() {
    // Tuổi đúng H=2 ở năm 2024 chưa CHƯA RÕ.
    Result r = SignLifecycle.evaluate(nam2022(), 2024, FIXTURE);
    assertEquals(2, r.reliability().evidenceAgeYears());
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
  }

  @Test
  void motNguoiNhieuChuyen() {
    List<Evidence> list = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      list.add(ev("mapillary:c1", "2025-06-02T02:00:%02dZ".formatted(i), SEEN));
    }
    Reliability r = ReliabilityCalculator.compute(list, 2025, FIXTURE, false);
    assertEquals(1, r.positivePairCount());
    assertScore(r, 2, 1);
  }

  @Test
  void duongUuTienTrongCung1Cap() {
    List<Evidence> list =
        List.of(
            ev("mapillary:c1", "2025-03-01T00:00:00Z", NOT_SEEN),
            ev("mapillary:c1", "2025-04-01T00:00:00Z", SEEN));
    Reliability r = ReliabilityCalculator.compute(list, 2025, FIXTURE, false);
    assertEquals(1, r.positivePairCount());
    assertEquals(0, r.negativePairCount());
    assertScore(r, 2, 1);
  }

  @Test
  void unknownKhongDoiGi() {
    List<Evidence> base = bonNam();
    Reliability before = ReliabilityCalculator.compute(base, 2025, FIXTURE, true);
    List<Evidence> more = new ArrayList<>(base);
    for (int i = 0; i < 100; i++) {
      more.add(ev("mapillary:u" + i, "2025-12-01T00:00:00Z", UNKNOWN));
    }
    Reliability after = ReliabilityCalculator.compute(more, 2025, FIXTURE, true);
    assertEquals(before, after);
  }

  @Test
  void thieuNguoiKhongTaoCap() {
    List<Evidence> list = List.of(ev(null, "2025-06-02T02:00:00Z", SEEN));
    Reliability r = ReliabilityCalculator.compute(list, 2025, FIXTURE, false);
    assertEquals(0, r.positivePairCount());
    assertNull(r.lastSeenAt());
    assertNull(SignLifecycle.evaluate(list, 2025, FIXTURE).status());
  }

  @Test
  void bienThetaDungBang075() {
    // Hai người năm 2024 và một năm 2025, xét 2025: α = 1 + 2×0,5 + 1 = 3, β = 1 → 0,75.
    List<Evidence> list =
        List.of(
            ev("mapillary:c1", "2024-05-01T00:00:00Z", SEEN),
            ev("mapillary:c2", "2024-05-02T00:00:00Z", SEEN),
            ev("mapillary:c3", "2025-05-03T00:00:00Z", SEEN));
    Result r = SignLifecycle.evaluate(list, 2025, FIXTURE);
    assertEquals(0.75, r.reliability().agreementScore(), 0.0);
    assertEquals(3, r.reliability().positivePairCount());
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
  }

  @Test
  void bienThetaThap() {
    // α=2, β=3 → 0,40 đúng bằng θ_thấp: (b) false vì dùng <, không ≤.
    List<Evidence> list =
        List.of(
            ev("mapillary:c1", "2025-01-01T00:00:00Z", SEEN),
            ev("mapillary:c2", "2025-02-01T00:00:00Z", NOT_SEEN),
            ev("mapillary:c3", "2025-01-02T00:00:00Z", NOT_SEEN));
    Reliability r = ReliabilityCalculator.compute(list, 2025, FIXTURE, true);
    assertEquals(0.4, r.agreementScore(), 0.0);
    assertFalse(r.conflictReasons().contains(AGREEMENT_BELOW_LOW));
  }

  @Test
  void chuaDuKCap() {
    List<Evidence> list = new ArrayList<>(nam2022());
    list.add(ev("mapillary:c5", "2024-06-01T00:00:00Z", NOT_SEEN));
    Reliability r = ReliabilityCalculator.compute(list, 2024, FIXTURE, true);
    assertFalse(r.conflictReasons().contains(LATEST_K_PAIRS_NEGATIVE));
  }

  @Test
  void thamSoNull() {
    Result r = SignLifecycle.evaluate(nam2022(), 2022, NULL_THRESHOLDS);
    assertEquals(SignStatus.CANDIDATE, r.status());
    assertEquals(4, r.reliability().positivePairCount());

    Reliability b = ReliabilityCalculator.compute(bonNam(), 2025, NULL_THRESHOLDS, true);
    assertTrue(b.unevaluatedSignals().contains(AGREEMENT_BELOW_LOW));
    assertFalse(b.conflictReasons().contains(AGREEMENT_BELOW_LOW));
  }

  @Test
  void capCungNgayMoHo() {
    // +,-,- cùng ngày, chỉ biết ngày: + có thể nằm cuối → chưa đủ kết luận (a).
    List<Evidence> list =
        List.of(
            day("mapillary:c1", "2025-06-02", SEEN),
            day("mapillary:c2", "2025-06-02", NOT_SEEN),
            day("mapillary:c3", "2025-06-02", NOT_SEEN));
    Reliability r = ReliabilityCalculator.compute(list, 2025, FIXTURE, true);
    assertFalse(r.conflictReasons().contains(LATEST_K_PAIRS_NEGATIVE));
  }

  @Test
  void cungNgayToanAm() {
    // Hai cặp cuối chắc chắn âm, chỉ hòa giữa chúng với nhau: (a) vẫn đúng.
    List<Evidence> list =
        List.of(
            day("mapillary:c1", "2025-06-01", SEEN),
            day("mapillary:c2", "2025-06-02", NOT_SEEN),
            day("mapillary:c3", "2025-06-02", NOT_SEEN));
    Reliability r = ReliabilityCalculator.compute(list, 2025, FIXTURE, true);
    assertTrue(r.conflictReasons().contains(LATEST_K_PAIRS_NEGATIVE));
  }

  @Test
  void goThayKhongTuHoiSinh() {
    List<Evidence> list = new ArrayList<>(nam2022());
    list.add(ev("mapillary:c9", "2025-03-01T00:00:00Z", SEEN));
    AdminDecision remove =
        new AdminDecision(
            AdminDecision.Decision.REMOVE,
            Instant.parse("2023-06-01T00:00:00Z"),
            Instant.parse("2023-06-02T00:00:00Z"),
            null);
    assertEquals(
        SignStatus.REMOVED, SignLifecycle.evaluate(list, List.of(remove), 2025, FIXTURE).status());

    AdminDecision replace =
        new AdminDecision(
            AdminDecision.Decision.REPLACE,
            Instant.parse("2023-06-01T00:00:00Z"),
            Instant.parse("2023-06-02T00:00:00Z"),
            "sign-new");
    Result r = SignLifecycle.evaluate(list, List.of(replace), 2025, FIXTURE);
    assertEquals(SignStatus.REPLACED, r.status());
    assertEquals("sign-new", r.replacementSignId());
  }

  @Test
  void quyetDinhSauCutoffKhongVaoNamCu() {
    AdminDecision remove2026 =
        new AdminDecision(
            AdminDecision.Decision.REMOVE,
            Instant.parse("2023-06-01T00:00:00Z"),
            Instant.parse("2026-10-20T00:00:00Z"),
            null);
    Result r = SignLifecycle.evaluate(nam2022(), List.of(remove2026), 2023, FIXTURE);
    assertEquals(SignStatus.CONFIRMED_AUTO, r.status());
  }

  @Test
  void keepKhongMoLaiCungTapChungCu() {
    // Nghi cuối 2025; admin keep trong 2025 sau mọi bằng chứng → xác nhận thủ công, không nghi lại.
    AdminDecision keep =
        new AdminDecision(
            AdminDecision.Decision.KEEP,
            Instant.parse("2025-09-01T00:00:00Z"),
            Instant.parse("2025-09-02T00:00:00Z"),
            null);
    Result r = SignLifecycle.evaluate(bonNam(), List.of(keep), 2025, FIXTURE);
    assertEquals(SignStatus.CONFIRMED_MANUAL, r.status());
    // Điểm thật vẫn giữ, không bị nâng giả.
    assertEquals(0.375, r.reliability().agreementScore(), EPS);
  }

  @Test
  void duLieuSauCutoffKhongRo() {
    List<Evidence> list = new ArrayList<>(nam2022());
    list.add(ev("mapillary:c5", "2024-01-01T00:00:00Z", NOT_SEEN));
    Reliability r2023 = ReliabilityCalculator.compute(list, 2023, FIXTURE, true);
    assertEquals(0, r2023.negativePairCount());
    assertScore(r2023, 3, 1);
  }

  @Test
  void docThamSoThatKhongLayProposed() {
    ReliabilityParams real =
        ReliabilityParams.fromParamsYaml(Path.of("..", "..", "config", "params.yaml"));
    assertNull(real.n());
    assertNull(real.theta());
    assertNull(real.thetaLow());
    assertEquals(2, real.h());
    assertEquals(2, real.k());
    assertEquals(1.0, real.alphaPrior());
    assertEquals(0.5, real.decayBase());
    assertFalse(real.autoConfirmEnabled());
  }

  @Test
  void tuChoiThetaThapKhongNhoHonTheta() {
    assertThrows(
        IllegalArgumentException.class, () -> new ReliabilityParams(3, 0.4, 0.4, 2, 2, 1, 1, 0.5));
  }
}
