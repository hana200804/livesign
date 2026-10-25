package vn.livesign.app.flow;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.livesign.app.workflow.ObservationValidator;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.config.CoreConfig;

class ObservationValidatorTest {
  final ObjectMapper json = new ObjectMapper();
  final CoreConfig config = CoreConfig.load(Path.of("../../config/params.yaml"));
  final SignCatalog catalog = SignCatalog.load(Path.of("../../ai/configs/classes.yaml"));
  final ObservationValidator validator = new ObservationValidator(catalog, config);

  ObjectNode valid() {
    var o =
        json.createObjectNode()
            .put("observation_id", "o")
            .put("trip_id", "t")
            .put("source", "mapillary")
            .put("sequence_id", "s")
            .put("image_id", "i")
            .put("captured_at", "2025-01-01T00:00:00Z")
            .put("captured_at_precision", "instant")
            .put("outcome", "seen")
            .put("visibility_method", "unverified")
            .put("sign_type_code", "P.102")
            .put("model_version", "m")
            .put("detector_confidence", .9)
            .put("image_width_px", 640)
            .put("image_height_px", 480);
    o.putArray("bbox_px").add(1).add(2).add(10).add(20);
    return o;
  }

  @Test
  void bienDuLieuSoVaBbox() {
    assertEquals("accepted", validator.validate(valid()));
    for (var key :
        List.of(
            "observation_id",
            "source",
            "trip_id",
            "sequence_id",
            "image_id",
            "captured_at",
            "captured_at_precision",
            "outcome",
            "visibility_method",
            "bbox_px",
            "model_version",
            "detector_confidence",
            "image_width_px")) {
      var o = valid();
      o.remove(key);
      assertThrows(RuntimeException.class, () -> validator.validate(o), key);
    }
    for (var field :
        List.of(
            "camera_lon",
            "camera_lat",
            "camera_heading_deg",
            "sign_lon",
            "sign_lat",
            "sign_facing_deg",
            "off_deg",
            "distance_m",
            "focal",
            "detector_confidence")) {
      var o = valid();
      o.put(field, "bad");
      assertThrows(RuntimeException.class, () -> validator.validate(o), field);
    }
    for (var field : List.of("is_pano", "in_fov", "facing_camera", "sharp", "unoccluded")) {
      var o = valid();
      o.put(field, 1);
      assertThrows(RuntimeException.class, () -> validator.validate(o), field);
    }
    for (var field : List.of("detector_confidence", "distance_m", "focal")) {
      var o = valid();
      o.put(field, -1);
      assertThrows(RuntimeException.class, () -> validator.validate(o));
    }
    for (var box :
        List.of(
            List.of(-1, 2, 10, 20),
            List.of(10, 2, 1, 20),
            List.of(1, 2, 641, 20),
            List.of(1, 2, 10, 481),
            List.of(1, 2, 3))) {
      var o = valid();
      o.set("bbox_px", json.valueToTree(box));
      assertThrows(RuntimeException.class, () -> validator.validate(o));
    }
    var coords = valid().put("camera_lon", 105);
    assertThrows(RuntimeException.class, () -> validator.validate(coords));
    coords.put("camera_lat", 91);
    assertThrows(RuntimeException.class, () -> validator.validate(coords));
    var bad = valid().put("camera_heading_deg", 360);
    assertThrows(RuntimeException.class, () -> validator.validate(bad));
  }

  @Test
  void ngayKhongGiaGioVaUnknownKhongTaoBangChung() {
    var o = valid().put("captured_at_precision", "day").put("captured_at", "2025-01-01");
    assertEquals("2025-01-01T00:00:00Z", validator.time(o).toString());
    o.put("captured_at", "2025-01-01T12:00:00Z");
    assertThrows(RuntimeException.class, () -> validator.time(o));
    o.put("outcome", "unknown");
    o.remove("sign_type_code");
    o.put("captured_at", "2025-01-01");
    assertEquals("metadata_only", validator.validate(o));
    o.put("source_metadata", "not-object");
    assertThrows(RuntimeException.class, () -> validator.validate(o));
  }

  @Test
  void khongChoCredentialHoacRefTuyY() {
    for (var key : List.of("access_token", "password", "api_key", "authorization")) {
      var o = valid();
      o.putObject("source_metadata").put(key, "secret");
      assertThrows(RuntimeException.class, () -> validator.validate(o));
    }
    var o = valid();
    o.putObject("source_metadata").putArray("nested").add("MLY|credential");
    assertThrows(RuntimeException.class, () -> validator.validate(o));
    for (var ref : List.of("../x", "/tmp/x", "C:/x", "https://host/x", "a\\b", "a//b"))
      assertThrows(RuntimeException.class, () -> ObservationValidator.safeRef(ref));
    ObservationValidator.safeRef("observations/o/crop.jpg");
  }

  @Test
  void amCanToanBoCoVaProvenance() {
    var enabled =
        new CoreConfig(
            new String(config.bytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace(
                    "negative_evidence_enabled: {value: null",
                    "negative_evidence_enabled: {value: true")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var v = new ObservationValidator(catalog, enabled);
    var o =
        valid()
            .put("outcome", "not_seen")
            .put("target_sign_id", "s")
            .put("creator_id", "c")
            .put("camera_lon", 105)
            .put("camera_lat", 21)
            .put("camera_heading_deg", 0)
            .put("sign_lon", 105)
            .put("sign_lat", 21.0001)
            .put("sign_facing_deg", 180)
            .put("distance_m", 10)
            .put("focal", .8)
            .put("is_pano", false)
            .put("in_fov", true)
            .put("facing_camera", true)
            .put("sharp", true)
            .put("unoccluded", true)
            .put("evidence_ref", "o/a.jpg")
            .put("visibility_method", "human");
    o.remove(List.of("bbox_px", "detector_confidence"));
    o.putObject("source_metadata").putObject("provenance").put("snapshot_run_id", "r");
    assertEquals("accepted", v.validate(o));
    assertThrows(RuntimeException.class, () -> validator.validate(o));
    for (var key : List.of("in_fov", "facing_camera", "sharp", "unoccluded")) {
      var bad = o.deepCopy().putNull(key);
      assertThrows(RuntimeException.class, () -> v.validate(bad));
    }
  }
}
