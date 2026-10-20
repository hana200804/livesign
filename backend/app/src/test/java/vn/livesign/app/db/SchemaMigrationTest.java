package vn.livesign.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.livesign.app.MySqlTestcontainer;

/** Tiêu chí duyệt DDL trong docs/contracts/db-schema.md, chạy trên MySQL 8 thật. */
@SpringBootTest
@Import(MySqlTestcontainer.class)
@Testcontainers(disabledWithoutDocker = true)
class SchemaMigrationTest {

  private static final String POINT =
      "ST_GeomFromText('POINT(105.84715 21.02537)', 4326, 'axis-order=long-lat')";

  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void xoaDuLieuThu() {
    for (String t :
        List.of(
            "sign_snapshot_observation", "sign_snapshot", "snapshot_run", "review",
            "sign_history", "observation", "sign", "trip", "vehicle_profile", "sign_type",
            "`user`")) {
      jdbc.update("DELETE FROM " + t);
    }
  }

  @Test
  void duMuoiMotBang() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()"
                + " AND table_name <> 'flyway_schema_history'",
            String.class);
    assertThat(tables)
        .containsExactlyInAnyOrder(
            "user", "vehicle_profile", "sign_type", "trip", "sign", "observation",
            "sign_history", "review", "data_state", "snapshot_run", "sign_snapshot",
            "sign_snapshot_observation");
    assertThat(jdbc.queryForObject("SELECT input_seq FROM data_state WHERE id = 1", Long.class))
        .isZero();
  }

  @Test
  void signCoSridVaSpatialIndex() {
    String ddl = jdbc.queryForMap("SHOW CREATE TABLE sign").get("Create Table").toString();
    assertThat(ddl).contains("SRID 4326").contains("SPATIAL KEY `sx_sign_location`");
  }

  @Test
  void pointGiuDungThuTuLonLat() {
    themSignType("P.131a", 7);
    jdbc.update(
        "INSERT INTO sign (sign_id, sign_type_code, location, conflict_reasons, params_version)"
            + " VALUES ('s1', 'P.131a', " + POINT + ", JSON_ARRAY(), 'test')");
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT ST_SRID(location) AS srid, ST_Longitude(location) AS lon,"
                + " ST_Latitude(location) AS lat FROM sign WHERE sign_id = 's1'");
    assertThat(((Number) row.get("srid")).intValue()).isEqualTo(4326);
    assertThat((Double) row.get("lon")).isEqualTo(105.84715);
    assertThat((Double) row.get("lat")).isEqualTo(21.02537);
  }

  @Test
  void fkSaiBiTuChoi() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO sign (sign_id, sign_type_code, location, conflict_reasons,"
                        + " params_version) VALUES ('s1', 'X.999', " + POINT
                        + ", JSON_ARRAY(), 'test')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void idKhacHoaThuongKhongBiGop() {
    themUser("u1");
    jdbc.update(
        "INSERT INTO trip (trip_id, source, sequence_id, user_id) VALUES"
            + " ('mapillary:Seq', 'mapillary', 'Seq', 'u1'),"
            + " ('mapillary:seq', 'mapillary', 'seq', 'u1')");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip", Integer.class)).isEqualTo(2);
  }

  @Test
  void notSeenQuaBaMuoiMetBiChan() {
    themUser("u1");
    themSignType("P.131a", 7);
    jdbc.update(
        "INSERT INTO trip (trip_id, source, sequence_id, user_id) VALUES ('t1', 'mapillary', 'q', 'u1')");
    jdbc.update(
        "INSERT INTO sign (sign_id, sign_type_code, location, conflict_reasons, params_version)"
            + " VALUES ('s1', 'P.131a', " + POINT + ", JSON_ARRAY(), 'test')");
    String insert =
        "INSERT INTO observation (observation_id, schema_version, classes_version, payload_sha256,"
            + " source, trip_id, sequence_id, creator_id, captured_at, captured_at_precision,"
            + " image_id, target_sign_id, sign_type_code, camera_lon, camera_lat,"
            + " camera_heading_deg, sign_lon, sign_lat, sign_facing_deg, distance_m, outcome,"
            + " in_fov, facing_camera, sharp, unoccluded, visibility_method, model_version,"
            + " ingest_status, uploaded_by) VALUES (?, '1.0', 'v', REPEAT('a', 64), 'mapillary',"
            + " 't1', 'q', 'mapillary:c', '2025-06-02 02:00:00', 'instant', 'img', 's1', 'P.131a',"
            + " 105.8, 21.0, 0, 105.8, 21.0, 180, ?, 'not_seen', TRUE, TRUE, TRUE, TRUE, 'human',"
            + " 'm', 'accepted', 'u1')";
    jdbc.update(insert, "o-30", 30.0);
    // Lỗi CHECK của MySQL (3819) không được Spring phân loại, nên kiểm theo tên ràng buộc.
    assertThatThrownBy(() -> jdbc.update(insert, "o-30.01", 30.01))
        .hasMessageContaining("ck_observation_negative");
    assertThat(
            jdbc.queryForObject(
                "SELECT captured_year FROM observation WHERE observation_id = 'o-30'",
                Integer.class))
        .isEqualTo(2025);
  }

  @Test
  void motCaPendingMoiSign() {
    themSignType("P.131a", 7);
    jdbc.update(
        "INSERT INTO sign (sign_id, sign_type_code, location, conflict_reasons, params_version)"
            + " VALUES ('s1', 'P.131a', " + POINT + ", JSON_ARRAY(), 'test')");
    jdbc.update(
        "INSERT INTO review (review_id, sign_id, conflict_reasons) VALUES ('r1', 's1', JSON_ARRAY())");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO review (review_id, sign_id, conflict_reasons)"
                        + " VALUES ('r2', 's1', JSON_ARRAY())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void themUser(String id) {
    jdbc.update(
        "INSERT INTO `user` (user_id, username, password_hash) VALUES (?, ?, 'x')", id, id);
  }

  private void themSignType(String code, int classId) {
    jdbc.update(
        "INSERT INTO sign_type (sign_type_code, class_id, classes_version, name_vi, sign_group,"
            + " vehicle_types) VALUES (?, ?, 'test', 'thu', 'prohibition', JSON_ARRAY('car'))",
        code,
        classId);
  }
}
