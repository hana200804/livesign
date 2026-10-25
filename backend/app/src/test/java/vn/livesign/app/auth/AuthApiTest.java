package vn.livesign.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.livesign.app.MySqlTestcontainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainer.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthApiTest {

  /** Mật khẩu sinh trong test, không phải tài khoản thật. */
  static final String PASSWORD = "mat-khau-test-" + System.nanoTime();

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthService auth;
  @Autowired ObjectMapper json;

  @BeforeEach
  void taoTaiKhoan() {
    jdbc.update("DELETE FROM `user` WHERE username LIKE 'test_%'");
    auth.createUser("test_admin", Role.ADMIN, PASSWORD);
    auth.createUser("test_user", Role.USER, PASSWORD);
  }

  @Test
  void dangNhapDungTraJwt() throws Exception {
    String body =
        mvc.perform(login("test_admin", PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in_s").value(3600))
            .andExpect(jsonPath("$.role").value("admin"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("password").doesNotContain(PASSWORD);

    String token = json.readTree(body).get("access_token").asText();
    mvc.perform(get("/api/v1/sign-types").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void matKhauLuuBcrypt() {
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM `user` WHERE username = 'test_admin'", String.class);
    assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
  }

  @Test
  void saiMatKhauVaKhongCoUserCungMotLoi() throws Exception {
    JsonNode wrong =
        loi(
            mvc.perform(login("test_admin", "sai-mat-khau-123"))
                .andExpect(status().isUnauthorized()));
    JsonNode missing =
        loi(mvc.perform(login("test_khong_co", PASSWORD)).andExpect(status().isUnauthorized()));
    assertThat(wrong.at("/error/code").asText()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(missing.at("/error/code").asText()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(wrong.at("/error/message")).isEqualTo(missing.at("/error/message"));
    assertThat(wrong.at("/error/request_id").asText()).startsWith("req-");
  }

  @Test
  void thieuTruongTra400() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_admin\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.error.fields[0]").value("password"));
  }

  @Test
  void khongCoTokenTra401Json() throws Exception {
    mvc.perform(get("/api/v1/sign-types"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void tokenBiSuaTra401() throws Exception {
    String token = auth.login("test_user", PASSWORD).accessToken();
    String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
    mvc.perform(get("/api/v1/sign-types").header("Authorization", "Bearer " + tampered))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void userKhongVaoDuocAdmin() throws Exception {
    String token = auth.login("test_user", PASSWORD).accessToken();
    mvc.perform(get("/api/v1/admin/reviews").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void khongTaoTaiKhoanYeuHoacTrung() {
    assertThatThrownBy(() -> auth.createUser("test_x", Role.USER, "ngan"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> auth.createUser("test_admin", Role.USER, PASSWORD))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void khoaNganBiTuChoi() {
    assertThatThrownBy(() -> new JwtProperties("ngan-qua", Duration.ofHours(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(new JwtProperties("x".repeat(32), Duration.ofHours(1)).toString())
        .doesNotContain("xxxx");
  }

  private static org.springframework.test.web.servlet.RequestBuilder login(
      String username, String password) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
  }

  private JsonNode loi(org.springframework.test.web.servlet.ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }
}
