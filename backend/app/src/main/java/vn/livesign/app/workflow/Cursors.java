package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import vn.livesign.app.auth.JwtProperties;

@Component
public class Cursors {
  private final ObjectMapper json;
  private final byte[] secret;

  public Cursors(ObjectMapper json, JwtProperties properties) {
    this.json = json;
    secret = properties.secret().getBytes(StandardCharsets.UTF_8);
  }

  private byte[] sign(byte[] payload) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public int offset(String cursor, Object binding) {
    if (cursor == null) return 0;
    try {
      if (cursor.length() > 2048) throw new IllegalArgumentException();
      var parts = cursor.split("\\.");
      if (parts.length != 2) throw new IllegalArgumentException();
      byte[] data = Base64.getUrlDecoder().decode(parts[0]);
      if (!MessageDigest.isEqual(sign(data), Base64.getUrlDecoder().decode(parts[1])))
        throw new IllegalArgumentException();
      var o = json.readTree(data);
      if (!hash(json, binding).equals(o.path("binding").asText())
          || !o.path("offset").canConvertToInt()
          || o.path("offset").asInt() < 0) throw new IllegalArgumentException();
      return o.get("offset").asInt();
    } catch (Exception e) {
      throw error(400, "INVALID_CURSOR", "Cursor sai hoặc bộ lọc/snapshot đã thay đổi");
    }
  }

  public String next(int offset, Object binding) {
    byte[] data =
        write(json, map("offset", offset, "binding", hash(json, binding)))
            .getBytes(StandardCharsets.UTF_8);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        + "."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(data));
  }

  public <T> Map<String, Object> page(List<T> items, int limit, String cursor, Object binding) {
    if (limit < 1 || limit > 500) throw error(400, "INVALID_LIMIT", "limit phải từ 1 đến 500");
    int start = offset(cursor, binding);
    if (start > items.size()) throw error(400, "INVALID_CURSOR", "Cursor vượt danh sách");
    int end = Math.min(start + limit, items.size());
    return map(
        "items",
        items.subList(start, end),
        "next_cursor",
        end < items.size() ? next(end, binding) : null);
  }
}
