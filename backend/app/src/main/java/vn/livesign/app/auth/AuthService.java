package vn.livesign.app.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import vn.livesign.app.auth.UserAccountRepository.UserAccount;
import vn.livesign.app.web.ApiException;

@Service
public class AuthService {

  public record Token(String accessToken, long expiresInS, Role role) {
    @Override
    public String toString() {
      return "Token[<ẩn>, expiresInS=" + expiresInS + ", role=" + role + "]";
    }
  }

  static final int MIN_PASSWORD_LENGTH = 12;

  private final UserAccountRepository users;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final JwtProperties props;
  /** So với hash giả khi không có user, để thời gian trả lời không lộ username có tồn tại. */
  private final String dummyHash;

  AuthService(
      UserAccountRepository users,
      PasswordEncoder passwords,
      JwtEncoder encoder,
      JwtProperties props) {
    this.users = users;
    this.passwords = passwords;
    this.encoder = encoder;
    this.props = props;
    this.dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  public Token login(String username, String password) {
    Optional<UserAccount> account = users.findByUsername(username);
    boolean ok = passwords.matches(password, account.map(UserAccount::passwordHash).orElse(dummyHash));
    if (account.isEmpty() || !ok) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu");
    }
    UserAccount user = account.get();
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("livesign")
            .subject(user.userId())
            .issuedAt(now)
            .expiresAt(now.plus(props.ttl()))
            .claim("username", user.username())
            .claim(SecurityConfig.ROLE_CLAIM, user.role().dbValue())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String jwt = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new Token(jwt, props.ttl().toSeconds(), user.role());
  }

  /** Tài khoản do nhóm cấp qua lệnh vận hành, không có API tự đăng ký. Trả user_id mới. */
  public String createUser(String username, Role role, String rawPassword) {
    if (username == null || username.isBlank() || username.length() > 80) {
      throw new IllegalArgumentException("username phải có 1–80 ký tự");
    }
    if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException("Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
    }
    if (users.findByUsername(username).isPresent()) {
      throw new IllegalArgumentException("username đã tồn tại: " + username);
    }
    String userId = "user:" + UUID.randomUUID();
    users.insert(userId, username, passwords.encode(rawPassword), role);
    return userId;
  }
}
