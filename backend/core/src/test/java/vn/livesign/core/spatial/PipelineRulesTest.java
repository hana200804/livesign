package vn.livesign.core.spatial;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.reliability.*;

class PipelineRulesTest {
  final CoreConfig p = CoreConfig.load(Path.of("../../config/params.yaml"));
  final SignCatalog catalog = SignCatalog.load(Path.of("../../ai/configs/classes.yaml"));
  final Geo.Point origin = new Geo.Point(105.84, 21.02);

  Pipeline.Detection d(
      String id,
      String seq,
      String image,
      String type,
      double x,
      double y,
      double heading,
      String feature,
      String target,
      String outcome,
      String date) {
    var point = Geo.offset(origin, x, y);
    return new Pipeline.Detection(
        id,
        seq,
        "creator:" + seq,
        Instant.parse(date),
        "instant",
        image,
        type,
        point,
        heading,
        0.0,
        20.0,
        feature,
        feature == null
            ? List.of()
            : List.of(
                new Pipeline.Feature(
                    feature,
                    "regulatory--no-parking--g1",
                    Geo.offset(point, 0, heading == 0 ? 10 : -10))),
        target,
        outcome);
  }

  List<Pipeline.Detection> track(String seq, double x, double heading) {
    return List.of(
        d(
            seq + "a",
            seq,
            seq + "ia",
            "P.131a",
            x,
            0,
            heading,
            "f:" + seq,
            null,
            "seen",
            "2025-01-01T00:00:00Z"),
        d(
            seq + "b",
            seq,
            seq + "ib",
            "P.131a",
            x,
            1,
            heading,
            "f:" + seq,
            null,
            "seen",
            "2025-01-01T00:00:01Z"));
  }

  @Test
  void khongGopNguocChieuHoacNoiChuoi() {
    var input = new ArrayList<>(track("a", 0, 0));
    input.addAll(track("b", 1, 180));
    assertEquals(2, Pipeline.build(input, p, catalog).size());
    input = new ArrayList<>(track("a", 0, 0));
    input.addAll(track("b", 15, 0));
    input.addAll(track("c", 30, 0));
    assertTrue(
        Pipeline.build(input, p, catalog).stream().noneMatch(c -> c.observations().size() == 6));
  }

  @Test
  void featureHoaVaSaiNhanKhongDuocChon() {
    var a = track("a", 0, 0).getFirst();
    var ambiguous =
        new Pipeline.Detection(
            a.id(),
            a.sequence(),
            a.creator(),
            a.time(),
            a.precision(),
            a.image(),
            a.type(),
            a.camera(),
            a.heading(),
            a.off(),
            a.boxHeight(),
            null,
            List.of(
                new Pipeline.Feature("f1", "regulatory--no-parking--g1", Geo.offset(origin, 0, 10)),
                new Pipeline.Feature(
                    "f2", "regulatory--no-parking--g1", Geo.offset(origin, 0, 20))),
            null,
            "seen");
    assertNull(Pipeline.feature(ambiguous, p, catalog));
    var wrong = d("w", "s", "i", "P.102", 0, 0, 0, "wrong", null, "seen", "2025-01-01T00:00:00Z");
    assertNull(Pipeline.feature(wrong, p, catalog));
    assertEquals(
        "camera_proxy", Pipeline.build(List.of(wrong), p, catalog).getFirst().locationMethod());
  }

  @Test
  void trackKhongFeatureDuaVaoGiaoTiaVaKhoangTrong() {
    var a = d("a", "s", "i1", "P.131a", 0, 0, 45, null, null, "seen", "2025-01-01T00:00:00Z");
    var b = d("b", "s", "i2", "P.131a", 10, 0, 315, null, null, "seen", "2025-01-01T00:00:01Z");
    assertEquals(1, Pipeline.build(List.of(a, b), p, catalog).size());
    var late = d("c", "s", "i3", "P.131a", 10, 0, 315, null, null, "seen", "2025-01-01T00:01:01Z");
    assertEquals(2, Pipeline.build(List.of(a, late), p, catalog).size());
  }

  Pipeline.Cluster cluster(String id, String type, List<Pipeline.Detection> obs) {
    return new Pipeline.Cluster(
        id,
        type,
        origin,
        "ray_least_squares",
        new Positioning.Facing(180.0, "aggregated", 1),
        2,
        0.0,
        null,
        obs);
  }

  @Test
  void doiLoaiCanAmVaKhongDongXuatHien() {
    var old =
        d("a", "old", "old-image", "P.131a", 0, 0, 0, null, "old", "seen", "2023-01-01T00:00:00Z");
    var negative =
        d(
            "b",
            "negative",
            "absent",
            "P.131a",
            0,
            0,
            0,
            null,
            "old",
            "not_seen",
            "2025-01-01T00:00:00Z");
    var newer =
        d("c", "new", "new-image", "P.102", 0, 0, 0, null, "new", "seen", "2025-01-02T00:00:00Z");
    var input =
        List.of(
            cluster("old", "P.131a", List.of(old, negative)),
            cluster("new", "P.102", List.of(newer)));
    assertEquals(1, TypeChanges.detect(input, p).get("old").size());
    var recovered =
        d(
            "d",
            "old-again",
            "recovered",
            "P.131a",
            0,
            0,
            0,
            null,
            "old",
            "seen",
            "2025-02-01T00:00:00Z");
    assertTrue(
        TypeChanges.detect(
                List.of(
                    cluster("old", "P.131a", List.of(old, negative, recovered)), input.getLast()),
                p)
            .isEmpty());
    var shared =
        d("e", "old", "old-image", "P.102", 0, 0, 0, null, "new", "seen", "2025-01-02T00:00:00Z");
    assertTrue(
        TypeChanges.detect(List.of(input.getFirst(), cluster("new", "P.102", List.of(shared))), p)
            .isEmpty());
  }

  @Test
  void snapshotCutoffVaNguongNull() {
    var obs = new ArrayList<>(track("a", 0, 0));
    obs.add(
        d(
            "future",
            "future",
            "future",
            "P.102",
            10,
            0,
            0,
            null,
            null,
            "seen",
            "2026-01-01T00:00:00Z"));
    var years = SnapshotEngine.build(obs, Map.of(), 2025, p, catalog);
    assertEquals(1, years.getLast().signs().size());
    assertEquals(SignStatus.CANDIDATE, years.getLast().signs().getFirst().lifecycle().status());
    assertTrue(
        SnapshotEngine.build(List.of(), Map.of(), 2025, p, catalog).getLast().signs().isEmpty());
  }

  @Test
  void tinhKhoangCachDenDoanVaCauHinhKhongTuLayProposed() {
    assertEquals(
        5, Geo.segmentDistance(Geo.offset(origin, 5, 5), origin, Geo.offset(origin, 10, 0)), .001);
    assertEquals(10, Geo.segmentDistance(Geo.offset(origin, 10, 0), origin, origin), .001);
    assertNull(p.value("reliability.N"));
    assertFalse(p.enabled("workflow.negative_evidence_enabled"));
    assertThrows(IllegalArgumentException.class, () -> p.number("reliability.N"));
    assertThrows(IllegalArgumentException.class, () -> p.value("missing.path"));
    assertEquals(64, p.sha256().length());
    assertNotEquals(0, p.bytes().length);
    assertNotNull(p.version());
  }
}
