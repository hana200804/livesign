package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.spatial.*;

@Service
public class SignQueries {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final SignCatalog catalog;
  private final Map<String, SignCatalog> catalogs = new java.util.concurrent.ConcurrentHashMap<>();

  public SignQueries(JdbcTemplate jdbc, ObjectMapper json, SignCatalog catalog) {
    this.jdbc = jdbc;
    this.json = json;
    this.catalog = catalog;
  }

  public List<Map<String, Object>> rows(String run) {
    return jdbc.queryForList(
        "SELECT s.*,ST_Longitude(location) lon,ST_Latitude(location) lat FROM sign_snapshot s WHERE"
            + " run_id=? ORDER BY sign_id",
        run);
  }

  public Map<String, Object> row(String run, String sign) {
    var rows =
        jdbc.queryForList(
            "SELECT s.*,ST_Longitude(location) lon,ST_Latitude(location) lat FROM sign_snapshot s"
                + " WHERE run_id=? AND sign_id=?",
            run,
            sign);
    if (rows.isEmpty()) throw error(404, "SIGN_NOT_FOUND", "Không có biển trong snapshot");
    return rows.getFirst();
  }

  public Ahead.Sign aheadSign(Map<String, Object> r) {
    String run = (String) r.get("run_id");
    SignCatalog frozen =
        catalogs.computeIfAbsent(
            run,
            k -> {
              String data =
                  jdbc.queryForObject(
                      "SELECT classes_json FROM snapshot_run WHERE run_id=?", String.class, k);
              try {
                return data == null ? catalog : json.readValue(data, SignCatalog.class);
              } catch (Exception e) {
                throw new IllegalStateException("Danh mục run không hợp lệ", e);
              }
            });
    return new Ahead.Sign(
        (String) r.get("sign_id"),
        (String) r.get("sign_type_code"),
        new Geo.Point(((Number) r.get("lon")).doubleValue(), ((Number) r.get("lat")).doubleValue()),
        r.get("sign_facing_deg") == null ? null : ((Number) r.get("sign_facing_deg")).doubleValue(),
        (String) r.get("facing_source"),
        (String) r.get("location_method"),
        (String) r.get("status"),
        (String) r.get("road_ref"),
        frozen.byCode((String) r.get("sign_type_code")).orElseThrow().vehicleTypes());
  }

  public Map<String, Object> item(Map<String, Object> r, int year, String vehicle) {
    var s = aheadSign(r);
    var lastSeen = instant(r.get("last_seen_at"));
    var lastEvidence = instant(r.get("last_evidence_at"));
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
        "location_method",
        s.locationMethod(),
        "facing_source",
        s.facingSource(),
        "ray_count",
        r.get("ray_count"),
        "ray_residual_m",
        r.get("ray_residual_m"),
        "source_sign_id",
        r.get("source_sign_id"),
        "road_ref",
        s.roadRef(),
        "status",
        s.status(),
        "version",
        r.get("sign_version"),
        "replacement_sign_id",
        r.get("replacement_sign_id"),
        "applies_to_vehicle",
        vehicle == null ? null : s.vehicles().contains(vehicle),
        "alertable",
        vehicle == null
            ? s.alertable("car") || s.alertable("truck") || s.alertable("motorcycle")
            : s.alertable(vehicle),
        "warning_label",
        Ahead.warningLabel(s.status()),
        "reliability",
        map(
            "alpha",
            r.get("alpha"),
            "beta",
            r.get("beta"),
            "agreement_score",
            r.get("agreement_score"),
            "positive_pair_count",
            r.get("positive_pair_count"),
            "negative_pair_count",
            r.get("negative_pair_count"),
            "last_seen_at",
            lastSeen,
            "positive_age_years",
            lastSeen == null ? null : year - lastSeen.atZone(ZoneOffset.UTC).getYear(),
            "last_evidence_at",
            lastEvidence,
            "evidence_age_years",
            lastEvidence == null ? null : year - lastEvidence.atZone(ZoneOffset.UTC).getYear(),
            "conflict_reasons",
            parse(json, (String) r.get("conflict_reasons"))));
  }

  public Map<String, Object> meta(Map<String, Object> run) {
    return map(
        "evaluation_year",
        run.get("evaluation_year"),
        "snapshot_run_id",
        run.get("run_id"),
        "classes_version",
        run.get("classes_version"),
        "params_version",
        run.get("params_version"),
        "config_sha256",
        run.get("config_sha256"),
        "data_revision",
        run.get("data_revision"));
  }

  public void vehicle(String vehicle) {
    if (!catalog.vehicleTypes().contains(vehicle))
      throw error(422, "INVALID_VEHICLE_TYPE", "Loại xe không hợp lệ");
  }
}
