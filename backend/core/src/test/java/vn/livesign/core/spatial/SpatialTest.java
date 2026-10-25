package vn.livesign.core.spatial;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpatialTest {
  @Test void gocTronVaToaDo() {
    assertEquals(20, Geo.angleDiff(350, 10), 1e-9);
    var origin = new Geo.Point(105.84, 21.02);
    var east = Geo.offset(origin, 10, 0);
    assertEquals(10, Geo.distance(origin, east), .001);
    assertEquals(90, Geo.bearing(origin, east), .001);
    assertThrows(IllegalArgumentException.class, () -> new Geo.Point(Double.NaN, 0));
  }

  @Test void giaoTiaTinhTayVaLoaiTiaXau() {
    var p = new Geo.Point(105.84, 21.02);
    var r = Positioning.intersect(List.of(new Positioning.Ray(p, 45),
        new Positioning.Ray(Geo.offset(p, 10, 0), 315)), 10, 1000, 5);
    assertNotNull(r);
    assertEquals(5, Geo.east(p, r.point()), .001);
    assertEquals(5, Geo.north(p, r.point()), .001);
    assertTrue(r.residualM() < .001);
    assertNull(Positioning.intersect(List.of(new Positioning.Ray(p, 0),
        new Positioning.Ray(Geo.offset(p, 10, 0), 0)), 10, 1000, 5));
    assertNull(Positioning.intersect(List.of(new Positioning.Ray(p, 225),
        new Positioning.Ray(Geo.offset(p, 10, 0), 135)), 10, 1000, 5));
  }

  @Test void huongMatCanNhieuAnhVaKhongLayTrungBinhSoHoc() {
    var f = Positioning.facing(List.of(170.0, 190.0), 2, .5);
    assertEquals(0, Geo.angleDiff(f.degrees(), 0), 1e-8);
    assertEquals("aggregated", f.source());
    assertEquals("single_view", Positioning.facing(List.of(0.0), 2, .5).source());
    assertNull(Positioning.facing(List.of(0.0, 180.0), 2, .5).degrees());
  }

  @Test void nhinDuocKhongChapNhanFocalGiaHoacQua30Met() {
    assertEquals(0, Positioning.boxAngle(1024, 2048, 1152, false, .8), 1e-9);
    assertEquals(90, Positioning.boxAngle(1536, 2048, 1152, true, null), 1e-9);
    assertTrue(Positioning.inFrame(20, 2048, 1152, false, .8, .85));
    assertFalse(Positioning.inFrame(30, 2048, 1152, false, .8, .85));
  }

  @Test void bienPhiaTruocLocXeHuongVaTrangThai() {
    var p = new Geo.Point(105.84, 21.02);
    var sign = new Ahead.Sign("s", "P.103a", Geo.offset(p, 0, 40), 180.0,
        "aggregated", "ray_least_squares", "confirmed_auto", "road", List.of("car", "truck"));
    assertEquals(1, Ahead.select(List.of(sign), p, 0, 10, "car", "road", 5, 1.5, 45, 45).size());
    assertTrue(Ahead.select(List.of(sign), p, 0, 10, "motorcycle", "road", 5, 1.5, 45, 45).isEmpty());
    assertTrue(Ahead.select(List.of(sign), p, 180, 10, "car", null, 5, 1.5, 45, 45).isEmpty());
    assertTrue(Ahead.select(List.of(sign), p, 0, 0, "car", null, 5, 1.5, 45, 45).isEmpty());
    assertTrue(Ahead.select(List.of(sign), p, 0, 10, "car", "other", 5, 1.5, 45, 45).isEmpty());
  }
}
