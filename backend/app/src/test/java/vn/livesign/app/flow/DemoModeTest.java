package vn.livesign.app.flow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.livesign.app.workflow.DemoMode;

class DemoModeTest {
  @Test
  void vanHanhKhongLayDongHoMoPhong() {
    var env = new MockEnvironment().withProperty("livesign.simulation-now", "2025-01-01T00:00:00Z");
    var mode = new DemoMode(env);
    assertFalse(mode.active());
    assertTrue(mode.labels().isEmpty());
    assertEquals(ZoneOffset.UTC, mode.decisionClock().getZone());
  }

  @Test
  void moPhongCanDbRiengVaCoNhan() {
    var env =
        new MockEnvironment()
            .withProperty("livesign.scenario-id", "test")
            .withProperty("livesign.simulation-now", "2025-01-01T00:00:00Z")
            .withProperty("spring.datasource.url", "jdbc:mysql://localhost/livesign");
    env.setActiveProfiles("simulation");
    assertThrows(IllegalStateException.class, () -> new DemoMode(env));
    env.setProperty("spring.datasource.url", "jdbc:mysql://localhost/livesign_simulation_test");
    var mode = new DemoMode(env);
    assertTrue(mode.active());
    assertEquals(Instant.parse("2025-01-01T00:00:00Z"), mode.decisionClock().instant());
    assertEquals("simulation", mode.labels().get("data_kind"));
  }
}
