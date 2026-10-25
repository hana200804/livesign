package vn.livesign.core.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Danh mục loại biển đọc từ ai/configs/classes.yaml, nguồn duy nhất cho AI, Backend và Android.
 *
 * <p>Bộ đọc kiểm tra file thay vì sửa: class_id liên tục từ 0, mỗi mã có định nghĩa, loại xe và
 * nhóm biển thuộc tập cho phép. File sai thì ném {@link IllegalArgumentException}.
 */
public record SignCatalog(String classesVersion, List<String> vehicleTypes, List<SignType> types) {

  private static final Set<String> GROUPS = Set.of("prohibition", "warning", "information");

  public SignCatalog {
    vehicleTypes = List.copyOf(vehicleTypes);
    types = List.copyOf(types);
  }

  public Optional<SignType> byCode(String code) {
    return types.stream().filter(t -> t.code().equals(code)).findFirst();
  }

  public static SignCatalog load(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      return load(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Không đọc được " + file, e);
    }
  }

  public static SignCatalog load(InputStream in) {
    Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    if (root == null) {
      throw new IllegalArgumentException("classes.yaml rỗng");
    }
    String version = requireString(root, "classes_version");
    List<String> vehicles = stringList(root.get("vehicle_types"), "vehicle_types");
    Map<?, ?> classes = requireMap(root, "classes");
    Map<?, ?> definitions = requireMap(root, "definitions");

    List<SignType> types = new ArrayList<>();
    for (int id = 0; id < classes.size(); id++) {
      Object code = classes.get(id);
      if (!(code instanceof String c)) {
        throw new IllegalArgumentException("classes phải có class_id liên tục từ 0, thiếu " + id);
      }
      if (!(definitions.get(c) instanceof Map<?, ?> def)) {
        throw new IllegalArgumentException("Thiếu definitions cho " + c);
      }
      String group = requireString(def, "group");
      if (!GROUPS.contains(group)) {
        throw new IllegalArgumentException(c + ": group không hợp lệ: " + group);
      }
      List<String> applies = stringList(def.get("vehicle_types"), c + ".vehicle_types");
      if (applies.isEmpty() || !vehicles.containsAll(applies)) {
        throw new IllegalArgumentException(c + ": vehicle_types ngoài danh mục: " + applies);
      }
      List<String> labels =
          def.get("mapillary_label_candidates") == null
              ? List.of()
              : stringList(
                  def.get("mapillary_label_candidates"), c + ".mapillary_label_candidates");
      types.add(new SignType(id, c, requireString(def, "name_vi"), group, applies, labels));
    }
    if (types.stream().map(SignType::code).distinct().count() != types.size()) {
      throw new IllegalArgumentException("classes có mã lặp");
    }
    return new SignCatalog(version, vehicles, types);
  }

  private static String requireString(Map<?, ?> map, String key) {
    if (map.get(key) instanceof String s && !s.isBlank()) {
      return s;
    }
    throw new IllegalArgumentException("Thiếu chuỗi " + key);
  }

  private static Map<?, ?> requireMap(Map<?, ?> map, String key) {
    if (map.get(key) instanceof Map<?, ?> m) {
      return m;
    }
    throw new IllegalArgumentException("Thiếu " + key);
  }

  private static List<String> stringList(Object value, String name) {
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(name + " phải là danh sách");
    }
    List<String> out = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof String s)) {
        throw new IllegalArgumentException(name + " chỉ chứa chuỗi");
      }
      out.add(s);
    }
    return out;
  }
}
