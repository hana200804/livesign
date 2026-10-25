package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.reliability.*;
import vn.livesign.core.spatial.*;

@Service
public class SnapshotService {
  private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final DbWriter db;
  private final ObjectMapper json;
  private final CoreConfig config;
  private final SignCatalog catalog;

  public SnapshotService(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      DbWriter db,
      ObjectMapper json,
      CoreConfig config,
      SignCatalog catalog) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.db = db;
    this.json = json;
    this.config = config;
    this.catalog = catalog;
  }

  public void validateYear(int year) {
    if (!(config.value("evaluation.years_T") instanceof List<?> years) || !years.contains(year))
      throw error(422, "INVALID_EVALUATION_YEAR", "Năm đánh giá không được hỗ trợ");
  }

  public List<Integer> years() {
    return ((List<?>) config.value("evaluation.years_T"))
        .stream().map(n -> ((Number) n).intValue()).toList();
  }

  private String configurationHash() {
    return hash(json, map("params_sha256", config.sha256(), "catalog", catalog));
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    jdbc.update(
        "UPDATE snapshot_run SET status='failed',error_code='BUILD_INTERRUPTED' WHERE"
            + " status='building'");
    if (jdbc.queryForObject("SELECT COUNT(*) FROM observation", Long.class) > 0) rebuild(null);
  }

  public synchronized List<Map<String, Object>> rebuild(Integer selectedYear) {
    if (selectedYear != null) validateYear(selectedYear);
    var results = new ArrayList<Map<String, Object>>();
    for (int year : selectedYear == null ? years() : List.of(selectedYear)) {
      String run = "run:" + UUID.randomUUID();
      try {
        long seq = jdbc.queryForObject("SELECT input_seq FROM data_state WHERE id=1", Long.class);
        var existing = current(year);
        if (existing != null
            && ((Number) existing.get("input_seq")).longValue() == seq
            && configurationHash().equals(existing.get("config_sha256"))) {
          results.add(
              map("evaluation_year", year, "status", "ready", "run_id", existing.get("run_id")));
          continue;
        }
        db.insert(
            "snapshot_run",
            map(
                "run_id",
                run,
                "evaluation_year",
                year,
                "cutoff_exclusive_utc",
                timestamp(ReliabilityCalculator.cutoffOf(year)),
                "params_version",
                config.version(),
                "config_sha256",
                configurationHash(),
                "classes_version",
                catalog.classesVersion(),
                "data_revision",
                "pending",
                "input_seq",
                seq,
                "core_version",
                "0.2.0",
                "status",
                "building",
                "config_yaml",
                new String(config.bytes(), StandardCharsets.UTF_8),
                "classes_json",
                write(json, catalog)));
        Map<String, Object> built = tx.execute(status -> build(run, year));
        results.add(built);
      } catch (Exception e) {
        jdbc.update(
            "UPDATE snapshot_run SET status='failed',error_code='SNAPSHOT_BUILD_FAILED' WHERE"
                + " run_id=? AND status='building'",
            run);
        log.error("Snapshot build failed: year={}, run={}", year, run, e);
        results.add(
            map(
                "evaluation_year",
                year,
                "status",
                "failed",
                "run_id",
                run,
                "error_code",
                "SNAPSHOT_BUILD_FAILED"));
      }
    }
    return results;
  }

  private Map<String, Object> current(int year) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM snapshot_run WHERE evaluation_year=? AND status='ready' ORDER BY"
                + " input_seq DESC,ready_at DESC,run_id DESC LIMIT 1",
            year);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Map<String, Object> resolve(int year, String run) {
    validateYear(year);
    if (run == null) {
      var r = current(year);
      if (r == null) throw error(503, "SNAPSHOT_NOT_READY", "Chưa có snapshot sẵn sàng");
      return r;
    }
    var rows = jdbc.queryForList("SELECT * FROM snapshot_run WHERE run_id=?", run);
    if (rows.isEmpty()) throw error(410, "SNAPSHOT_RUN_GONE", "Snapshot không còn được lưu");
    var r = rows.getFirst();
    if (((Number) r.get("evaluation_year")).intValue() != year)
      throw error(422, "SNAPSHOT_YEAR_MISMATCH", "Snapshot thuộc năm khác");
    if (!Set.of("ready", "superseded").contains(r.get("status")))
      throw error(503, "SNAPSHOT_NOT_READY", "Snapshot chưa sẵn sàng");
    return r;
  }

  public CoreConfig configFor(Map<String, Object> run) {
    return new CoreConfig(
        Objects.toString(run.get("config_yaml")).getBytes(StandardCharsets.UTF_8));
  }

  private Map<String, Object> build(String run, int year) {
    long seq =
        jdbc.queryForObject("SELECT input_seq FROM data_state WHERE id=1 FOR UPDATE", Long.class);
    var rows =
        jdbc.queryForList(
            "SELECT * FROM observation WHERE input_seq<=? AND captured_at<? AND"
                + " ingest_status='accepted' ORDER BY captured_at,image_id,observation_id",
            seq,
            timestamp(ReliabilityCalculator.cutoffOf(year)));
    var detections =
        rows.stream().filter(r -> r.get("payload_json") != null).map(this::detection).toList();
    var decisionRows =
        jdbc.queryForList(
            "SELECT * FROM sign_history WHERE event_type='admin_decision' AND input_seq<=? AND"
                + " effective_at<? AND recorded_at<? ORDER BY recorded_at,history_id",
            seq,
            timestamp(ReliabilityCalculator.cutoffOf(year)),
            timestamp(ReliabilityCalculator.cutoffOf(year)));
    var decisions = new HashMap<String, List<AdminDecision>>();
    for (var r : decisionRows) {
      var after = parse(json, (String) r.get("after_snapshot"));
      String decision = after.path("decision").asText();
      if (decision.isBlank()) continue;
      decisions
          .computeIfAbsent((String) r.get("sign_id"), k -> new ArrayList<>())
          .add(
              new AdminDecision(
                  AdminDecision.Decision.valueOf(decision.toUpperCase(Locale.ROOT)),
                  instant(r.get("effective_at")),
                  instant(r.get("recorded_at")),
                  string(after, "replacement_sign_id")));
    }
    var built = SnapshotEngine.build(detections, decisions, year, config, catalog);
    var signs = built.getLast().signs();
    for (var sign : signs) {
      var c = sign.cluster();
      if (jdbc.queryForObject("SELECT COUNT(*) FROM sign WHERE sign_id=?", Integer.class, c.id())
          == 0)
        db.insert(
            "sign",
            map(
                "sign_id",
                c.id(),
                "sign_type_code",
                c.type(),
                "location",
                c.point(),
                "sign_facing_deg",
                c.facing().degrees(),
                "conflict_reasons",
                "[]",
                "params_version",
                config.version()));
    }
    for (var sign : signs) {
      var c = sign.cluster();
      var state = sign.lifecycle();
      var r = state.reliability();
      int version =
          jdbc.queryForObject("SELECT version FROM sign WHERE sign_id=?", Integer.class, c.id());
      String road =
          jdbc.queryForObject("SELECT road_ref FROM sign WHERE sign_id=?", String.class, c.id());
      db.insert(
          "sign_snapshot",
          map(
              "run_id",
              run,
              "sign_id",
              c.id(),
              "sign_type_code",
              c.type(),
              "location",
              c.point(),
              "location_method",
              c.locationMethod(),
              "sign_facing_deg",
              c.facing().degrees(),
              "facing_source",
              c.facing().source(),
              "ray_count",
              c.rayCount(),
              "ray_residual_m",
              c.residualM(),
              "source_sign_id",
              c.sourceId(),
              "road_ref",
              road,
              "status",
              state.status().name().toLowerCase(Locale.ROOT),
              "last_confirmation_source",
              state.lastConfirmationSource() == null
                  ? null
                  : state.lastConfirmationSource().name().toLowerCase(Locale.ROOT),
              "replacement_sign_id",
              state.replacementSignId(),
              "alpha",
              r.alpha(),
              "beta",
              r.beta(),
              "agreement_score",
              r.agreementScore(),
              "positive_pair_count",
              r.positivePairCount(),
              "negative_pair_count",
              r.negativePairCount(),
              "last_seen_at",
              timestamp(r.lastSeenAt()),
              "last_evidence_at",
              timestamp(r.lastEvidenceAt()),
              "conflict_reasons",
              write(json, r.conflictReasons()),
              "sign_version",
              version));
      for (var o : c.observations()) {
        db.insert(
            "sign_snapshot_observation",
            map("run_id", run, "sign_id", c.id(), "observation_id", o.id()));
        jdbc.update(
            "UPDATE observation SET target_sign_id=? WHERE observation_id=? AND target_sign_id IS"
                + " NULL",
            c.id(),
            o.id());
      }
      if (year == Collections.max(years()))
        jdbc.update(
            "UPDATE sign SET"
                + " location=ST_GeomFromText(?,4326,'axis-order=long-lat'),sign_facing_deg=?,status=?,replacement_sign_id=?,as_of_year=?,alpha=?,beta=?,agreement_score=?,positive_pair_count=?,negative_pair_count=?,last_seen_at=?,last_evidence_at=?,conflict_reasons=?,params_version=?,version=version+1"
                + " WHERE sign_id=? AND (as_of_year IS NULL OR as_of_year<=?)",
            "POINT(" + c.point().lon() + " " + c.point().lat() + ")",
            c.facing().degrees(),
            state.status().name().toLowerCase(Locale.ROOT),
            state.replacementSignId(),
            year,
            r.alpha(),
            r.beta(),
            r.agreementScore(),
            r.positivePairCount(),
            r.negativePairCount(),
            timestamp(r.lastSeenAt()),
            timestamp(r.lastEvidenceAt()),
            write(json, r.conflictReasons()),
            config.version(),
            c.id(),
            year);
      if (state.status() == SignStatus.SUSPECTED_CHANGE) openReview(c, r, seq);
    }
    var previous = new HashMap<String, String>();
    for (var y : built)
      for (var sign : y.signs()) {
        var c = sign.cluster();
        String state = sign.lifecycle().status().name().toLowerCase(Locale.ROOT),
            old = previous.put(c.id(), state);
        db.insert(
            "sign_history",
            map(
                "history_id",
                "history:" + UUID.randomUUID(),
                "sign_id",
                c.id(),
                "event_type",
                Objects.equals(old, state) ? "score_updated" : "state_changed",
                "old_status",
                old,
                "new_status",
                state,
                "old_sign_type_code",
                old == null ? null : c.type(),
                "new_sign_type_code",
                c.type(),
                "after_snapshot",
                write(
                    json,
                    map(
                        "evaluation_year",
                        y.year(),
                        "sign_lon",
                        c.point().lon(),
                        "sign_lat",
                        c.point().lat(),
                        "status",
                        state,
                        "reliability",
                        sign.lifecycle().reliability())),
                "effective_at",
                timestamp(ReliabilityCalculator.cutoffOf(y.year()).minusMillis(1)),
                "reason",
                "Suy luận từ bằng chứng trước cutoff; dựng hồi cứu",
                "input_seq",
                seq,
                "run_id",
                run));
      }
    String revision =
        hash(
            json,
            map(
                "observations",
                rows.stream().map(r -> r.get("payload_sha256")).toList(),
                "decisions",
                decisionRows.stream().map(r -> r.get("history_id")).toList()));
    // data_state giữ khóa tới commit: không thể công bố run cũ hơn dữ liệu đang đọc.
    jdbc.update(
        "UPDATE snapshot_run SET status='superseded' WHERE evaluation_year=? AND status='ready' AND"
            + " input_seq<=?",
        year,
        seq);
    jdbc.update(
        "UPDATE snapshot_run SET"
            + " status='ready',input_seq=?,data_revision=?,sign_count=?,ready_at=CURRENT_TIMESTAMP(3)"
            + " WHERE run_id=?",
        seq,
        revision,
        signs.size(),
        run);
    return map("evaluation_year", year, "status", "ready", "run_id", run);
  }

  private void openReview(Pipeline.Cluster c, Reliability r, long seq) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM review WHERE sign_id=? AND status='pending'",
            Integer.class,
            c.id())
        > 0) return;
    var decided =
        jdbc.queryForList(
            "SELECT effective_at FROM review WHERE sign_id=? AND status='decided' ORDER BY"
                + " decided_at DESC LIMIT 1",
            c.id());
    if (!decided.isEmpty()
        && c.observations().stream()
            .noneMatch(
                o ->
                    !"unknown".equals(o.outcome())
                        && o.time().isAfter(instant(decided.getFirst().get("effective_at")))))
      return;
    var before =
        c.observations().stream()
            .filter(o -> "seen".equals(o.outcome()))
            .max(Comparator.comparing(Pipeline.Detection::time))
            .orElse(null);
    var after =
        c.observations().stream()
            .filter(o -> "not_seen".equals(o.outcome()))
            .max(Comparator.comparing(Pipeline.Detection::time))
            .orElse(null);
    db.insert(
        "review",
        map(
            "review_id",
            "review:" + UUID.randomUUID(),
            "sign_id",
            c.id(),
            "conflict_reasons",
            write(json, r.conflictReasons()),
            "before_observation_id",
            before == null ? null : before.id(),
            "after_observation_id",
            after == null ? null : after.id(),
            "input_seq",
            seq));
  }

  private Pipeline.Detection detection(Map<String, Object> row) {
    var o = parse(json, (String) row.get("payload_json"));
    var features = new ArrayList<Pipeline.Feature>();
    var candidates = o.at("/source_metadata/positioning/feature_candidates");
    if (candidates.isArray())
      for (var f : candidates)
        features.add(
            new Pipeline.Feature(
                string(f, "source_sign_id"),
                string(f, "source_sign_type"),
                new Geo.Point(f.path("lon").asDouble(), f.path("lat").asDouble())));
    Double off = number(o, "off_deg"), height = null;
    if (o.path("bbox_px").isArray()) {
      var b = o.get("bbox_px");
      height = b.get(3).asDouble() - b.get(1).asDouble();
      if (o.hasNonNull("is_pano"))
        off =
            Positioning.boxAngle(
                (b.get(0).asDouble() + b.get(2).asDouble()) / 2,
                o.path("image_width_px").asInt(),
                o.path("image_height_px").asInt(),
                o.path("is_pano").asBoolean(),
                number(o, "focal"));
    }
    Geo.Point camera =
        o.hasNonNull("camera_lon")
            ? new Geo.Point(o.get("camera_lon").asDouble(), o.get("camera_lat").asDouble())
            : null;
    return new Pipeline.Detection(
        (String) row.get("observation_id"),
        (String) row.get("sequence_id"),
        (String) row.get("creator_id"),
        instant(row.get("captured_at")),
        (String) row.get("captured_at_precision"),
        (String) row.get("image_id"),
        (String) row.get("sign_type_code"),
        camera,
        number(o, "camera_heading_deg"),
        off,
        height,
        string(o, "source_sign_id"),
        features,
        (String) row.get("target_sign_id"),
        (String) row.get("outcome"));
  }
}
