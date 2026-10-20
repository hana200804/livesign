package vn.livesign.app.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Cấp tài khoản rồi thoát, không mở API tự đăng ký:
 *
 * <pre>
 * LIVESIGN_NEW_USER_PASSWORD=... java -jar app.jar --create-user=demo_admin --role=admin
 * </pre>
 *
 * Mật khẩu đọc từ biến môi trường để không nằm trong lịch sử lệnh hay log.
 */
@Component
class CreateUserCommand implements ApplicationRunner {

  static final String PASSWORD_ENV = "LIVESIGN_NEW_USER_PASSWORD";
  private static final Logger log = LoggerFactory.getLogger(CreateUserCommand.class);

  private final AuthService auth;
  private final ConfigurableApplicationContext context;

  CreateUserCommand(AuthService auth, ConfigurableApplicationContext context) {
    this.auth = auth;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption("create-user")) {
      return;
    }
    int code = 0;
    try {
      String username = args.getOptionValues("create-user").get(0);
      Role role =
          args.containsOption("role") ? Role.fromDb(args.getOptionValues("role").get(0)) : Role.USER;
      String userId = auth.createUser(username, role, System.getenv(PASSWORD_ENV));
      log.info("Đã tạo tài khoản {} ({}), role={}", username, userId, role.dbValue());
    } catch (IllegalArgumentException e) {
      log.error("Không tạo được tài khoản: {}", e.getMessage());
      code = 1;
    }
    int exit = code;
    System.exit(SpringApplication.exit(context, () -> exit));
  }
}
