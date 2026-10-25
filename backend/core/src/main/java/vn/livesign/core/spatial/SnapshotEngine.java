package vn.livesign.core.spatial;

import java.time.*;
import java.util.*;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.reliability.*;

/** Dịch vụ và CLI dùng cùng bộ dựng; không phụ thuộc Spring hay SQL. */
public final class SnapshotEngine {
  private SnapshotEngine() {}
  public record Sign(Pipeline.Cluster cluster, SignLifecycle.Result lifecycle) {}
  public record Year(int year, List<Sign> signs) {}
  public static List<Year> build(List<Pipeline.Detection> observations,
      Map<String,List<AdminDecision>> decisions, int year, CoreConfig params, SignCatalog catalog) {
    int first=observations.stream().mapToInt(d->d.time().atZone(ZoneOffset.UTC).getYear()).min().orElse(year);
    var years=new ArrayList<Year>();
    for(int y=Math.min(first,year);y<=year;y++) {
      Instant cutoff=ReliabilityCalculator.cutoffOf(y);
      var clusters=Pipeline.build(observations.stream().filter(d->d.time().isBefore(cutoff)).toList(),params,catalog);
      var changes=TypeChanges.detect(clusters,params);
      var signs=new ArrayList<Sign>();
      for(var cluster:clusters) {
        var evidence=cluster.observations().stream().map(d->new Evidence(d.id(),d.creator(),d.time(),
            Evidence.TimePrecision.valueOf(d.precision().toUpperCase(Locale.ROOT)), Evidence.Outcome.valueOf(d.outcome().toUpperCase(Locale.ROOT)))).toList();
        var lifecycle=SignLifecycle.evaluate(evidence,decisions.getOrDefault(cluster.id(),List.of()),y,params.reliability(),changes.getOrDefault(cluster.id(),List.of()));
        // Không có người chụp: giữ candidate để audit, không tự tạo cặp hay xác nhận.
        if(lifecycle.status()==null) lifecycle=new SignLifecycle.Result(SignStatus.CANDIDATE,null,null,lifecycle.reliability(),List.of());
        signs.add(new Sign(cluster,lifecycle));
      }
      years.add(new Year(y,List.copyOf(signs)));
    }
    return List.copyOf(years);
  }
}
