package vn.livesign.core.spatial;

import java.util.List;

public final class Positioning {
  private Positioning() {}
  public record Ray(Geo.Point camera, double heading) {}
  public record Intersection(Geo.Point point, double residualM, double conditionNumber) {}
  public record Facing(Double degrees, String source, double concentration) {}
  public static double boxAngle(double cx, int width, int height, boolean pano, Double focal) {
    return pano ? (cx / width - .5) * 360 : Math.toDegrees(Math.atan((cx - width / 2.0) / ((focal == null ? .8 : focal) * Math.max(width, height))));
  }
  public static boolean inFrame(double off, int width, int height, boolean pano, double focal, double keep) {
    return pano || Math.abs(off) <= keep * Math.toDegrees(Math.atan(width / (2.0 * focal * Math.max(width, height))));
  }
  public static Facing facing(List<Double> headings, int minViews, double minR) {
    if (headings.isEmpty()) return new Facing(null, "insufficient", 0);
    double x = 0, y = 0;
    for (double h : headings) { x += Math.sin(Math.toRadians(h + 180)); y += Math.cos(Math.toRadians(h + 180)); }
    double r = Math.hypot(x, y) / headings.size();
    if (r < minR) return new Facing(null, "insufficient", r);
    return new Facing(Geo.wrap(Math.toDegrees(Math.atan2(x, y))), headings.size() < minViews ? "single_view" : "aggregated", r);
  }
  public static Intersection intersect(List<Ray> rays, double minCrossing, double maxCondition, double maxResidual) {
    if (rays.size() < 2) return null;
    boolean goodPair = false;
    for (int i = 0; i < rays.size(); i++) for (int j = 0; j < i; j++) {
      double d = Geo.angleDiff(rays.get(i).heading, rays.get(j).heading);
      if (Geo.distance(rays.get(i).camera, rays.get(j).camera) > .001 && Math.min(d, 180 - d) >= minCrossing) goodPair = true;
    }
    if (!goodPair) return null;
    Geo.Point origin = rays.getFirst().camera;
    double a = 0, b = 0, c = 0, bx = 0, by = 0;
    for (Ray ray : rays) {
      double ux = Math.sin(Math.toRadians(ray.heading)), uy = Math.cos(Math.toRadians(ray.heading));
      double px = Geo.east(origin, ray.camera), py = Geo.north(origin, ray.camera);
      double aa = 1 - ux * ux, bb = -ux * uy, cc = 1 - uy * uy;
      a += aa; b += bb; c += cc; bx += aa * px + bb * py; by += bb * px + cc * py;
    }
    double det = a * c - b * b, disc = Math.hypot(a - c, 2 * b);
    double min = (a + c - disc) / 2, max = (a + c + disc) / 2;
    if (det <= 1e-12 || min <= 0 || max / min > maxCondition) return null;
    double x = (c * bx - b * by) / det, y = (a * by - b * bx) / det, residual = 0;
    for (Ray ray : rays) {
      double dx = x - Geo.east(origin, ray.camera), dy = y - Geo.north(origin, ray.camera);
      double ux = Math.sin(Math.toRadians(ray.heading)), uy = Math.cos(Math.toRadians(ray.heading));
      if (dx * ux + dy * uy <= 0) return null;
      residual = Math.max(residual, Math.abs(dx * uy - dy * ux));
    }
    return residual > maxResidual ? null : new Intersection(Geo.offset(origin, x, y), residual, max / min);
  }
}
