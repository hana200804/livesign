package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.reliability.ReliabilityCalculator;
import vn.livesign.core.spatial.Geo;

@Service
public class TripService {
  public record Trace(
      Map<String, Object> trip,
      JsonNode manifest,
      List<ObjectNode> points,
      byte[] ndjson,
      Map<String, Object> representation) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final SafeStorage storage;
  private final CoreConfig config;
  private final long maxBytes;
  private final DemoMode mode;

  public TripService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      SafeStorage storage,
      CoreConfig config,
      @Value("${livesign.trace-max-bytes:67108864}") long maxBytes,
      DemoMode mode) {
    this.jdbc = jdbc;
    this.json = json;
    this.storage = storage;
    this.config = config;
    this.maxBytes = maxBytes;
    this.mode = mode;
  }

  public List<Map<String, Object>> permitted(Jwt principal) {
    String role = principal.getClaimAsString("role");
    return "admin".equals(role) || "importer".equals(role)
        ? jdbc.queryForList("SELECT * FROM trip ORDER BY trip_id")
        : jdbc.queryForList(
            "SELECT * FROM trip WHERE user_id=? OR (source='mapillary' AND"
                + " data_kind='public_metadata') ORDER BY trip_id",
            principal.getSubject());
  }

  public Map<String, Object> permitted(String trip, Jwt principal) {
    return permitted(principal).stream()
        .filter(t -> trip.equals(t.get("trip_id")))
        .findFirst()
        .orElseThrow(() -> error(404, "TRIP_NOT_FOUND", "Không có chuyến được phép đọc"));
  }

  public Trace trace(String trip, Jwt principal, int year) {
    return trace(permitted(trip, principal), year);
  }

  public Trace trace(Map<String, Object> trip, int year) {
    if (!Boolean.TRUE.equals(trip.get("replay_enabled")))
      throw error(422, "TRACE_NOT_REPLAYABLE", "Chuyến chưa được chuẩn bị để phát lại");
    var manifest =
        parse(
            json,
            new String(
                storage.bytes((String) trip.get("trace_manifest_ref")), StandardCharsets.UTF_8));
    if (!"1.1".equals(string(manifest, "trace_schema_version"))
        || !"instant".equals(string(manifest, "time_precision")))
      throw error(422, "TRACE_NOT_REPLAYABLE", "Trace cần schema 1.1 và thời gian instant");
    String traceRef = string(manifest, "trace_ref");
    if (traceRef == null) traceRef = (String) trip.get("trace_ref");
    Path trace = storage.path(traceRef);
    byte[] source;
    try {
      if (Files.size(trace) > maxBytes)
        throw error(413, "TRACE_TOO_LARGE", "Trace vượt giới hạn cấu hình triển khai");
      source = Files.readAllBytes(trace);
    } catch (IOException e) {
      throw error(404, "TRACE_NOT_AVAILABLE", "Không đọc được trace");
    }
    String sourceHash = CoreConfig.sha256(source);
    for (var hash :
        Arrays.asList(string(manifest, "trace_sha256"), (String) trip.get("trace_sha256")))
      if (hash != null && !sourceHash.equals(hash))
        throw error(422, "TRACE_HASH_MISMATCH", "Hash trace không khớp manifest");
    var points = new ArrayList<ObjectNode>();
    Instant cutoff = ReliabilityCalculator.cutoffOf(year), previousTime = null;
    int previousSegment = -1;
    try (var lines =
        new BufferedReader(new StringReader(new String(source, StandardCharsets.UTF_8)))) {
      String line;
      while ((line = lines.readLine()) != null) {
        if (line.isBlank()) continue;
        JsonNode raw = json.readTree(line);
        if (!raw.isObject()) throw new IllegalArgumentException();
        ObjectNode p = (ObjectNode) raw;
        String t = string(p, "t");
        if (t == null || !t.endsWith("Z")) throw new IllegalArgumentException();
        Instant time = Instant.parse(t);
        if (!Objects.equals(string(p, "sequence_id"), trip.get("sequence_id"))
            || !Objects.equals(string(p, "creator_id"), trip.get("creator_id")))
          throw new IllegalArgumentException();
        if (number(p, "lon") == null || number(p, "lat") == null)
          throw new IllegalArgumentException();
        new Geo.Point(number(p, "lon"), number(p, "lat"));
        if (!p.path("segment_id").isIntegralNumber()
            || p.path("segment_id").asInt() < previousSegment
            || p.path("segment_id").asInt() < 0
            || string(p, "image_id") == null) throw new IllegalArgumentException();
        int segment = p.path("segment_id").asInt();
        if (segment == previousSegment && previousTime != null && !time.isAfter(previousTime))
          throw new IllegalArgumentException();
        if (p.hasNonNull("heading_deg")
            && (number(p, "heading_deg") == null
                || number(p, "heading_deg") < 0
                || number(p, "heading_deg") >= 360)) throw new IllegalArgumentException();
        if (p.hasNonNull("speed_mps")
            && (number(p, "speed_mps") == null || number(p, "speed_mps") < 0))
          throw new IllegalArgumentException();
        if (!Set.of("public_metadata", "illustration").contains(string(p, "data_kind")))
          throw new IllegalArgumentException();
        previousTime = time;
        previousSegment = segment;
        if (time.isBefore(cutoff)) points.add(p.deepCopy());
      }
    } catch (Exception e) {
      if (e instanceof vn.livesign.app.web.ApiException a) throw a;
      throw error(422, "TRACE_NOT_REPLAYABLE", "Trace sai metadata, thời gian hoặc tọa độ");
    }
    if (points.isEmpty()) throw error(404, "TRIP_NOT_FOUND", "Không có điểm trước cutoff");
    // Chuẩn hóa gap/GPS lỗi trên representation đã lọc; không dùng điểm tương lai để tính tốc độ.
    int segment = 0;
    ObjectNode prev = null;
    for (var p : points) {
      if (prev != null) {
        double seconds =
            Duration.between(
                        Instant.parse(prev.path("t").asText()), Instant.parse(p.path("t").asText()))
                    .toMillis()
                / 1000.0;
        double distance =
            Geo.distance(
                new Geo.Point(number(prev, "lon"), number(prev, "lat")),
                new Geo.Point(number(p, "lon"), number(p, "lat")));
        if (seconds <= 0
            || seconds > config.number("replay.max_gap_s")
            || distance / seconds > config.number("replay.max_gps_speed_mps")
            || p.path("segment_id").asInt() != prev.path("source_segment_id").asInt()) {
          segment++;
          p.putNull("speed_mps");
        } else if (!p.hasNonNull("speed_mps")) p.put("speed_mps", distance / seconds);
      }
      p.put("source_segment_id", p.path("segment_id").asInt());
      p.put("segment_id", segment);
      prev = p;
    }
    points.forEach(p -> p.remove("source_segment_id"));
    StringBuilder text = new StringBuilder();
    points.forEach(p -> text.append(write(json, p)).append('\n'));
    byte[] ndjson = text.toString().getBytes(StandardCharsets.UTF_8);
    var frames = json.createObjectNode();
    for (var p : points) {
      String image = p.path("image_id").asText();
      var metadata = manifest.path("frames_metadata").path(image);
      if (metadata.isObject()) {
        var selected = frames.putObject(image);
        for (var key :
            List.of(
                "focal",
                "is_pano",
                "image_width_px",
                "image_height_px",
                "geometry_source",
                "heading_source")) if (metadata.has(key)) selected.set(key, metadata.get(key));
      }
    }
    var representation =
        map(
            "trip_id",
            trip.get("trip_id"),
            "trace_schema_version",
            "1.1",
            "evaluation_year",
            year,
            "cutoff_exclusive_utc",
            cutoff,
            "sequence_id",
            trip.get("sequence_id"),
            "creator_id",
            trip.get("creator_id"),
            "data_kind",
            trip.get("data_kind"),
            "point_count",
            points.size(),
            "segment_count",
            segment + 1,
            "time_precision",
            "instant",
            "speed_method",
            string(manifest, "speed_method"),
            "heading_method",
            string(manifest, "heading_method"),
            "trace_sha256",
            CoreConfig.sha256(ndjson),
            "source_trace_sha256",
            sourceHash,
            "attribution",
            string(manifest, "attribution"),
            "frames_metadata",
            frames);
    representation.putAll(mode.labels());
    return new Trace(trip, manifest, List.copyOf(points), ndjson, representation);
  }
}
