package vn.livesign.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** MySQL 8.0 thật cho test tích hợp, cùng image và múi giờ với docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainer {

  @Bean
  @ServiceConnection
  MySQLContainer<?> mysql() {
    return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withCommand(
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_0900_ai_ci",
            "--default-time-zone=+00:00");
  }
}
