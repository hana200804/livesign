package vn.livesign.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import(MySqlTestcontainer.class)
@Testcontainers(disabledWithoutDocker = true)
class LivesignApplicationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  void khoiDongVoiMySql8() {
    String version = jdbc.queryForObject("SELECT VERSION()", String.class);
    assertThat(version).startsWith("8.");
  }
}
