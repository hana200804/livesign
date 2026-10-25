package vn.livesign.core.spatial;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.livesign.core.config.CoreConfig;
import vn.livesign.core.catalog.SignCatalog;

class PipelineTest {
  final CoreConfig params = CoreConfig.load(Path.of("../../config/params.yaml"));
  final SignCatalog catalog = SignCatalog.load(Path.of("../../ai/configs/classes.yaml"));
  final Geo.Point p = new Geo.Point(105.84, 21.02);
  Pipeline.Detection seen(String id, String image, double x, double heading) {
    return new Pipeline.Detection(id, "seq", "creator", Instant.parse("2025-01-01T00:00:0" + x + "Z"),
        "instant", image, "P.131a", Geo.offset(p, x, 0), heading, 0.0,
        10.0, "feature", List.of(new Pipeline.Feature("feature", "regulatory--no-parking--g1", Geo.offset(p, 5, 10))), null, "seen");
  }
  @Test void featureGomNhieuAnhVaIdOnDinh() {
    var a = seen("a", "i1", 0, 26.565051177);
    var b = seen("b", "i2", 5, 0);
    var result = Pipeline.build(List.of(b, a), params, catalog);
    assertEquals(1, result.size());
    assertEquals(2, result.getFirst().observations().size());
    assertEquals("ray_least_squares", result.getFirst().locationMethod());
    assertEquals(result.getFirst().id(), Pipeline.build(List.of(a, b), params, catalog).getFirst().id());
  }
  @Test void haiBboxCungAnhKhongThanhMotTam() {
    assertEquals(2, Pipeline.build(List.of(seen("a", "image", 0, 0), seen("b", "image", 0, 1)), params, catalog).size());
  }
  @Test void unknownVaAmKhongTuTaoBien() {
    var a = seen("a", "i", 0, 0);
    var unknown = new Pipeline.Detection(a.id(), a.sequence(), a.creator(), a.time(), "instant", a.image(), a.type(),
        a.camera(), a.heading(), a.off(), a.boxHeight(), null, List.of(), null, "unknown");
    assertTrue(Pipeline.build(List.of(unknown), params, catalog).isEmpty());
  }
}
