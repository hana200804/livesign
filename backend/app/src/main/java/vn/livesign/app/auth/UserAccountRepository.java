package vn.livesign.app.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

  public record UserAccount(String userId, String username, String passwordHash, Role role) {
    @Override
    public String toString() {
      return "UserAccount[userId=" + userId + ", username=" + username + ", role=" + role + "]";
    }
  }

  private final JdbcTemplate jdbc;

  UserAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UserAccount> findByUsername(String username) {
    List<UserAccount> rows =
        jdbc.query(
            "SELECT user_id, username, password_hash, role FROM `user` WHERE username = ?",
            (rs, i) ->
                new UserAccount(
                    rs.getString("user_id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    Role.fromDb(rs.getString("role"))),
            username);
    return rows.stream().findFirst();
  }

  public void insert(String userId, String username, String passwordHash, Role role) {
    jdbc.update(
        "INSERT INTO `user` (user_id, username, password_hash, role) VALUES (?, ?, ?, ?)",
        userId,
        username,
        passwordHash,
        role.dbValue());
  }
}
