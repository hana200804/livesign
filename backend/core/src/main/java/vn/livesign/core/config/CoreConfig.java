package vn.livesign.core.config;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.livesign.core.reliability.ReliabilityParams;

/** Cấu hình đóng theo bytes; chỉ đọc value, không dùng proposed thay null. */
public final class CoreConfig {
  private final byte[] bytes;
  private final Map<String, Object> root;
  public CoreConfig(byte[] bytes) {
    this.bytes = bytes.clone();
    root = new Yaml(new SafeConstructor(new LoaderOptions())).load(new ByteArrayInputStream(bytes));
    Objects.requireNonNull(root, "params.yaml rỗng");
  }
  public static CoreConfig load(Path path) {
    try { return new CoreConfig(Files.readAllBytes(path)); }
    catch (IOException e) { throw new UncheckedIOException(e); }
  }
  public Object value(String path) {
    Object at = root;
    for (String key : path.split("\\.")) {
      if (!(at instanceof Map<?, ?> m) || !m.containsKey(key)) throw new IllegalArgumentException("Thiếu " + path);
      at = m.get(key);
    }
    return at instanceof Map<?, ?> m && m.containsKey("value") ? m.get("value") : at;
  }
  public double number(String path) {
    Object v = value(path);
    if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Cần số tại " + path);
    return n.doubleValue();
  }
  public boolean enabled(String path) { return Boolean.TRUE.equals(value(path)); }
  public String version() { return Objects.toString(root.get("params_version")); }
  public ReliabilityParams reliability() { return ReliabilityParams.fromParamsYaml(new ByteArrayInputStream(bytes)); }
  public byte[] bytes() { return bytes.clone(); }
  public String sha256() { return sha256(bytes); }
  public static String sha256(byte[] data) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
