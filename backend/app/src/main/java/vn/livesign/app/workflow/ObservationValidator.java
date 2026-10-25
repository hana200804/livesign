package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.spatial.*;

/** Kiểm hợp đồng trước khi ghi; lỗi từng dòng được trả riêng. */
@Component
public class ObservationValidator {
  private final SignCatalog catalog;
  private final CoreConfig config;

  public ObservationValidator(SignCatalog catalog, CoreConfig config) {
    this.catalog = catalog;
    this.config = config;
  }

  static void require(boolean ok, String field) {
    if (!ok) throw error(422, "INVALID_OBSERVATION", "Thiếu hoặc sai " + field);
  }

  static String required(JsonNode n, String key, int max) {
    String s = string(n, key);
    require(s != null && !s.isBlank() && s.length() <= max, key);
    return s;
  }

  public Instant time(JsonNode n) {
    String precision = required(n, "captured_at_precision", 16),
        text = required(n, "captured_at", 40);
    try {
      if ("day".equals(precision)) {
        if (text.length() == 10)
          return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant t = Instant.parse(text);
        require(t.equals(t.truncatedTo(java.time.temporal.ChronoUnit.DAYS)), "captured_at");
        return t;
      }
      require("instant".equals(precision) && text.endsWith("Z"), "captured_at_precision");
      Instant t = Instant.parse(text);
      require(t.getNano() % 1_000_000 == 0, "captured_at millisecond");
      return t;
    } catch (DateTimeException e) {
      throw error(422, "INVALID_OBSERVATION", "captured_at không hợp lệ");
    }
  }

  public static void safeRef(String ref) {
    if (ref == null) return;
    require(ref.chars().noneMatch(Character::isISOControl), "evidence_ref/trace_ref");
    require(
        !ref.isBlank()
            && ref.length() <= 512
            && !ref.contains("\\")
            && !ref.contains(":")
            && !ref.startsWith("/")
            && Arrays.stream(ref.split("/", -1))
                .noneMatch(s -> s.equals("..") || s.equals(".") || s.isEmpty()),
        "evidence_ref/trace_ref");
  }

  public String validate(JsonNode o) {
    require(o.isObject(), "observation");
    required(o, "observation_id", 160);
    required(o, "trip_id", 160);
    required(o, "sequence_id", 128);
    required(o, "image_id", 128);
    time(o);
    String creator = string(o, "creator_id");
    require(creator == null || !creator.isBlank() && creator.length() <= 128, "creator_id");
    require(!o.hasNonNull("creator_id") || o.get("creator_id").isTextual(), "creator_id");
    require(Set.of("mapillary", "android_replay").contains(required(o, "source", 32)), "source");
    String outcome = required(o, "outcome", 16);
    require(Set.of("seen", "not_seen", "unknown").contains(outcome), "outcome");
    require(
        Set.of("unverified", "independent_model_audited", "human", "automatic_rules")
            .contains(required(o, "visibility_method", 32)),
        "visibility_method");
    for (var key :
        List.of(
            "target_sign_id",
            "source_sign_id",
            "source_sign_type",
            "model_version",
            "evidence_ref")) {
      if (o.hasNonNull(key))
        require(
            o.get(key).isTextual()
                && !o.get(key).asText().isBlank()
                && o.get(key).asText().length()
                    <= (key.equals("evidence_ref")
                        ? 512
                        : key.equals("target_sign_id") || key.equals("source_sign_type")
                            ? 160
                            : 128),
            key);
    }
    for (var key :
        List.of(
            "camera_lon",
            "camera_lat",
            "camera_heading_deg",
            "sign_lon",
            "sign_lat",
            "sign_facing_deg",
            "distance_m",
            "off_deg",
            "focal",
            "detector_confidence")) if (o.hasNonNull(key)) require(number(o, key) != null, key);
    for (var key : List.of("is_pano", "in_fov", "facing_camera", "sharp", "unoccluded"))
      if (o.hasNonNull(key)) require(o.get(key).isBoolean(), key);
    coords(o, "camera_lon", "camera_lat");
    coords(o, "sign_lon", "sign_lat");
    for (var key : List.of("camera_heading_deg", "sign_facing_deg")) range(o, key, 0, 360, false);
    range(o, "off_deg", -180, 180, false);
    range(o, "detector_confidence", 0, 1, true);
    if (o.hasNonNull("distance_m")) require(number(o, "distance_m") >= 0, "distance_m");
    if (o.hasNonNull("focal")) require(number(o, "focal") > 0, "focal");
    require(o.hasNonNull("image_width_px") == o.hasNonNull("image_height_px"), "image size");
    if (o.hasNonNull("image_width_px"))
      for (var k : List.of("image_width_px", "image_height_px"))
        require(
            o.get(k).isIntegralNumber() && o.get(k).canConvertToInt() && o.get(k).asInt() > 0, k);
    if (o.hasNonNull("bbox_px")) {
      var b = o.get("bbox_px");
      require(b.isArray() && b.size() == 4 && o.hasNonNull("image_width_px"), "bbox_px");
      for (var v : b) require(v.isNumber() && Double.isFinite(v.asDouble()), "bbox_px");
      require(
          b.get(0).asDouble() >= 0
              && b.get(1).asDouble() >= 0
              && b.get(2).asDouble() > b.get(0).asDouble()
              && b.get(3).asDouble() > b.get(1).asDouble()
              && b.get(2).asDouble() <= o.path("image_width_px").asInt()
              && b.get(3).asDouble() <= o.path("image_height_px").asInt(),
          "bbox_px");
      require(o.hasNonNull("model_version"), "model_version");
    }
    String type = string(o, "sign_type_code");
    require(type == null || catalog.byCode(type).isPresent(), "sign_type_code");
    require(
        !o.hasNonNull("sign_type_code") || o.get("sign_type_code").isTextual(), "sign_type_code");
    safeRef(string(o, "evidence_ref"));
    require(
        !o.hasNonNull("source_metadata") || o.get("source_metadata").isObject(), "source_metadata");
    if (o.hasNonNull("source_metadata")) checkSecrets(o.get("source_metadata"));
    var features = o.at("/source_metadata/positioning/feature_candidates");
    if (!features.isMissingNode() && !features.isNull()) {
      require(features.isArray() && features.size() <= 100, "feature_candidates");
      for (var f : features) {
        required(f, "source_sign_id", 128);
        required(f, "source_sign_type", 160);
        require(number(f, "lon") != null && number(f, "lat") != null, "feature coordinates");
        try {
          new Geo.Point(number(f, "lon"), number(f, "lat"));
        } catch (IllegalArgumentException e) {
          require(false, "feature coordinates");
        }
      }
    }
    if ("seen".equals(outcome))
      require(
          type != null
              && o.hasNonNull("bbox_px")
              && o.hasNonNull("detector_confidence")
              && o.hasNonNull("model_version"),
          "seen bbox/confidence/model/type");
    if ("not_seen".equals(outcome)) {
      require(config.enabled("workflow.negative_evidence_enabled"), "negative_evidence_enabled");
      for (var k :
          List.of(
              "target_sign_id",
              "creator_id",
              "camera_lon",
              "camera_lat",
              "camera_heading_deg",
              "sign_lon",
              "sign_lat",
              "sign_facing_deg",
              "distance_m",
              "is_pano",
              "image_width_px",
              "focal",
              "model_version",
              "evidence_ref")) require(o.hasNonNull(k), k);
      require(
          type != null && !o.hasNonNull("bbox_px") && !o.hasNonNull("detector_confidence"),
          "not_seen bbox/confidence/type");
      for (var k : List.of("in_fov", "facing_camera", "sharp", "unoccluded"))
        require(o.path(k).isBoolean() && o.path(k).asBoolean(), k);
      require(
          Set.of("human", "independent_model_audited").contains(string(o, "visibility_method")),
          "visibility_method");
      require(o.at("/source_metadata/provenance/snapshot_run_id").isTextual(), "snapshot_run_id");
    }
    return "unknown".equals(outcome)
            && (type == null || creator == null || !o.hasNonNull("camera_lon"))
        ? "metadata_only"
        : "accepted";
  }

  private void checkSecrets(JsonNode n) {
    if (n.isObject())
      n.fields()
          .forEachRemaining(
              e -> {
                require(
                    !e.getKey()
                        .toLowerCase(Locale.ROOT)
                        .matches(".*(token|password|secret|authorization|api_key).*"),
                    "source_metadata credential");
                checkSecrets(e.getValue());
              });
    else if (n.isArray()) n.forEach(this::checkSecrets);
    else if (n.isTextual())
      require(
          !n.asText().contains("MLY|") && !n.asText().contains("access_token="),
          "source_metadata credential");
  }

  private static void coords(JsonNode o, String x, String y) {
    require(o.hasNonNull(x) == o.hasNonNull(y), x + "/" + y);
    if (o.hasNonNull(x)) {
      require(number(o, x) != null && number(o, y) != null, x + "/" + y);
      try {
        new Geo.Point(number(o, x), number(o, y));
      } catch (IllegalArgumentException e) {
        require(false, x + "/" + y);
      }
    }
  }

  private static void range(JsonNode o, String key, double min, double max, boolean inclusive) {
    if (o.hasNonNull(key))
      require(
          number(o, key) >= min && (inclusive ? number(o, key) <= max : number(o, key) < max), key);
  }
}
