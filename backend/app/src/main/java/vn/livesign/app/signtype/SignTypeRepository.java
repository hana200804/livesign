package vn.livesign.app.signtype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.livesign.core.catalog.SignCatalog;
import vn.livesign.core.catalog.SignType;

@Repository
public class SignTypeRepository {

  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  SignTypeRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Ghi đủ danh mục vào sign_type; chạy lại với cùng file không đổi gì. */
  @Transactional
  public void upsert(SignCatalog catalog) {
    for (SignType t : catalog.types()) {
      jdbc.update(
          """
          INSERT INTO sign_type (sign_type_code, class_id, classes_version, name_vi, sign_group,
                                 vehicle_types)
          VALUES (?, ?, ?, ?, ?, CAST(? AS JSON)) AS n
          ON DUPLICATE KEY UPDATE class_id = n.class_id, classes_version = n.classes_version,
            name_vi = n.name_vi, sign_group = n.sign_group, vehicle_types = n.vehicle_types
          """,
          t.code(),
          t.classId(),
          catalog.classesVersion(),
          t.nameVi(),
          t.group(),
          toJson(t.vehicleTypes()));
    }
  }

  public List<SignTypeItem> findByClassesVersion(String classesVersion) {
    return jdbc.query(
        """
        SELECT class_id, sign_type_code, name_vi, vehicle_types FROM sign_type
        WHERE classes_version = ? ORDER BY class_id
        """,
        (rs, i) ->
            new SignTypeItem(
                rs.getInt("class_id"),
                rs.getString("sign_type_code"),
                rs.getString("name_vi"),
                fromJson(rs.getString("vehicle_types"))),
        classesVersion);
  }

  private String toJson(List<String> values) {
    try {
      return json.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<String> fromJson(String value) {
    try {
      return json.readValue(value, STRINGS);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("vehicle_types không phải mảng chuỗi: " + value, e);
    }
  }
}
