package vn.livesign.app.workflow;

import com.fasterxml.jackson.databind.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import vn.livesign.app.web.ApiException;
import vn.livesign.core.config.CoreConfig;

public final class Jsons {
  private Jsons() {}

  public static Map<String, Object> map(Object... entries) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < entries.length; i += 2) m.put((String) entries[i], entries[i + 1]);
    return m;
  }

  public static String string(JsonNode n, String key) {
    JsonNode v = n.get(key);
    return v == null || v.isNull() ? null : v.isTextual() ? v.asText() : null;
  }

  public static Double number(JsonNode n, String key) {
    var v = n.get(key);
    return v == null || v.isNull()
        ? null
        : v.isNumber() && Double.isFinite(v.asDouble()) ? v.asDouble() : null;
  }

  /** DATETIME lưu UTC dạng lịch, không để múi giờ JVM đổi ngày/năm khi JDBC bind. */
  public static LocalDateTime timestamp(Instant t) {
    return t == null ? null : LocalDateTime.ofInstant(t, ZoneOffset.UTC);
  }

  public static Instant instant(Object t) {
    return t == null
        ? null
        : t instanceof Timestamp ts
            ? ts.toLocalDateTime().toInstant(ZoneOffset.UTC)
            : ((LocalDateTime) t).toInstant(ZoneOffset.UTC);
  }

  public static ApiException error(int code, String name, String message) {
    return new ApiException(HttpStatus.valueOf(code), name, message);
  }

  public static JsonNode parse(ObjectMapper json, String text) {
    try {
      return json.readTree(text);
    } catch (Exception e) {
      throw new IllegalStateException("JSON lưu trữ không hợp lệ", e);
    }
  }

  public static String write(ObjectMapper json, Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static JsonNode canonical(JsonNode n, ObjectMapper json) {
    if (n.isObject()) {
      var out = json.createObjectNode();
      var keys = new TreeSet<String>();
      n.fieldNames().forEachRemaining(keys::add);
      for (var k : keys) if (!n.get(k).isNull()) out.set(k, canonical(n.get(k), json));
      return out;
    }
    if (n.isArray()) {
      var out = json.createArrayNode();
      n.forEach(v -> out.add(canonical(v, json)));
      return out;
    }
    if (n.isNumber())
      return json.getNodeFactory().numberNode(n.decimalValue().stripTrailingZeros());
    return n;
  }

  public static String hash(ObjectMapper json, Object o) {
    return CoreConfig.sha256(
        write(json, canonical(json.valueToTree(o), json))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
