package vn.livesign.core.spatial;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.config.CoreConfig;

/** Gom theo feature/vết rồi định vị và gộp; chỉ nhận đầu vào đã lọc cutoff. */
public final class Pipeline {
  private Pipeline() {}

  public record Feature(String id, String label, Geo.Point point) {}

  public record Detection(
      String id,
      String sequence,
      String creator,
      Instant time,
      String precision,
      String image,
      String type,
      Geo.Point camera,
      Double heading,
      Double off,
      Double boxHeight,
      String featureId,
      List<Feature> features,
      String targetId,
      String outcome) {
    public Detection {
      features = features == null ? List.of() : List.copyOf(features);
    }

    public Positioning.Ray ray() {
      return camera == null || heading == null || off == null
          ? null
          : new Positioning.Ray(camera, Geo.wrap(heading + off));
    }

    public String imageKey() {
      return sequence + "\u0000" + image;
    }
  }

  public record Cluster(
      String id,
      String type,
      Geo.Point point,
      String locationMethod,
      Positioning.Facing facing,
      int rayCount,
      Double residualM,
      String sourceId,
      List<Detection> observations) {}

  private record Located(List<Detection> detections, Geo.Point anchor, String featureKey) {}

  public static Feature feature(Detection d, CoreConfig params, SignCatalog catalog) {
    if (!params.enabled("positioning.feature_fallback_enabled") || d.ray() == null) return null;
    var labels = catalog.byCode(d.type).orElseThrow().mapillaryLabelCandidates();
    List<Feature> candidates =
        d.features.stream()
            .filter(f -> labels.contains(f.label))
            .filter(f -> d.featureId == null || d.featureId.equals(f.id))
            .filter(
                f ->
                    Geo.distance(d.camera, f.point)
                        <= params.number("geometry.visibility_max_distance_m"))
            .filter(f -> Geo.angleDiff(d.ray().heading(), Geo.bearing(d.camera, f.point)) < 90)
            .filter(
                f ->
                    rayDistance(d.ray(), f.point)
                        <= params.number("positioning.feature_link_max_ray_distance_m"))
            .sorted(Comparator.comparingDouble(f -> rayDistance(d.ray(), f.point)))
            .toList();
    if (candidates.isEmpty()
        || candidates.size() > 1
            && Math.abs(
                    rayDistance(d.ray(), candidates.get(0).point)
                        - rayDistance(d.ray(), candidates.get(1).point))
                < 1e-6) return null;
    return candidates.getFirst();
  }

  private static double rayDistance(Positioning.Ray ray, Geo.Point p) {
    return Geo.distance(ray.camera(), p)
        * Math.abs(
            Math.sin(Math.toRadians(Geo.signed(Geo.bearing(ray.camera(), p) - ray.heading()))));
  }

  private static String featureKey(Detection d, CoreConfig p, SignCatalog c) {
    Feature f = feature(d, p, c);
    return f == null ? d.featureId : f.id;
  }

  private static boolean cannotLink(List<Detection> a, List<Detection> b) {
    return a.stream()
        .anyMatch(
            x -> b.stream().anyMatch(y -> x.imageKey().equals(y.imageKey()) && !x.id.equals(y.id)));
  }

  private static boolean continues(Detection a, Detection b, CoreConfig p) {
    if (!a.sequence.equals(b.sequence)
        || !a.type.equals(b.type)
        || a.ray() == null
        || b.ray() == null) return false;
    if (Duration.between(a.time, b.time).toMillis() > p.number("grouping.track_max_gap_s") * 1000
        || Geo.distance(a.camera, b.camera) > p.number("grouping.track_max_gap_m")) return false;
    var intersection =
        Positioning.intersect(
            List.of(a.ray(), b.ray()),
            p.number("positioning.min_ray_crossing_deg"),
            p.number("positioning.max_condition_number"),
            p.number("positioning.max_ray_residual_m"));
    if (intersection != null)
      return Geo.distance(a.camera, intersection.point())
              <= p.number("geometry.visibility_max_distance_m")
          && Geo.distance(b.camera, intersection.point())
              <= p.number("geometry.visibility_max_distance_m");
    double angle = Geo.angleDiff(a.ray().heading(), b.ray().heading());
    // Chỉ dùng điều kiện gần song song khi có chuyển động và bbox tiến tới lớn hơn.
    return Math.min(angle, 180 - angle) < p.number("positioning.min_ray_crossing_deg")
        && Geo.distance(a.camera, b.camera) > .001
        && a.off != null
        && b.off != null
        && a.off * (b.off - a.off) > 0
        && a.boxHeight != null
        && b.boxHeight != null
        && b.boxHeight >= a.boxHeight;
  }

  private static Cluster locate(List<Detection> detections, CoreConfig p, SignCatalog catalog) {
    List<Positioning.Ray> rays =
        detections.stream()
            .filter(d -> d.ray() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    Detection::imageKey, Detection::ray, (a, b) -> a, LinkedHashMap::new))
            .values()
            .stream()
            .toList();
    var intersection =
        Positioning.intersect(
            rays,
            p.number("positioning.min_ray_crossing_deg"),
            p.number("positioning.max_condition_number"),
            p.number("positioning.max_ray_residual_m"));
    var facing =
        Positioning.facing(
            rays.stream().map(Positioning.Ray::heading).toList(),
            (int) p.number("facing.min_views"),
            p.number("facing.min_resultant_length"));
    Detection first = detections.getFirst();
    List<Feature> features =
        detections.stream().map(d -> feature(d, p, catalog)).filter(Objects::nonNull).toList();
    Feature fallback =
        features.isEmpty() || features.stream().map(Feature::id).distinct().count() != 1
            ? null
            : features.getFirst();
    Geo.Point point =
        intersection != null
            ? intersection.point()
            : fallback != null ? fallback.point : first.camera;
    String method =
        intersection != null
            ? "ray_least_squares"
            : fallback != null ? "mapillary_feature" : "camera_proxy";
    Set<String> targets = new HashSet<>();
    detections.stream().map(Detection::targetId).filter(Objects::nonNull).forEach(targets::add);
    String id =
        targets.size() == 1
            ? targets.iterator().next()
            : "sign:" + UUID.nameUUIDFromBytes(first.id.getBytes(StandardCharsets.UTF_8));
    return new Cluster(
        id,
        first.type,
        point,
        method,
        facing,
        rays.size(),
        intersection == null ? null : intersection.residualM(),
        fallback == null ? null : fallback.id,
        List.copyOf(detections));
  }

  public static List<Cluster> build(List<Detection> input, CoreConfig p, SignCatalog catalog) {
    return build(input, p, catalog, Set.of());
  }

  public static List<Cluster> build(
      List<Detection> input, CoreConfig p, SignCatalog catalog, Set<String> terminalIds) {
    List<Detection> seen =
        input.stream()
            .filter(d -> "seen".equals(d.outcome) && d.camera != null && d.type != null)
            .sorted(
                Comparator.comparing(Detection::time)
                    .thenComparing(Detection::image)
                    .thenComparing(Detection::id))
            .toList();
    List<List<Detection>> tracks = new ArrayList<>();
    for (Detection d : seen) {
      String feature = featureKey(d, p, catalog);
      List<List<Detection>> matches =
          tracks.stream()
              .filter(t -> !cannotLink(t, List.of(d)))
              .filter(
                  t -> t.getFirst().sequence.equals(d.sequence) && t.getFirst().type.equals(d.type))
              .filter(
                  t -> {
                    Detection last = t.getLast();
                    if (d.targetId == null
                        && last.targetId != null
                        && terminalIds.contains(last.targetId)) return false;
                    if (d.targetId != null && last.targetId != null)
                      return d.targetId.equals(last.targetId);
                    String key = featureKey(last, p, catalog);
                    if (feature != null || key != null) return Objects.equals(feature, key);
                    return continues(last, d, p);
                  })
              .toList();
      if (matches.size() == 1) matches.getFirst().add(d);
      else {
        var t = new ArrayList<Detection>();
        t.add(d);
        tracks.add(t);
      }
    }
    List<List<Located>> groups = new ArrayList<>();
    for (List<Detection> track : tracks) {
      Cluster loc = locate(track, p, catalog);
      List<List<Located>> matches =
          groups.stream()
              .filter(
                  g -> {
                    List<Detection> all = g.stream().flatMap(t -> t.detections.stream()).toList();
                    Cluster prev = locate(all, p, catalog);
                    if (!prev.type.equals(loc.type) || cannotLink(all, track)) return false;
                    var locked =
                        all.stream()
                            .map(Detection::targetId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    var fresh =
                        track.stream()
                            .map(Detection::targetId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    if (!locked.isEmpty() && !fresh.isEmpty()) return locked.equals(fresh);
                    if (locked.stream().anyMatch(terminalIds::contains)
                        || fresh.stream().anyMatch(terminalIds::contains)) return false;
                    return "aggregated".equals(prev.facing.source())
                        && "aggregated".equals(loc.facing.source())
                        && !"camera_proxy".equals(prev.locationMethod)
                        && !"camera_proxy".equals(loc.locationMethod)
                        && Geo.angleDiff(prev.facing.degrees(), loc.facing.degrees())
                            <= p.number("geometry.merge_max_facing_diff_deg")
                        && g.stream()
                            .allMatch(
                                a ->
                                    Geo.distance(a.anchor, loc.point)
                                        <= p.number("geometry.merge_radius_m"));
                  })
              .toList();
      Located placed = new Located(track, loc.point, loc.sourceId);
      if (matches.size() == 1) matches.getFirst().add(placed);
      else {
        var g = new ArrayList<Located>();
        g.add(placed);
        groups.add(g);
      }
    }
    List<Cluster> clusters = new ArrayList<>();
    for (var group : groups) {
      List<Detection> all =
          new ArrayList<>(group.stream().flatMap(t -> t.detections.stream()).toList());
      Cluster loc = locate(all, p, catalog);
      input.stream()
          .filter(d -> !"seen".equals(d.outcome) && loc.id.equals(d.targetId))
          .forEach(all::add);
      clusters.add(
          new Cluster(
              loc.id,
              loc.type,
              loc.point,
              loc.locationMethod,
              loc.facing,
              loc.rayCount,
              loc.residualM,
              loc.sourceId,
              List.copyOf(all)));
    }
    return List.copyOf(clusters);
  }
}
