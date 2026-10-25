package vn.livesign.app.flow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.livesign.app.MySqlTestcontainer;
import vn.livesign.core.spatial.Geo;

/** Kịch bản riêng trong MySQL test; không sửa ngưỡng/cơ sở dữ liệu vận hành. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainer.class)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("simulation")
class SimulationFlowTest {
  static final Path ROOT = Path.of("target", "simulation-" + UUID.randomUUID()).toAbsolutePath();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) throws Exception {
    Files.createDirectories(ROOT);
    String params =
        Files.readString(Path.of("../../config/params.yaml"))
            .replace("N: {value: null", "N: {value: 3")
            .replace("theta: {value: null", "theta: {value: 0.75")
            .replace("theta_low: {value: null", "theta_low: {value: 0.40")
            .replace(
                "negative_evidence_enabled: {value: null",
                "negative_evidence_enabled: {value: true");
    Files.writeString(ROOT.resolve("params.yaml"), params);
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        ROOT.resolve("evidence.png").toFile());
    r.add("livesign.storage-root", ROOT::toString);
    r.add("livesign.params", () -> ROOT.resolve("params.yaml").toUri().toString());
    r.add("livesign.scenario-id", () -> "backend-test");
    r.add("livesign.simulation-now", () -> "2025-10-01T00:00:00Z");
    r.add("spring.datasource.url", () -> "jdbc:mysql://localhost/livesign_simulation_test");
    r.add("spring.datasource.username", () -> "test");
    r.add("spring.datasource.password", () -> "test");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  String user;

  @BeforeEach
  void account() {
    user = "simulation:" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO `user`(user_id,username,password_hash,role) VALUES (?,?,?,'admin')",
        user,
        user,
        "unused");
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
    return jwt()
        .jwt(j -> j.subject(user).claim("role", role))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
  }

  ObjectNode seen(
      String trip, String creator, String feature, String type, int second, double x, String date) {
    var camera = Geo.offset(new Geo.Point(105.84, 21.02), x, second);
    var point = Geo.offset(new Geo.Point(105.84, 21.02), x, 16);
    var o =
        json.createObjectNode()
            .put("observation_id", trip + ":" + second)
            .put("trip_id", trip)
            .put("source", "mapillary")
            .put("sequence_id", trip)
            .put("creator_id", creator)
            .put("image_id", trip + ":image:" + second)
            .put("captured_at", date.replace("00Z", "0" + second + "Z"))
            .put("captured_at_precision", "instant")
            .put("outcome", "seen")
            .put("sign_type_code", type)
            .put("camera_lon", camera.lon())
            .put("camera_lat", camera.lat())
            .put("camera_heading_deg", 0)
            .put("off_deg", 0)
            .put("is_pano", false)
            .put("focal", .8)
            .put("image_width_px", 2048)
            .put("image_height_px", 1152)
            .put("detector_confidence", .9)
            .put("visibility_method", "human")
            .put("model_version", "simulation-model")
            .put("evidence_ref", "evidence.png")
            .put("source_sign_id", feature);
    o.putArray("bbox_px").add(1000).add(400).add(1048).add(460);
    o.putObject("source_metadata")
        .putObject("positioning")
        .putArray("feature_candidates")
        .addObject()
        .put("source_sign_id", feature)
        .put(
            "source_sign_type",
            type.equals("P.131a") ? "regulatory--no-parking--g1" : "regulatory--no-entry--g1")
        .put("lon", point.lon())
        .put("lat", point.lat())
        .put("ray_distance_m", 0);
    return o;
  }

  JsonNode upload(String trip, String creator, ObjectNode... rows) throws Exception {
    var b =
        json.createObjectNode()
            .put("schema_version", "1.0")
            .put("classes_version", "qcvn12-2026-10-19");
    b.putObject("trip")
        .put("trip_id", trip)
        .put("source", "mapillary")
        .put("sequence_id", trip)
        .put("creator_id", creator)
        .put("data_kind", "illustration");
    for (var row : rows) b.withArray("observations").add(row);
    return json.readTree(
        mvc.perform(
                post("/api/v1/observations/batch")
                    .with(as("importer"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(b)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rejected").value(0))
            .andReturn()
            .getResponse()
            .getContentAsByteArray());
  }

  JsonNode sign(String id, int year) throws Exception {
    return json.readTree(
        mvc.perform(
                get("/api/v1/signs/{id}", id)
                    .param("evaluation_year", String.valueOf(year))
                    .with(as("user")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray());
  }

  record Fixture(
      String sign, String before, String after, String review, String feature, double x) {}

  Fixture suspected(double x) throws Exception {
    String prefix = UUID.randomUUID().toString(),
        feature = "feature:" + prefix,
        sign = null,
        before = null,
        after = null;
    for (int i = 0; i < 4; i++) {
      String trip = "trip:" + prefix + ":" + i, creator = "creator:" + prefix + ":" + i;
      var a = seen(trip, creator, feature, "P.131a", 0, x, "2023-01-01T00:00:00Z");
      var b = seen(trip, creator, feature, "P.131a", 1, x, "2023-01-01T00:00:00Z");
      var response = upload(trip, creator, a, b);
      if (sign == null) {
        sign = response.at("/items/0/target_sign_id").asText();
        before = a.path("observation_id").asText();
      }
    }
    assertThat(sign(sign, 2023).path("status").asText()).isEqualTo("confirmed_auto");
    for (int year : List.of(2024, 2025)) {
      String trip = "negative:" + prefix + ":" + year, creator = "creator:" + prefix + ":" + year;
      JsonNode snapshot = sign(sign, year);
      var camera = Geo.offset(new Geo.Point(105.84, 21.02), x, 0);
      var point =
          new Geo.Point(snapshot.path("sign_lon").asDouble(), snapshot.path("sign_lat").asDouble());
      var o =
          seen(trip, creator, feature, "P.131a", 0, x, year + "-01-01T00:00:00Z")
              .put("outcome", "not_seen")
              .put("target_sign_id", sign)
              .put("sign_lon", point.lon())
              .put("sign_lat", point.lat())
              .put("sign_facing_deg", snapshot.path("sign_facing_deg").asDouble())
              .put("distance_m", Geo.distance(camera, point))
              .put("in_fov", true)
              .put("facing_camera", true)
              .put("sharp", true)
              .put("unoccluded", true);
      o.remove(List.of("bbox_px", "detector_confidence"));
      o.withObject("/source_metadata")
          .putObject("provenance")
          .put("snapshot_run_id", snapshot.path("snapshot_run_id").asText());
      upload(trip, creator, o);
      after = o.path("observation_id").asText();
    }
    assertThat(sign(sign, 2025).path("status").asText()).isEqualTo("suspected_change");
    String review =
        jdbc.queryForObject(
            "SELECT review_id FROM review WHERE sign_id=? AND status='pending'",
            String.class,
            sign);
    return new Fixture(sign, before, after, review, feature, x);
  }

  ObjectNode decision(Fixture f, String action) {
    return json.createObjectNode()
        .put("expected_version", 1)
        .put("decision", action)
        .put("before_observation_id", f.before)
        .put("after_observation_id", f.after)
        .put("effective_at", "2025-01-01T00:00:00Z")
        .put("reason", "Kịch bản mô phỏng trong CSDL kiểm thử");
  }

  @Test
  void xacNhanNghiVaGiuKhongNangDiemGia() throws Exception {
    var f = suspected(0);
    JsonNode old = sign(f.sign, 2025);
    assertThat(old.at("/reliability/agreement_score").asDouble())
        .isCloseTo(4.0 / 9.0, within(1e-9));
    mvc.perform(
            get("/api/v1/admin/reviews/{id}", f.review)
                .param("evaluation_year", "2025")
                .with(as("admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.before.observation_id").isNotEmpty())
        .andExpect(jsonPath("$.after.observation_id").value(f.after));
    mvc.perform(get("/api/v1/admin/reviews").param("evaluation_year", "2025").with(as("admin")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/signs/{id}/history", f.sign)
                .param("evaluation_year", "2025")
                .with(as("user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].changed_by").doesNotExist());
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", f.review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(decision(f, "defer"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", f.review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(decision(f, "keep").put("expected_version", 2))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/admin/snapshots/rebuild")
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evaluation_year\":2025}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot_runs[0].status").value("ready"));
    assertThat(sign(f.sign, 2025).at("/reliability/agreement_score").asDouble())
        .isEqualTo(old.at("/reliability/agreement_score").asDouble());
  }

  @Test
  void goCanAmVaAnhTruocSau() throws Exception {
    var f = suspected(100);
    mvc.perform(get("/api/v1/admin/observations/{id}/evidence", f.before).with(as("user")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/observations/{id}/evidence", f.before).with(as("admin")))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", f.review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        decision(f, "remove").put("after_observation_id", f.before))))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", f.review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(decision(f, "remove"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sign_status").value("removed"));
    assertThat(jdbc.queryForObject("SELECT status FROM sign WHERE sign_id=?", String.class, f.sign))
        .isEqualTo("removed");
  }

  @Test
  void thayGiuHaiSignVaTuChoiVersionCu() throws Exception {
    var f = suspected(200);
    String trip = "replacement:" + UUID.randomUUID(), creator = "new-creator";
    var first = seen(trip, creator, "new-feature:" + trip, "P.102", 0, f.x, "2025-02-01T00:00:00Z");
    var second =
        seen(trip, creator, "new-feature:" + trip, "P.102", 1, f.x, "2025-02-01T00:00:00Z");
    String newer = upload(trip, creator, first, second).at("/items/0/target_sign_id").asText();
    for (int i = 1; i < 3; i++) {
      String extra = trip + ":" + i, author = "replacement-creator:" + i;
      upload(
          extra,
          author,
          seen(extra, author, "new-feature:" + trip, "P.102", 0, f.x, "2025-02-01T00:00:00Z"),
          seen(extra, author, "new-feature:" + trip, "P.102", 1, f.x, "2025-02-01T00:00:00Z"));
    }
    assertThat(sign(newer, 2025).path("status").asText()).isEqualTo("confirmed_auto");
    var request =
        decision(f, "replace")
            .put("after_observation_id", first.path("observation_id").asText())
            .put("replacement_sign_id", newer);
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", f.review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replacement_sign_id").value(newer));
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", f.review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT sign_type_code FROM sign WHERE sign_id=?", String.class, f.sign))
        .isEqualTo("P.131a");
    assertThat(
            jdbc.queryForObject(
                "SELECT sign_type_code FROM sign WHERE sign_id=?", String.class, newer))
        .isEqualTo("P.102");
  }
}
