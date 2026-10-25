package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.livesign.app.web.ApiException;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.spatial.Geo;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {
  private final TripService trips;
  private final SnapshotService snapshots;
  private final SignQueries signs;
  private final Cursors cursors;
  private final SafeStorage storage;
  private final ObjectMapper json;

  public TripController(
      TripService trips,
      SnapshotService snapshots,
      SignQueries signs,
      Cursors cursors,
      SafeStorage storage,
      ObjectMapper json) {
    this.trips = trips;
    this.snapshots = snapshots;
    this.signs = signs;
    this.cursors = cursors;
    this.storage = storage;
    this.json = json;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(name = "evaluation_year") int year,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(required = false) String cursor,
      @AuthenticationPrincipal Jwt principal) {
    snapshots.validateYear(year);
    var items = new ArrayList<Map<String, Object>>();
    for (var trip : trips.permitted(principal)) {
      if (!Boolean.TRUE.equals(trip.get("replay_enabled"))) continue;
      try {
        var t = trips.trace(trip, year);
        items.add(
            map(
                "trip_id",
                trip.get("trip_id"),
                "source",
                trip.get("source"),
                "sequence_id",
                trip.get("sequence_id"),
                "creator_id",
                trip.get("creator_id"),
                "started_at",
                t.points().getFirst().path("t").asText(),
                "ended_at",
                t.points().getLast().path("t").asText(),
                "trace_schema_version",
                "1.1",
                "trace_available",
                true,
                "point_count",
                t.points().size(),
                "data_kind",
                trip.get("data_kind"),
                "trace_url",
                "/api/v1/trips/"
                    + java.net.URLEncoder.encode(
                        (String) trip.get("trip_id"), StandardCharsets.UTF_8)
                    + "/trace?evaluation_year="
                    + year));
      } catch (ApiException e) {
        if (e.status().value() != 404) throw e;
      }
    }
    var response = map("evaluation_year", year);
    response.putAll(
        cursors.page(
            items,
            limit,
            cursor,
            map(
                "kind",
                "trips",
                "year",
                year,
                "user",
                principal.getSubject(),
                "limit",
                limit,
                "representation",
                hash(json, items))));
    return response;
  }

  @GetMapping("/{tripId}/trace")
  public ResponseEntity<?> trace(
      @PathVariable String tripId,
      @RequestParam(name = "evaluation_year") int year,
      @RequestHeader(value = "Accept", defaultValue = "application/json") String accept,
      @RequestHeader(value = "If-None-Match", required = false) String etag,
      @AuthenticationPrincipal Jwt principal) {
    snapshots.validateYear(year);
    var trace = trips.trace(tripId, principal, year);
    boolean ndjson = accept.contains("application/x-ndjson");
    byte[] bytes =
        ndjson
            ? trace.ndjson()
            : write(json, trace.representation()).getBytes(StandardCharsets.UTF_8);
    String tag = "\"" + CoreConfig.sha256(bytes) + "\"";
    if (tag.equals(etag))
      return ResponseEntity.status(304)
          .eTag(tag)
          .cacheControl(CacheControl.noCache().cachePrivate())
          .build();
    return ResponseEntity.ok()
        .eTag(tag)
        .cacheControl(CacheControl.noCache().cachePrivate())
        .contentType(MediaType.parseMediaType(ndjson ? "application/x-ndjson" : "application/json"))
        .body(bytes);
  }

  @GetMapping("/{tripId}/images/{imageId}")
  public ResponseEntity<byte[]> image(
      @PathVariable String tripId,
      @PathVariable String imageId,
      @RequestParam(name = "evaluation_year") int year,
      @RequestHeader(value = "If-None-Match", required = false) String etag,
      @AuthenticationPrincipal Jwt principal) {
    snapshots.validateYear(year);
    var trace = trips.trace(tripId, principal, year);
    if (trace.points().stream().noneMatch(p -> imageId.equals(p.path("image_id").asText())))
      throw error(404, "IMAGE_NOT_AVAILABLE", "Ảnh không thuộc representation được phép");
    var image = trace.manifest().path("images").path(imageId);
    String ref = string(image, "ref");
    if (ref == null) throw error(404, "IMAGE_NOT_AVAILABLE", "Ảnh chưa có trong kho");
    byte[] bytes = storage.bytes(ref);
    String hash = CoreConfig.sha256(bytes);
    if (image.hasNonNull("sha256") && !hash.equals(string(image, "sha256")))
      throw error(422, "IMAGE_HASH_MISMATCH", "Hash ảnh không khớp manifest");
    String content = string(image, "content_type");
    if (content == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(content))
      throw error(422, "INVALID_IMAGE_TYPE", "Media type ảnh chưa được hỗ trợ");
    String tag = "\"" + hash + "\"";
    if (tag.equals(etag))
      return ResponseEntity.status(304)
          .eTag(tag)
          .cacheControl(CacheControl.noCache().cachePrivate())
          .build();
    String attribution =
        Objects.toString(
            string(image, "attribution"),
            Objects.toString(string(trace.manifest(), "attribution"), ""));
    return ResponseEntity.ok()
        .eTag(tag)
        .cacheControl(CacheControl.noCache().cachePrivate())
        .header("X-Attribution", java.net.URLEncoder.encode(attribution, StandardCharsets.UTF_8))
        .contentType(MediaType.parseMediaType(content))
        .body(bytes);
  }

  @GetMapping("/{tripId}/signs")
  public Map<String, Object> signs(
      @PathVariable String tripId,
      @RequestParam(name = "evaluation_year") int year,
      @RequestParam(name = "vehicle_type") String vehicle,
      @RequestParam(name = "snapshot_run_id", required = false) String runId,
      @AuthenticationPrincipal Jwt principal) {
    signs.vehicle(vehicle);
    var run = snapshots.resolve(year, runId);
    var trace = trips.trace(tripId, principal, year);
    var config = snapshots.configFor(run);
    List<Geo.Point> points =
        trace.points().stream()
            .map(p -> new Geo.Point(number(p, "lon"), number(p, "lat")))
            .toList();
    double buffer = config.number("replay.corridor_buffer_m");
    var items =
        signs.rows((String) run.get("run_id")).stream()
            .filter(
                r -> {
                  var point = signs.aheadSign(r).point();
                  for (int i = 0; i < points.size(); i++) {
                    if (Geo.distance(point, points.get(i)) <= buffer) return true;
                    if (i > 0
                        && trace.points().get(i).path("segment_id").asInt()
                            == trace.points().get(i - 1).path("segment_id").asInt()
                        && Geo.segmentDistance(point, points.get(i - 1), points.get(i)) <= buffer)
                      return true;
                  }
                  return false;
                })
            .map(r -> signs.item(r, year, vehicle))
            .toList();
    if (items.size() > 500) throw error(413, "TRIP_TOO_LARGE", "Quá 500 biển; cần chia chuyến");
    var alert = map("params_version", config.version(), "corridor_buffer_m", buffer);
    for (var key :
        List.of(
            "warning_min_lead_s",
            "warning_latency_buffer_s",
            "ahead_max_bearing_diff_deg",
            "ahead_max_facing_diff_deg")) alert.put(key, config.number("geometry." + key));
    for (var key : List.of("alert_cooldown_s", "alert_rearm_distance_m"))
      alert.put(key, config.number("replay." + key));
    var response = signs.meta(run);
    response.put("trip_id", tripId);
    response.put("vehicle_type", vehicle);
    response.put("alert_params", alert);
    response.put("items", items);
    return response;
  }
}
