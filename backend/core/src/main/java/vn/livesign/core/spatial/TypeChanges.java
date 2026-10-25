package vn.livesign.core.spatial;

import java.time.Instant;
import java.util.*;
import vn.livesign.core.config.CoreConfig;

/** Dấu hiệu (c) luôn cần âm hợp lệ của tấm cũ và loại trừ hai loại đồng xuất hiện. */
public final class TypeChanges {
  private TypeChanges() {}

  public static Map<String, List<Instant>> detect(List<Pipeline.Cluster> clusters, CoreConfig p) {
    var result = new HashMap<String, List<Instant>>();
    for (var old : clusters)
      for (var replacement : clusters) {
        if (old.type().equals(replacement.type())
            || old.facing().degrees() == null
            || replacement.facing().degrees() == null) continue;
        if (Geo.distance(old.point(), replacement.point()) > p.number("geometry.merge_radius_m")
            || Geo.angleDiff(old.facing().degrees(), replacement.facing().degrees())
                > p.number("geometry.merge_max_facing_diff_deg")) continue;
        var oldSeen = old.observations().stream().filter(d -> "seen".equals(d.outcome())).toList();
        var newSeen =
            replacement.observations().stream().filter(d -> "seen".equals(d.outcome())).toList();
        Instant lastOld =
            oldSeen.stream()
                .map(Pipeline.Detection::time)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MAX);
        if (oldSeen.stream()
            .anyMatch(a -> newSeen.stream().anyMatch(b -> a.imageKey().equals(b.imageKey()))))
          continue;
        for (var negative : old.observations())
          if ("not_seen".equals(negative.outcome()) && lastOld.isBefore(negative.time())) {
            newSeen.stream()
                .filter(n -> !n.time().isBefore(negative.time()))
                .map(Pipeline.Detection::time)
                .min(Comparator.naturalOrder())
                .ifPresent(t -> result.computeIfAbsent(old.id(), k -> new ArrayList<>()).add(t));
          }
      }
    return result;
  }
}
