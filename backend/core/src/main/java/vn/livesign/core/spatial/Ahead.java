package vn.livesign.core.spatial;

import java.util.*;

/** Phép lọc ứng viên không giữ trạng thái; chống lặp thuộc phiên Android. */
public final class Ahead {
  private Ahead() {}

  public record Sign(
      String id,
      String type,
      Geo.Point point,
      Double facing,
      String facingSource,
      String locationMethod,
      String status,
      String roadRef,
      List<String> vehicles) {
    public boolean alertable(String vehicle) {
      return vehicles.contains(vehicle)
          && facing != null
          && "aggregated".equals(facingSource)
          && !"camera_proxy".equals(locationMethod)
          && Set.of("confirmed_auto", "confirmed_manual", "unclear", "suspected_change")
              .contains(status);
    }
  }

  public record Match(Sign sign, double distanceM, double leadTimeS) {}

  public static String warningLabel(String status) {
    return switch (status) {
      case "unclear" -> "lâu chưa xác minh";
      case "suspected_change" -> "chưa chắc chắn";
      default -> null;
    };
  }

  public static List<Match> select(
      List<Sign> signs,
      Geo.Point camera,
      double heading,
      double speed,
      String vehicle,
      String road,
      double lead,
      double buffer,
      double maxBearing,
      double maxFacing) {
    if (!Double.isFinite(speed)
        || speed < 0
        || !Double.isFinite(heading)
        || heading < 0
        || heading >= 360) throw new IllegalArgumentException("Hướng/tốc độ sai");
    if (speed == 0) return List.of();
    return signs.stream()
        .filter(s -> s.alertable(vehicle))
        .filter(s -> road == null || road.equals(s.roadRef))
        .filter(s -> Geo.angleDiff(heading, Geo.bearing(camera, s.point)) <= maxBearing)
        .filter(s -> Geo.angleDiff(s.facing, Geo.bearing(s.point, camera)) <= maxFacing)
        .map(
            s -> new Match(s, Geo.distance(camera, s.point), Geo.distance(camera, s.point) / speed))
        .filter(m -> m.distanceM > 0 && m.distanceM <= speed * (lead + buffer))
        .sorted(Comparator.comparingDouble(Match::distanceM).thenComparing(m -> m.sign.id))
        .toList();
  }
}
