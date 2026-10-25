package vn.livesign.core.reliability;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Tham số mục reliability của config/params.yaml.
 *
 * <p>Chỉ đọc trường {@code value}; {@code proposed} không bao giờ được dùng thay. N/θ null thì
 * không tự xác nhận; θ_thấp null thì dấu hiệu (b) báo chưa tính được.
 */
public record ReliabilityParams(
    Integer n,
    Double theta,
    Double thetaLow,
    int h,
    int k,
    double alphaPrior,
    double betaPrior,
    double decayBase) {

  public ReliabilityParams {
    if (k < 1 || h < 0 || alphaPrior < 1 || betaPrior < 1 || decayBase <= 0 || decayBase > 1) {
      throw new IllegalArgumentException("Tham số reliability ngoài miền hợp lệ");
    }
    if (theta != null && thetaLow != null && thetaLow >= theta) {
      throw new IllegalArgumentException("theta_low phải nhỏ hơn theta");
    }
  }

  public boolean autoConfirmEnabled() {
    return n != null && theta != null;
  }

  public static ReliabilityParams fromParamsYaml(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      return fromParamsYaml(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Không đọc được " + file, e);
    }
  }

  public static ReliabilityParams fromParamsYaml(InputStream in) {
    Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    if (root == null || !(root.get("reliability") instanceof Map<?, ?> r)) {
      throw new IllegalArgumentException("params.yaml thiếu mục reliability");
    }
    return new ReliabilityParams(
        integer(r, "N", false),
        decimal(r, "theta", false),
        decimal(r, "theta_low", false),
        integer(r, "H", true),
        integer(r, "K", true),
        decimal(r, "alpha_prior", true),
        decimal(r, "beta_prior", true),
        decimal(r, "decay_base", true));
  }

  private static Object value(Map<?, ?> section, String key, boolean required) {
    if (!(section.get(key) instanceof Map<?, ?> entry) || !entry.containsKey("value")) {
      throw new IllegalArgumentException("reliability." + key + " thiếu value");
    }
    Object v = entry.get("value");
    if (v == null && required) {
      throw new IllegalArgumentException("reliability." + key + " phải có value");
    }
    return v;
  }

  private static Integer integer(Map<?, ?> section, String key, boolean required) {
    Object v = value(section, key, required);
    if (v == null) {
      return null;
    }
    if (v instanceof Integer i) {
      return i;
    }
    throw new IllegalArgumentException("reliability." + key + " phải là số nguyên");
  }

  private static Double decimal(Map<?, ?> section, String key, boolean required) {
    Object v = value(section, key, required);
    if (v == null) {
      return null;
    }
    if (v instanceof Number num) {
      return num.doubleValue();
    }
    throw new IllegalArgumentException("reliability." + key + " phải là số");
  }
}
