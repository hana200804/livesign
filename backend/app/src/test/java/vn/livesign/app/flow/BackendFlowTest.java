package vn.livesign.app.flow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.livesign.app.MySqlTestcontainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainer.class)
@Testcontainers(disabledWithoutDocker = true)
class BackendFlowTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @org.springframework.boot.test.mock.mockito.SpyBean vn.livesign.app.workflow.DbWriter writer;
  String account;
  String trip;

  @BeforeEach
  void user() {
    account = "flow:" + UUID.randomUUID();
    trip = "trip:" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO `user`(user_id,username,password_hash,role) VALUES (?,?,?,'importer')",
        account,
        account,
        "unused");
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
    return jwt()
        .jwt(j -> j.subject(account).claim("role", role))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
  }

  ObjectNode observation(String id, String date, String outcome) {
    var o = json.createObjectNode();
    o.put("observation_id", id)
        .put("source", "mapillary")
        .put("trip_id", trip)
        .put("sequence_id", trip)
        .put("creator_id", "mapillary:creator")
        .put("captured_at", date)
        .put("captured_at_precision", "instant")
        .put("image_id", id)
        .put("sign_type_code", "P.131a")
        .put("outcome", outcome)
        .put("visibility_method", "unverified")
        .put("camera_lon", 105.84)
        .put("camera_lat", 21.02)
        .put("camera_heading_deg", 0)
        .put("off_deg", 0)
        .put("is_pano", false)
        .put("focal", .8)
        .put("image_width_px", 2048)
        .put("image_height_px", 1152)
        .put("model_version", "test-model")
        .put("detector_confidence", .9);
    o.putArray("bbox_px").add(1000).add(400).add(1048).add(460);
    o.put("source_sign_id", "feature:" + trip);
    o.putObject("source_metadata")
        .putObject("positioning")
        .putArray("feature_candidates")
        .addObject()
        .put("source_sign_id", "feature:" + trip)
        .put("source_sign_type", "regulatory--no-parking--g1")
        .put("lon", 105.84)
        .put("lat", 21.02015)
        .put("ray_distance_m", 0);
    return o;
  }

  ObjectNode batch(JsonNode... observations) {
    var b =
        json.createObjectNode()
            .put("schema_version", "1.0")
            .put("classes_version", "qcvn12-2026-10-19");
    b.putObject("trip")
        .put("trip_id", trip)
        .put("source", "mapillary")
        .put("sequence_id", trip)
        .put("creator_id", "mapillary:creator");
    var a = b.putArray("observations");
    for (var o : observations) a.add(o);
    return b;
  }

  JsonNode ingest(ObjectNode b) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/v1/observations/batch")
                    .with(as("importer"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(b)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray());
  }

  @Test
  void nhapLaiKhongNhanBangChungVaIdKhacNoiDungBiTuChoi() throws Exception {
    String id = "obs:" + UUID.randomUUID();
    var o = observation(id, "2025-01-01T00:00:00Z", "seen");
    assertThat(ingest(batch(o)).path("accepted").asInt()).isEqualTo(1);
    assertThat(ingest(batch(o)).path("duplicates").asInt()).isEqualTo(1);
    o.put("detector_confidence", .8);
    var response = ingest(batch(o));
    assertThat(response.path("rejected").asInt()).isEqualTo(1);
    assertThat(response.at("/items/0/error/code").asText()).isEqualTo("OBSERVATION_ID_CONFLICT");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM observation WHERE observation_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void snapshotNamCuKhongDocAnhTuongLaiVaGiuRunCu() throws Exception {
    String prefix = UUID.randomUUID().toString();
    var a = observation(prefix + ":a", "2023-01-01T00:00:00Z", "seen");
    var b = observation(prefix + ":b", "2023-01-01T00:00:01Z", "seen");
    var first = ingest(batch(a, b));
    String sign = first.at("/items/0/target_sign_id").asText();
    String body =
        mvc.perform(
                get("/api/v1/signs/{id}", sign).param("evaluation_year", "2023").with(as("user")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("candidate"))
            .andExpect(jsonPath("$.reliability.positive_pair_count").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String run = json.readTree(body).path("snapshot_run_id").asText();
    var future =
        observation(prefix + ":c", "2025-01-01T00:00:00Z", "seen").put("camera_lat", 21.02001);
    ingest(batch(future));
    mvc.perform(
            get("/api/v1/signs/{id}", sign)
                .param("evaluation_year", "2023")
                .param("snapshot_run_id", run)
                .with(as("user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot_run_id").value(run))
        .andExpect(jsonPath("$.reliability.positive_pair_count").value(1));
    mvc.perform(
            get("/api/v1/signs/{id}", sign)
                .param("evaluation_year", "2025")
                .param("snapshot_run_id", run)
                .with(as("user")))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void loiTungDongKhongMatDongTotVaAmChuaBatBiTuChoi() throws Exception {
    var bad = observation("bad:" + UUID.randomUUID(), "2025-01-01T00:00:00Z", "not_seen");
    var good = observation("good:" + UUID.randomUUID(), "2025-01-01T00:00:00Z", "seen");
    var r = ingest(batch(bad, good));
    assertThat(r.path("accepted").asInt()).isEqualTo(1);
    assertThat(r.path("rejected").asInt()).isEqualTo(1);
  }

  @Test
  void envelopeSaiVaLoQuaLonBiTuChoi() throws Exception {
    var o = observation("o", "2025-01-01T00:00:00Z", "seen").put("trip_id", "other");
    mvc.perform(
            post("/api/v1/observations/batch")
                .with(as("importer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(batch(o))))
        .andExpect(status().isBadRequest());
    var many = batch();
    for (int i = 0; i < 501; i++) many.withArray("observations").add(o);
    mvc.perform(
            post("/api/v1/observations/batch")
                .with(as("importer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(many)))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void apiDocTuChoiNamSaiVaQuyenAdmin() throws Exception {
    mvc.perform(
            get("/api/v1/signs")
                .param("evaluation_year", "2022")
                .param("bbox", "105,20,106,22")
                .with(as("user")))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(post("/api/v1/admin/snapshots/rebuild").with(as("user")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/reviews").param("evaluation_year", "2025").with(as("importer")))
        .andExpect(status().isForbidden());
  }

  @Test
  void moCaVaQuyetDinhKiemVersionKhongDoiBanDoQuaKhu() throws Exception {
    var response =
        ingest(batch(observation("manual:" + UUID.randomUUID(), "2025-01-01T00:00:00Z", "seen")));
    String sign = response.at("/items/0/target_sign_id").asText();
    String open = json.writeValueAsString(Map.of("sign_id", sign, "reason", "Kiểm tra thủ công"));
    String first =
        mvc.perform(
                post("/api/v1/admin/reviews")
                    .with(as("admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(open))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String review = json.readTree(first).path("review_id").asText();
    mvc.perform(
            post("/api/v1/admin/reviews")
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(open))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.review_id").value(review));
    String decision =
        json.writeValueAsString(
            Map.of(
                "expected_version",
                1,
                "decision",
                "confirm",
                "effective_at",
                "2025-01-01T00:00:00Z",
                "reason",
                "Đã kiểm tra ảnh"));
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.sign_status").value("confirmed_manual"));
    mvc.perform(
            post("/api/v1/admin/reviews/{id}/decision", review)
                .with(as("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/signs/{id}", sign).param("evaluation_year", "2025").with(as("user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("candidate"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM sign_history WHERE sign_id=? AND event_type='admin_decision'",
                Integer.class,
                sign))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM sign WHERE sign_id=?", String.class, sign))
        .isEqualTo("confirmed_manual");
  }

  @Test
  void cursorBiRangBuocVoiBoLocVaSnapshot() throws Exception {
    ingest(batch(observation("page:" + UUID.randomUUID(), "2025-01-01T00:00:00Z", "seen")));
    var body =
        mvc.perform(
                get("/api/v1/signs")
                    .param("evaluation_year", "2025")
                    .param("bbox", "105,20,106,22")
                    .param("limit", "1")
                    .with(as("user")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    var response = json.readTree(body);
    String cursor = response.path("next_cursor").asText(null);
    if (cursor != null)
      mvc.perform(
              get("/api/v1/signs")
                  .param("evaluation_year", "2023")
                  .param("bbox", "105,20,106,22")
                  .param("limit", "1")
                  .param("cursor", cursor)
                  .with(as("user")))
          .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/signs")
                .param("evaluation_year", "2025")
                .param("bbox", "105,20,106,22")
                .param("cursor", "forged.cursor")
                .with(as("user")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void daLuuNhungDungLoiThiRetryKhongNhanBangChung() throws Exception {
    var baseline =
        ingest(batch(observation("base:" + UUID.randomUUID(), "2025-01-01T00:00:00Z", "seen")));
    String oldRun =
        jdbc.queryForObject(
            "SELECT run_id FROM snapshot_run WHERE evaluation_year=2025 AND status='ready'",
            String.class);
    var next = batch(observation("failure:" + UUID.randomUUID(), "2025-01-02T00:00:00Z", "seen"));
    org.mockito.Mockito.doAnswer(
            invocation -> {
              if ("sign_snapshot".equals(invocation.getArgument(0)))
                throw new IllegalStateException("injected snapshot failure");
              return invocation.callRealMethod();
            })
        .when(writer)
        .insert(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
    try {
      mvc.perform(
              post("/api/v1/observations/batch")
                  .with(as("importer"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsBytes(next)))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.accepted").value(1))
          .andExpect(jsonPath("$.error.code").value("SNAPSHOT_BUILD_FAILED"));
      assertThat(
              jdbc.queryForObject(
                  "SELECT run_id FROM snapshot_run WHERE evaluation_year=2025 AND status='ready'",
                  String.class))
          .isEqualTo(oldRun);
    } finally {
      org.mockito.Mockito.reset(writer);
    }
    var retried = ingest(next);
    assertThat(retried.path("duplicates").asInt()).isEqualTo(1);
    assertThat(retried.path("accepted").asInt()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT run_id FROM snapshot_run WHERE evaluation_year=2025 AND status='ready'",
                String.class))
        .isNotEqualTo(oldRun);
  }

  @Test
  void jdbcGiuUtcTaiBienNam() throws Exception {
    String id = "utc:" + UUID.randomUUID();
    ingest(batch(observation(id, "2023-12-31T23:59:59Z", "seen")));
    assertThat(
            jdbc.queryForObject(
                "SELECT DATE_FORMAT(captured_at,'%Y-%m-%d %H:%i:%s') FROM observation WHERE"
                    + " observation_id=?",
                String.class, id))
        .isEqualTo("2023-12-31 23:59:59");
    assertThat(
            jdbc.queryForObject(
                "SELECT captured_year FROM observation WHERE observation_id=?", Integer.class, id))
        .isEqualTo(2023);
  }

  @Test
  void haiClientChiMotQuyetDinhCungVersion() throws Exception {
    String sign =
        ingest(batch(observation("race:" + UUID.randomUUID(), "2025-01-01T00:00:00Z", "seen")))
            .at("/items/0/target_sign_id")
            .asText();
    String opened =
        mvc.perform(
                post("/api/v1/admin/reviews")
                    .with(as("admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsBytes(
                            Map.of("sign_id", sign, "reason", "Kiểm tra cạnh tranh"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String review = json.readTree(opened).path("review_id").asText();
    byte[] request =
        json.writeValueAsBytes(
            Map.of(
                "expected_version",
                1,
                "decision",
                "defer",
                "effective_at",
                "2025-01-01T00:00:00Z",
                "reason",
                "Hai client đồng thời"));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var task =
          (java.util.concurrent.Callable<Integer>)
              () ->
                  mvc.perform(
                          post("/api/v1/admin/reviews/{id}/decision", review)
                              .with(as("admin"))
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(request))
                      .andReturn()
                      .getResponse()
                      .getStatus();
      var a = pool.submit(task);
      var b = pool.submit(task);
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM sign_history WHERE sign_id=? AND event_type='admin_decision'",
                Integer.class,
                sign))
        .isEqualTo(1);
  }
}
