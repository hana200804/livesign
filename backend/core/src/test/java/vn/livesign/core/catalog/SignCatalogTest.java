package vn.livesign.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignCatalogTest {

  /** Surefire chạy trong backend/core, nên file gốc nằm ở ../../ai/configs. */
  static final Path CLASSES = Path.of("..", "..", "ai", "configs", "classes.yaml");

  @Test
  void docDanhMucThatCuaRepo() {
    SignCatalog catalog = SignCatalog.load(CLASSES);

    assertEquals("qcvn12-2026-10-19", catalog.classesVersion());
    assertEquals(List.of("car", "truck", "motorcycle"), catalog.vehicleTypes());
    assertEquals(12, catalog.types().size());
    for (int i = 0; i < 12; i++) {
      assertEquals(i, catalog.types().get(i).classId());
    }
    assertEquals("P.102", catalog.types().get(0).code());
    assertEquals("I.407a", catalog.types().get(11).code());

    SignType p131a = catalog.byCode("P.131a").orElseThrow();
    assertEquals(7, p131a.classId());
    assertEquals("Cấm đỗ xe", p131a.nameVi());
    assertEquals(List.of("regulatory--no-parking--g1"), p131a.mapillaryLabelCandidates());
  }

  @Test
  void xeApDungTheoLoaiBien() {
    SignCatalog catalog = SignCatalog.load(CLASSES);
    SignType p103a = catalog.byCode("P.103a").orElseThrow();
    assertTrue(p103a.appliesTo("car"));
    assertTrue(p103a.appliesTo("truck"));
    assertFalse(p103a.appliesTo("motorcycle"));
    SignType p131a = catalog.byCode("P.131a").orElseThrow();
    assertTrue(p131a.appliesTo("motorcycle"));
  }

  @Test
  void tuChoiXeNgoaiDanhMuc() {
    String yaml = mau("[car, bus]", "prohibition");
    assertThrows(IllegalArgumentException.class, () -> load(yaml));
  }

  @Test
  void tuChoiNhomSai() {
    String yaml = mau("[car]", "speed");
    assertThrows(IllegalArgumentException.class, () -> load(yaml));
  }

  @Test
  void tuChoiThieuDinhNghia() {
    String yaml =
        """
        classes_version: "t"
        vehicle_types: [car]
        classes:
          0: P.102
        definitions: {}
        """;
    assertThrows(IllegalArgumentException.class, () -> load(yaml));
  }

  @Test
  void tuChoiClassIdNhay() {
    String yaml =
        """
        classes_version: "t"
        vehicle_types: [car]
        classes:
          1: P.102
        definitions:
          P.102: {name_vi: x, group: prohibition, vehicle_types: [car]}
        """;
    assertThrows(IllegalArgumentException.class, () -> load(yaml));
  }

  private static String mau(String vehicles, String group) {
    return """
        classes_version: "t"
        vehicle_types: [car, truck, motorcycle]
        classes:
          0: P.102
        definitions:
          P.102: {name_vi: x, group: %s, vehicle_types: %s}
        """
        .formatted(group, vehicles);
  }

  private static SignCatalog load(String yaml) {
    return SignCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
  }
}
