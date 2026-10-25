package vn.livesign.app.workflow;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.livesign.core.spatial.Geo;

/** Tên bảng/cột chỉ do code gọi cung cấp; mọi giá trị đi qua prepared statement. */
@Component
public class DbWriter {
  private final JdbcTemplate jdbc;

  public DbWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(String table, Map<String, Object> values) {
    if (!table.matches("[a-z_][a-z_0-9]*")
        || values.keySet().stream().anyMatch(k -> !k.matches("[a-z_][a-z_0-9]*")))
      throw new IllegalArgumentException("SQL identifier");
    var placeholders = new ArrayList<String>();
    var args = new ArrayList<Object>();
    values.forEach(
        (key, value) -> {
          if (value instanceof Geo.Point p) {
            placeholders.add("ST_GeomFromText(?,4326,'axis-order=long-lat')");
            args.add("POINT(" + (p.lon() == -180 ? 180 : p.lon()) + " " + p.lat() + ")");
          } else {
            placeholders.add("?");
            args.add(value);
          }
        });
    jdbc.update(
        "INSERT INTO "
            + table
            + " ("
            + String.join(",", values.keySet())
            + ") VALUES ("
            + String.join(",", placeholders)
            + ")",
        args.toArray());
  }
}
