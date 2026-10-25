package vn.livesign.app.flow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.livesign.app.MySqlTestcontainer;
import vn.livesign.core.config.CoreConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainer.class)
@Testcontainers(disabledWithoutDocker = true)
class ReplayApiTest {
  static final Path ROOT = Path.of("target", "replay-" + UUID.randomUUID()).toAbsolutePath();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("livesign.storage-root", ROOT::toString);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  org.springframework.test.web.servlet.request.RequestPostProcessor as(String id, String role) {
    return jwt()
        .jwt(j -> j.subject(id).claim("role", role))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
  }

  @Test
  void traceAnhVaHashChiChuaDuLieuTruocCutoff() throws Exception {
    Files.createDirectories(ROOT);
    String id = "replay:" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO `user`(user_id,username,password_hash,role) VALUES (?,?,?,'importer')",
        id,
        id,
        "unused");
    String trace =
        """
{"t":"2023-01-01T00:00:00Z","lon":105.84,"lat":21.02,"heading_deg":0,"speed_mps":10,"image_id":"past","sequence_id":"seq","creator_id":"creator","segment_id":0,"data_kind":"illustration"}
{"t":"2025-01-01T00:00:00Z","lon":105.84,"lat":21.0201,"heading_deg":0,"speed_mps":10,"image_id":"future","sequence_id":"seq","creator_id":"creator","segment_id":1,"data_kind":"illustration"}
""";
    Files.writeString(ROOT.resolve("trace.jsonl"), trace);
    var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
    javax.imageio.ImageIO.write(image, "png", ROOT.resolve("frame.png").toFile());
    var manifest =
        json.createObjectNode()
            .put("trip_id", id)
            .put("trace_schema_version", "1.1")
            .put("trace_ref", "trace.jsonl")
            .put(
                "trace_sha256",
                CoreConfig.sha256(trace.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .put("time_precision", "instant")
            .put("speed_method", "fixture")
            .put("heading_method", "fixture")
            .put("attribution", "Dữ liệu minh họa");
    for (String frame : List.of("past", "future"))
      manifest
          .withObject("/images")
          .putObject(frame)
          .put("ref", "frame.png")
          .put("content_type", "image/png");
    manifest.putObject("frames_metadata").putObject("future").put("focal", .8);
    Files.writeString(ROOT.resolve("manifest.json"), json.writeValueAsString(manifest));
    var b =
        json.createObjectNode()
            .put("schema_version", "1.0")
            .put("classes_version", "qcvn12-2026-10-19");
    b.putObject("trip")
        .put("trip_id", id)
        .put("source", "mapillary")
        .put("sequence_id", "seq")
        .put("creator_id", "creator")
        .put("trace_ref", "trace.jsonl")
        .put("trace_manifest_ref", "manifest.json")
        .put("data_kind", "illustration")
        .put("replay_enabled", true);
    b.putArray("observations")
        .addObject()
        .put("observation_id", id)
        .put("trip_id", id)
        .put("source", "mapillary")
        .put("sequence_id", "seq")
        .put("creator_id", "creator")
        .put("image_id", "past")
        .put("captured_at", "2023-01-01T00:00:00Z")
        .put("captured_at_precision", "instant")
        .put("outcome", "unknown")
        .put("visibility_method", "unverified");
    mvc.perform(
            post("/api/v1/observations/batch")
                .with(as(id, "importer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(b)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata_only").value(1));
    var response =
        mvc.perform(
                get("/api/v1/trips/{id}/trace", id)
                    .param("evaluation_year", "2023")
                    .with(as(id, "importer")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.point_count").value(1))
            .andExpect(jsonPath("$.frames_metadata.future").doesNotExist())
            .andReturn()
            .getResponse();
    String etag = response.getHeader("ETag");
    mvc.perform(
            get("/api/v1/trips/{id}/trace", id)
                .param("evaluation_year", "2023")
                .header("If-None-Match", etag)
                .with(as(id, "importer")))
        .andExpect(status().isNotModified());
    mvc.perform(
            get("/api/v1/trips/{id}/trace", id)
                .param("evaluation_year", "2023")
                .header("If-None-Match", etag)
                .with(as("other", "user")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/trips/{id}/images/future", id)
                .param("evaluation_year", "2023")
                .with(as(id, "importer")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/trips/{id}/images/future", id)
                .param("evaluation_year", "2025")
                .with(as(id, "importer")))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
    var ndjson =
        mvc.perform(
                get("/api/v1/trips/{id}/trace", id)
                    .param("evaluation_year", "2023")
                    .accept("application/x-ndjson")
                    .with(as(id, "importer")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(ndjson.getHeader("ETag"))
        .isEqualTo("\"" + CoreConfig.sha256(ndjson.getContentAsByteArray()) + "\"");
    assertThat(ndjson.getContentAsString()).doesNotContain("future");
    mvc.perform(get("/api/v1/trips").param("evaluation_year", "2023").with(as(id, "importer")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].point_count").value(1));
    mvc.perform(
            get("/api/v1/trips/{id}/signs", id)
                .param("evaluation_year", "2025")
                .param("vehicle_type", "car")
                .with(as(id, "importer")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alert_params.warning_min_lead_s").value(5));
  }
}
