package vn.livesign.app.signtype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.livesign.app.MySqlTestcontainer;
import vn.livesign.core.catalog.SignCatalog;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainer.class)
@Testcontainers(disabledWithoutDocker = true)
class SignTypeApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignTypeRepository repository;
  @Autowired SignCatalog catalog;

  @Test
  void napLaiKhongNhanDong() {
    repository.upsert(catalog);
    repository.upsert(catalog);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sign_type", Integer.class)).isEqualTo(12);
    assertThat(
            jdbc.queryForObject(
                "SELECT JSON_LENGTH(vehicle_types) FROM sign_type WHERE sign_type_code = 'P.103a'",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  void traDuMuoiHaiLoaiTheoClassId() throws Exception {
    repository.upsert(catalog);
    mvc.perform(get("/api/v1/sign-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.classes_version").value("qcvn12-2026-10-19"))
        .andExpect(jsonPath("$.items.length()").value(12))
        .andExpect(jsonPath("$.items[0].class_id").value(0))
        .andExpect(jsonPath("$.items[0].sign_type_code").value("P.102"))
        .andExpect(jsonPath("$.items[7].sign_type_code").value("P.131a"))
        .andExpect(jsonPath("$.items[7].name_vi").value("Cấm đỗ xe"))
        .andExpect(jsonPath("$.items[7].vehicle_types.length()").value(3))
        .andExpect(jsonPath("$.items[1].vehicle_types[0]").value("car"))
        .andExpect(jsonPath("$.items[1].vehicle_types[1]").value("truck"));
  }
}
