package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import vn.livesign.app.web.ApiException;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.spatial.*;

@RestController
@RequestMapping("/api/v1")
public class IngestController {
  private static final List<String> COLUMNS =
      List.of(
          "observation_id",
          "source",
          "trip_id",
          "sequence_id",
          "creator_id",
          "captured_at_precision",
          "image_id",
          "target_sign_id",
          "source_sign_id",
          "source_sign_type",
          "sign_type_code",
          "camera_lon",
          "camera_lat",
          "camera_heading_deg",
          "sign_lon",
          "sign_lat",
          "sign_facing_deg",
          "distance_m",
          "off_deg",
          "is_pano",
          "focal",
          "image_width_px",
          "image_height_px",
          "bbox_px",
          "detector_confidence",
          "outcome",
          "in_fov",
          "facing_camera",
          "sharp",
          "unoccluded",
          "visibility_method",
          "model_version",
          "evidence_ref",
          "source_metadata");
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final DbWriter db;
  private final ObjectMapper json;
  private final ObservationValidator validator;
  private final SnapshotService snapshots;
  private final SignCatalog catalog;
  private final CoreConfig config;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  private record Window(long minute, int count) {}

  public IngestController(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      DbWriter db,
      ObjectMapper json,
      ObservationValidator validator,
      SnapshotService snapshots,
      SignCatalog catalog,
      CoreConfig config) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.db = db;
    this.json = json;
    this.validator = validator;
    this.snapshots = snapshots;
    this.catalog = catalog;
    this.config = config;
  }

  @PostMapping("/observations/batch")
  public ResponseEntity<?> batch(
      @RequestBody JsonNode body, @AuthenticationPrincipal Jwt principal) {
    String user = principal.getSubject();
    long minute = Instant.now().getEpochSecond() / 60;
    if ("user".equals(principal.getClaimAsString("role")) && body.path("trip").isObject())
      for (var k : List.of("trace_ref", "trace_manifest_ref", "trace_sha256", "replay_enabled"))
        if (body.path("trip").hasNonNull(k))
          throw error(403, "FORBIDDEN", "Kho trace/ảnh do importer hoặc admin chuẩn bị");
    Window window =
        windows.compute(
            user,
            (k, v) ->
                v == null || v.minute != minute
                    ? new Window(minute, 1)
                    : new Window(minute, v.count + 1));
    if (windows.size() > 10000) windows.entrySet().removeIf(e -> e.getValue().minute < minute - 1);
    if (window.count > 60)
      return ResponseEntity.status(429)
          .header("Retry-After", Long.toString(60 - Instant.now().getEpochSecond() % 60))
          .body(map("error", map("code", "RATE_LIMITED", "message", "Vượt giới hạn nhập lô")));
    envelope(body);
    Map<String, Object> result = tx.execute(status -> store(body, user));
    var runs = snapshots.rebuild(null);
    result.put("snapshot_runs", runs);
    // Trả kết quả gán sau job, không dùng target do client gợi ý làm kết quả cuối.
    @SuppressWarnings("unchecked")
    var items = (List<Map<String, Object>>) result.get("items");
    for (var item : items)
      if (!"rejected".equals(item.get("ingest_status"))) {
        var ids =
            jdbc.queryForList(
                "SELECT target_sign_id FROM observation WHERE observation_id=?",
                item.get("stored_observation_id"));
        if (!ids.isEmpty()) item.put("target_sign_id", ids.getFirst().get("target_sign_id"));
        item.remove("stored_observation_id");
      }
    boolean failed = runs.stream().anyMatch(r -> "failed".equals(r.get("status")));
    if (failed)
      result.put(
          "error",
          map(
              "code",
              "SNAPSHOT_BUILD_FAILED",
              "message",
              "Đã lưu lô; dựng snapshot lỗi. Gửi lại đúng lô để thử dựng lại."));
    return ResponseEntity.status(failed ? 503 : 200).body(result);
  }

  private void envelope(JsonNode b) {
    if (!b.isObject()
        || !"1.0".equals(string(b, "schema_version"))
        || !catalog.classesVersion().equals(string(b, "classes_version"))
        || !b.path("trip").isObject()
        || !b.path("observations").isArray())
      throw error(400, "INVALID_BATCH", "Sai schema/classes/trip/observations");
    var trip = b.get("trip");
    try {
      for (var k : List.of("trip_id", "source", "sequence_id"))
        ObservationValidator.required(trip, k, k.equals("trip_id") ? 160 : 128);
      if (!Set.of("mapillary", "android_replay").contains(string(trip, "source")))
        throw error(400, "INVALID_TRIP", "Nguồn trip sai");
      ObservationValidator.safeRef(string(trip, "trace_ref"));
      ObservationValidator.safeRef(string(trip, "trace_manifest_ref"));
      if (trip.hasNonNull("creator_id")) ObservationValidator.required(trip, "creator_id", 128);
      for (var k : List.of("trace_ref", "trace_manifest_ref"))
        if (trip.hasNonNull(k)) ObservationValidator.required(trip, k, 512);
      if (trip.hasNonNull("trace_sha256"))
        ObservationValidator.require(
            trip.path("trace_sha256").isTextual()
                && trip.path("trace_sha256").asText().matches("[a-f0-9]{64}"),
            "trace_sha256");
      if (trip.hasNonNull("data_kind"))
        ObservationValidator.require(
            Set.of("public_metadata", "illustration")
                .contains(ObservationValidator.required(trip, "data_kind", 32)),
            "data_kind");
      if (trip.hasNonNull("data_revision"))
        ObservationValidator.required(trip, "data_revision", 64);
      if (trip.hasNonNull("trace_schema_version"))
        ObservationValidator.require(
            "1.1".equals(string(trip, "trace_schema_version")), "trace_schema_version");
      if (trip.hasNonNull("replay_enabled"))
        ObservationValidator.require(trip.path("replay_enabled").isBoolean(), "replay_enabled");
      if (trip.path("replay_enabled").asBoolean())
        ObservationValidator.require(
            trip.hasNonNull("trace_ref") && trip.hasNonNull("trace_manifest_ref"),
            "trace_ref/trace_manifest_ref");
    } catch (ApiException e) {
      throw error(400, "INVALID_TRIP", e.getMessage());
    }
    if (b.get("observations").size() > 500)
      throw error(413, "BATCH_TOO_LARGE", "Tối đa 500 dòng/lô");
    if (b.get("observations").isEmpty())
      throw error(400, "INVALID_BATCH", "Lô phải có ít nhất một dòng");
    for (var o : b.get("observations"))
      if (o.isObject())
        for (var k : List.of("trip_id", "source", "sequence_id", "creator_id"))
          if (!Objects.equals(string(o, k), string(trip, k)))
            throw error(400, "TRIP_METADATA_MISMATCH", "Dòng không khớp trip: " + k);
  }

  private Map<String, Object> store(JsonNode b, String user) {
    long
        previous =
            jdbc.queryForObject(
                "SELECT input_seq FROM data_state WHERE id=1 FOR UPDATE", Long.class),
        seq = previous + 1;
    var trip = b.get("trip");
    String tripId = string(trip, "trip_id");
    var trips = jdbc.queryForList("SELECT * FROM trip WHERE trip_id=?", tripId);
    if (trips.isEmpty()) {
      var fields =
          map(
              "trip_id",
              tripId,
              "source",
              string(trip, "source"),
              "sequence_id",
              string(trip, "sequence_id"),
              "creator_id",
              string(trip, "creator_id"),
              "user_id",
              user,
              "trace_ref",
              string(trip, "trace_ref"));
      for (var key :
          List.of(
              "trace_manifest_ref",
              "trace_schema_version",
              "trace_sha256",
              "data_kind",
              "data_revision")) if (trip.hasNonNull(key)) fields.put(key, string(trip, key));
      fields.put("replay_enabled", trip.path("replay_enabled").asBoolean(false));
      fields.putIfAbsent("data_kind", "public_metadata");
      db.insert("trip", fields);
    } else {
      for (var key :
          List.of(
              "trace_ref",
              "trace_manifest_ref",
              "trace_schema_version",
              "trace_sha256",
              "data_kind",
              "data_revision",
              "replay_enabled"))
        if (trip.hasNonNull(key)) {
          Object proposed =
              key.equals("replay_enabled") ? trip.get(key).asBoolean() : string(trip, key);
          if (!Objects.equals(trips.getFirst().get(key), proposed))
            throw error(
                409,
                "TRIP_METADATA_CONFLICT",
                "Metadata kho của trip đã được đóng; dùng trip/version mới");
        }
      for (var key : List.of("source", "sequence_id", "creator_id"))
        if (!Objects.equals(trips.getFirst().get(key), string(trip, key)))
          throw error(409, "TRIP_METADATA_CONFLICT", "Trip đã có metadata khác");
      // Trip người khác không thể bị user thay nguồn hoặc metadata kho ảnh.
      if (!Objects.equals(trips.getFirst().get("user_id"), user)) {
        String role =
            jdbc.queryForObject("SELECT role FROM `user` WHERE user_id=?", String.class, user);
        if (!Set.of("admin", "importer").contains(role))
          throw error(404, "TRIP_NOT_FOUND", "Không có trip được phép nhập");
      }
    }
    int accepted = 0, metadata = 0, duplicates = 0, rejected = 0;
    var items = new ArrayList<Map<String, Object>>();
    for (var raw : b.get("observations")) {
      String id = string(raw, "observation_id");
      try {
        String ingest = validator.validate(raw);
        var o = ((ObjectNode) raw).deepCopy();
        Instant captured = validator.time(o);
        o.put("captured_at", captured.toString());
        var payload =
            map(
                "schema_version",
                string(b, "schema_version"),
                "classes_version",
                string(b, "classes_version"),
                "observation",
                o);
        String payloadHash = hash(json, payload);
        var sameId =
            jdbc.queryForList(
                "SELECT observation_id,payload_sha256 FROM observation WHERE observation_id=?", id);
        if (!sameId.isEmpty()) {
          if (!payloadHash.equals(sameId.getFirst().get("payload_sha256")))
            throw error(409, "OBSERVATION_ID_CONFLICT", "Cùng ID có nội dung khác");
          items.add(
              map(
                  "observation_id",
                  id,
                  "ingest_status",
                  "duplicate",
                  "stored_observation_id",
                  id,
                  "target_sign_id",
                  null,
                  "error",
                  null));
          duplicates++;
          continue;
        }
        var logical =
            map(
                "image_id",
                string(o, "image_id"),
                "sign_type_code",
                string(o, "sign_type_code"),
                "model_version",
                string(o, "model_version"),
                "bbox_px",
                o.get("bbox_px"),
                "outcome",
                string(o, "outcome"),
                "target_sign_id",
                string(o, "target_sign_id"));
        if ("seen".equals(string(o, "outcome"))) logical.remove("target_sign_id");
        if ("unknown".equals(string(o, "outcome"))) logical.put("observation_id", id);
        String logicalHash = hash(json, logical);
        var logicalRows =
            jdbc.queryForList(
                "SELECT observation_id,creator_id,captured_at,sequence_id FROM observation WHERE"
                    + " logical_sha256=?",
                logicalHash);
        if (!logicalRows.isEmpty()) {
          var saved = logicalRows.getFirst();
          if (!Objects.equals(saved.get("creator_id"), string(o, "creator_id"))
              || !Objects.equals(saved.get("sequence_id"), string(o, "sequence_id"))
              || !instant(saved.get("captured_at")).equals(captured))
            throw error(409, "SOURCE_METADATA_CONFLICT", "Cùng ảnh/model có metadata gốc khác");
          items.add(
              map(
                  "observation_id",
                  id,
                  "ingest_status",
                  "duplicate",
                  "stored_observation_id",
                  saved.get("observation_id"),
                  "target_sign_id",
                  null,
                  "error",
                  null));
          duplicates++;
          continue;
        }
        validateTarget(o);
        var values =
            map(
                "schema_version",
                string(b, "schema_version"),
                "classes_version",
                string(b, "classes_version"),
                "payload_sha256",
                payloadHash,
                "captured_at",
                timestamp(captured),
                "ingest_status",
                ingest,
                "uploaded_by",
                user,
                "input_seq",
                seq,
                "payload_json",
                write(json, o),
                "logical_sha256",
                logicalHash);
        for (var key : COLUMNS) {
          var node = o.get(key);
          Object value = null;
          if (node != null && !node.isNull())
            value =
                node.isContainerNode()
                    ? write(json, node)
                    : node.isBoolean()
                        ? node.asBoolean()
                        : node.isNumber() ? node.numberValue() : node.asText();
          values.put(key, value);
        }
        db.insert("observation", values);
        items.add(
            map(
                "observation_id",
                id,
                "ingest_status",
                ingest,
                "stored_observation_id",
                id,
                "target_sign_id",
                null,
                "error",
                null));
        if ("accepted".equals(ingest)) accepted++;
        else metadata++;
      } catch (ApiException e) {
        rejected++;
        items.add(
            map(
                "observation_id",
                id,
                "ingest_status",
                "rejected",
                "target_sign_id",
                null,
                "error",
                map(
                    "code",
                    e.code(),
                    "status",
                    e.status().value(),
                    "message",
                    e.getMessage(),
                    "fields",
                    e.fields())));
      }
    }
    if (accepted + metadata > 0) {
      jdbc.update("UPDATE data_state SET input_seq=? WHERE id=1", seq);
      jdbc.update(
          "UPDATE trip SET started_at=(SELECT MIN(captured_at) FROM observation WHERE"
              + " trip_id=?),ended_at=(SELECT MAX(captured_at) FROM observation WHERE trip_id=?)"
              + " WHERE trip_id=?",
          tripId,
          tripId,
          tripId);
    }
    return map(
        "accepted",
        accepted,
        "metadata_only",
        metadata,
        "duplicates",
        duplicates,
        "rejected",
        rejected,
        "items",
        items);
  }

  private void validateTarget(JsonNode o) {
    String target = string(o, "target_sign_id");
    if (target == null) return;
    var sign = jdbc.queryForList("SELECT sign_type_code FROM sign WHERE sign_id=?", target);
    if (sign.isEmpty()
        || !Objects.equals(sign.getFirst().get("sign_type_code"), string(o, "sign_type_code")))
      throw error(422, "INVALID_TARGET_SIGN", "Biển đích không tồn tại hoặc khác loại");
    if (!"not_seen".equals(string(o, "outcome"))) return;
    String run = o.at("/source_metadata/provenance/snapshot_run_id").asText();
    int year = validator.time(o).atZone(ZoneOffset.UTC).getYear();
    snapshots.resolve(year, run);
    var rows =
        jdbc.queryForList(
            "SELECT ST_Longitude(location) lon,ST_Latitude(location)"
                + " lat,sign_facing_deg,facing_source,location_method FROM sign_snapshot WHERE"
                + " run_id=? AND sign_id=?",
            run,
            target);
    ObservationValidator.require(!rows.isEmpty(), "target trong snapshot");
    var s = rows.getFirst();
    ObservationValidator.require(
        "aggregated".equals(s.get("facing_source"))
            && !"camera_proxy".equals(s.get("location_method")),
        "hướng/vị trí snapshot");
    var point =
        new Geo.Point(((Number) s.get("lon")).doubleValue(), ((Number) s.get("lat")).doubleValue());
    var camera = new Geo.Point(number(o, "camera_lon"), number(o, "camera_lat"));
    double face = ((Number) s.get("sign_facing_deg")).doubleValue(),
        distance = Geo.distance(camera, point),
        off = Geo.signed(Geo.bearing(camera, point) - number(o, "camera_heading_deg"));
    ObservationValidator.require(
        Geo.distance(point, new Geo.Point(number(o, "sign_lon"), number(o, "sign_lat"))) < .01
            && Geo.angleDiff(face, number(o, "sign_facing_deg")) < .001,
        "tọa độ/hướng khớp snapshot");
    ObservationValidator.require(
        distance <= config.number("geometry.visibility_max_distance_m")
            && Math.abs(distance - number(o, "distance_m")) <= .5,
        "distance_m");
    ObservationValidator.require(
        Positioning.inFrame(
            off,
            o.path("image_width_px").asInt(),
            o.path("image_height_px").asInt(),
            o.path("is_pano").asBoolean(),
            number(o, "focal"),
            config.number("geometry.perspective_frame_keep")),
        "in_fov");
    ObservationValidator.require(
        Geo.angleDiff(face, Geo.bearing(point, camera))
            <= config.number("visibility.max_facing_diff_deg"),
        "facing_camera");
  }
}
