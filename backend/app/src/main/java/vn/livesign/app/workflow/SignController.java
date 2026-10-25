package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import vn.livesign.core.spatial.*;

@RestController
@RequestMapping("/api/v1/signs")
public class SignController {
  private final SnapshotService snapshots;
  private final SignQueries queries;
  private final Cursors cursors;
  private final JdbcTemplate jdbc;

  public SignController(
      SnapshotService snapshots, SignQueries queries, Cursors cursors, JdbcTemplate jdbc) {
    this.snapshots = snapshots;
    this.queries = queries;
    this.cursors = cursors;
    this.jdbc = jdbc;
  }

  @GetMapping
  public Map<String, Object> bbox(
      @RequestParam(name = "evaluation_year") int year,
      @RequestParam String bbox,
      @RequestParam(required = false) String status,
      @RequestParam(name = "snapshot_run_id", required = false) String runId,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(required = false) String cursor) {
    snapshots.validateYear(year);
    double[] bounds;
    try {
      bounds = Arrays.stream(bbox.split(",", -1)).mapToDouble(Double::parseDouble).toArray();
      if (bounds.length != 4
          || Arrays.stream(bounds).anyMatch(x -> !Double.isFinite(x))
          || bounds[0] >= bounds[2]
          || bounds[1] >= bounds[3]) throw new IllegalArgumentException();
      new Geo.Point(bounds[0], bounds[1]);
      new Geo.Point(bounds[2], bounds[3]);
    } catch (Exception e) {
      throw error(400, "INVALID_BBOX", "bbox phải là west,south,east,north hợp lệ");
    }
    if (status != null
        && !Set.of(
                "candidate",
                "confirmed_auto",
                "confirmed_manual",
                "unclear",
                "suspected_change",
                "removed",
                "replaced")
            .contains(status)) throw error(400, "INVALID_STATUS", "Trạng thái sai");
    var run = snapshots.resolve(year, runId);
    String id = (String) run.get("run_id");
    String polygon =
        "POLYGON(("
            + bounds[0]
            + " "
            + bounds[1]
            + ","
            + bounds[2]
            + " "
            + bounds[1]
            + ","
            + bounds[2]
            + " "
            + bounds[3]
            + ","
            + bounds[0]
            + " "
            + bounds[3]
            + ","
            + bounds[0]
            + " "
            + bounds[1]
            + "))";
    var rows =
        jdbc.queryForList(
            "SELECT s.*,ST_Longitude(location) lon,ST_Latitude(location) lat FROM sign_snapshot s"
                + " WHERE run_id=? AND"
                + " MBRIntersects(ST_GeomFromText(?,4326,'axis-order=long-lat'),location) ORDER BY"
                + " sign_id",
            id,
            polygon);
    var items =
        rows.stream()
            .filter(r -> status == null || status.equals(r.get("status")))
            .map(r -> queries.item(r, year, null))
            .toList();
    var response = queries.meta(run);
    response.putAll(
        cursors.page(
            items,
            limit,
            cursor,
            map("kind", "bbox", "run", id, "bbox", bbox, "status", status, "limit", limit)));
    return response;
  }

  @GetMapping("/{signId}")
  public Map<String, Object> detail(
      @PathVariable String signId,
      @RequestParam(name = "evaluation_year") int year,
      @RequestParam(name = "snapshot_run_id", required = false) String runId) {
    var run = snapshots.resolve(year, runId);
    var item = queries.item(queries.row((String) run.get("run_id"), signId), year, null);
    item.putAll(queries.meta(run));
    item.put(
        "evidence_available",
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM sign_snapshot_observation s JOIN observation o ON"
                    + " o.observation_id=s.observation_id WHERE s.run_id=? AND s.sign_id=? AND"
                    + " o.evidence_ref IS NOT NULL",
                Integer.class,
                run.get("run_id"),
                signId)
            > 0);
    item.put(
        "history_url",
        "/api/v1/signs/"
            + java.net.URLEncoder.encode(signId, java.nio.charset.StandardCharsets.UTF_8)
            + "/history?evaluation_year="
            + year
            + "&snapshot_run_id="
            + run.get("run_id"));
    return item;
  }

  @GetMapping("/{signId}/history")
  public Map<String, Object> history(
      @PathVariable String signId,
      @RequestParam(name = "evaluation_year") int year,
      @RequestParam(name = "snapshot_run_id", required = false) String runId,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(required = false) String cursor) {
    var run = snapshots.resolve(year, runId);
    queries.row((String) run.get("run_id"), signId);
    var cutoff = vn.livesign.core.reliability.ReliabilityCalculator.cutoffOf(year);
    var rows =
        jdbc.queryForList(
            "SELECT"
                + " history_id,sign_id,event_type,old_status,new_status,old_sign_type_code,new_sign_type_code,effective_at,recorded_at"
                + " FROM sign_history WHERE sign_id=? AND input_seq<=? AND (run_id=? OR"
                + " (event_type='admin_decision' AND effective_at<? AND recorded_at<?)) ORDER BY"
                + " effective_at,recorded_at,history_id",
            signId,
            run.get("input_seq"),
            run.get("run_id"),
            timestamp(cutoff),
            timestamp(cutoff));
    rows.forEach(
        r -> {
          r.put("effective_at", instant(r.get("effective_at")));
          r.put("recorded_at", instant(r.get("recorded_at")));
          r.put(
              "reason",
              "admin_decision".equals(r.get("event_type"))
                  ? "Quyết định quản trị"
                  : "Suy luận hồi cứu từ bằng chứng trước cutoff");
        });
    var result = queries.meta(run);
    result.putAll(
        cursors.page(
            rows,
            limit,
            cursor,
            map("kind", "history", "sign", signId, "run", run.get("run_id"), "limit", limit)));
    return result;
  }

  @GetMapping("/ahead")
  public Map<String, Object> ahead(
      @RequestParam(name = "evaluation_year") int year,
      @RequestParam double lon,
      @RequestParam double lat,
      @RequestParam(name = "heading_deg") double heading,
      @RequestParam(name = "speed_mps") double speed,
      @RequestParam(name = "vehicle_type") String vehicle,
      @RequestParam(name = "road_ref", required = false) String road,
      @RequestParam(name = "snapshot_run_id", required = false) String runId) {
    queries.vehicle(vehicle);
    var run = snapshots.resolve(year, runId);
    var config = snapshots.configFor(run);
    Geo.Point camera;
    try {
      camera = new Geo.Point(lon, lat);
    } catch (Exception e) {
      throw error(400, "INVALID_COORDINATES", "Tọa độ sai");
    }
    if (!Double.isFinite(speed)
        || speed < 0
        || !Double.isFinite(heading)
        || heading < 0
        || heading >= 360) throw error(400, "INVALID_MOTION", "Hướng/tốc độ sai");
    var all = queries.rows((String) run.get("run_id")).stream().map(queries::aheadSign).toList();
    var matches =
        Ahead.select(
            all,
            camera,
            heading,
            speed,
            vehicle,
            road,
            config.number("geometry.warning_min_lead_s"),
            config.number("geometry.warning_latency_buffer_s"),
            config.number("geometry.ahead_max_bearing_diff_deg"),
            config.number("geometry.ahead_max_facing_diff_deg"));
    var items =
        matches.stream()
            .map(
                m -> {
                  var s = m.sign();
                  return map(
                      "sign_id",
                      s.id(),
                      "sign_type_code",
                      s.type(),
                      "sign_lon",
                      s.point().lon(),
                      "sign_lat",
                      s.point().lat(),
                      "sign_facing_deg",
                      s.facing(),
                      "status",
                      s.status(),
                      "distance_m",
                      m.distanceM(),
                      "lead_time_s",
                      m.leadTimeS(),
                      "warning_label",
                      Ahead.warningLabel(s.status()));
                })
            .toList();
    var response = queries.meta(run);
    response.put(
        "road_match_status",
        road != null && all.stream().anyMatch(s -> road.equals(s.roadRef()))
            ? "matched"
            : "unverified");
    response.put(
        "warning_distance_m",
        speed
            * (config.number("geometry.warning_min_lead_s")
                + config.number("geometry.warning_latency_buffer_s")));
    response.put("items", items);
    response.put("next_cursor", null);
    return response;
  }
}
