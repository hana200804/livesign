package vn.livesign.app.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

  record LoginRequest(@NotBlank @Size(max = 80) String username, @NotBlank String password) {
    @Override
    public String toString() {
      return "LoginRequest[username=" + username + ", password=<ẩn>]";
    }
  }

  record LoginResponse(String accessToken, String tokenType, long expiresInS, String role) {}

  private final AuthService auth;

  AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  LoginResponse login(@Valid @RequestBody LoginRequest request) {
    AuthService.Token token = auth.login(request.username(), request.password());
    return new LoginResponse(
        token.accessToken(), "Bearer", token.expiresInS(), token.role().dbValue());
  }
}
