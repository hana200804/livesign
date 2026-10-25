package vn.livesign.core.spatial;

/** WGS84; mét trong hệ Đông/Bắc cục bộ dùng cho tuyến demo. */
public final class Geo {
  private Geo() {}
  public static final double R = 6371008.8;
  public record Point(double lon, double lat) {
    public Point {
      if (!Double.isFinite(lon) || !Double.isFinite(lat) || lon < -180 || lon > 180 || lat < -90 || lat > 90)
        throw new IllegalArgumentException("Tọa độ không hợp lệ");
    }
  }
  public static double wrap(double a) { return ((a % 360) + 360) % 360; }
  public static double signed(double a) { return wrap(a + 180) - 180; }
  public static double angleDiff(double a, double b) { return Math.abs(signed(a - b)); }
  public static double distance(Point a, Point b) {
    double dlat = Math.toRadians(b.lat - a.lat), dlon = Math.toRadians(b.lon - a.lon);
    double h = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.pow(Math.sin(dlon / 2), 2);
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
  }
  public static double bearing(Point a, Point b) {
    double l1 = Math.toRadians(a.lat), l2 = Math.toRadians(b.lat), d = Math.toRadians(b.lon - a.lon);
    return wrap(Math.toDegrees(Math.atan2(Math.sin(d) * Math.cos(l2), Math.cos(l1) * Math.sin(l2) - Math.sin(l1) * Math.cos(l2) * Math.cos(d))));
  }
  public static double east(Point origin, Point p) { return Math.toRadians(p.lon - origin.lon) * R * Math.cos(Math.toRadians(origin.lat)); }
  public static double north(Point origin, Point p) { return Math.toRadians(p.lat - origin.lat) * R; }
  public static Point offset(Point origin, double east, double north) {
    return new Point(origin.lon + Math.toDegrees(east / (R * Math.cos(Math.toRadians(origin.lat)))), origin.lat + Math.toDegrees(north / R));
  }
  public static double segmentDistance(Point p, Point a, Point b) {
    double x = east(a, p), y = north(a, p), bx = east(a, b), by = north(a, b);
    double denom = bx * bx + by * by;
    double t = denom == 0 ? 0 : Math.max(0, Math.min(1, (x * bx + y * by) / denom));
    return Math.hypot(x - t * bx, y - t * by);
  }
}
