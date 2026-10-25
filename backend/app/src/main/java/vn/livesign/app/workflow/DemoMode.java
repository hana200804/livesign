package vn.livesign.app.workflow;

import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** Đồng hồ lịch sử chỉ tồn tại trong profile/CSDL mô phỏng được khai rõ. */
@Configuration(proxyBeanMethods = false)
public class DemoMode {
  private final boolean active;
  private final String scenario;
  private final Clock clock;

  public DemoMode(Environment env) {
    active = env.acceptsProfiles(Profiles.of("simulation"));
    scenario = active ? env.getRequiredProperty("livesign.scenario-id") : null;
    if (active) {
      String url = env.getRequiredProperty("spring.datasource.url");
      String database = URI.create(url.substring("jdbc:".length())).getPath();
      if (!scenario.matches("[A-Za-z0-9._-]{1,64}")
          || database == null
          || !database.matches("/livesign_simulation(?:_[A-Za-z0-9_]+)?"))
        throw new IllegalStateException(
            "Mô phỏng cần scenario_id và CSDL livesign_simulation riêng");
      clock =
          Clock.fixed(
              Instant.parse(env.getRequiredProperty("livesign.simulation-now")), ZoneOffset.UTC);
    } else clock = Clock.systemUTC();
  }

  @Bean
  public Clock decisionClock() {
    return clock;
  }

  public boolean active() {
    return active;
  }

  public String scenario() {
    return scenario;
  }

  public Map<String, Object> labels() {
    return active
        ? Jsons.map(
            "scenario_id",
            scenario,
            "data_kind",
            "simulation",
            "simulation_now",
            clock.instant().toString())
        : Map.of();
  }
}
