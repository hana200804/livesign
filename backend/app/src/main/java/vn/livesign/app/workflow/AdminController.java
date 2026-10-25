package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import vn.livesign.core.reliability.ReliabilityCalculator;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final DbWriter db;
  private final ObjectMapper json;
  private final SnapshotService snapshots;
  private final SignQueries signs;
  private final Cursors cursors;
  private final SafeStorage storage;
  private final Clock decisionClock;

  public AdminController(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      DbWriter db,
      ObjectMapper json,
      SnapshotService snapshots,
      SignQueries signs,
      Cursors cursors,
      SafeStorage storage,
      Clock decisionClock) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.db = db;
    this.json = json;
    this.snapshots = snapshots;
    this.signs = signs;
    this.cursors = cursors;
    this.storage = storage;
    this.decisionClock = decisionClock;
  }

  @PostMapping("/snapshots/rebuild")
  public ResponseEntity<?> rebuild(@RequestBody(required = false) JsonNode body) {
    Integer year =
        body == null || !body.has("evaluation_year")
            ? null
            : body.path("evaluation_year").isIntegralNumber()
                ? body.path("evaluation_year").asInt()
                : -1;
    var runs = snapshots.rebuild(year);
    boolean failed = runs.stream().anyMatch(r -> "failed".equals(r.get("status")));
    return ResponseEntity.status(failed ? 503 : 200)
        .body(
            map(
                "snapshot_runs",
                runs,
                "error",
                failed
                    ? map(
                        "code",
                        "SNAPSHOT_BUILD_FAILED",
                        "message",
                        "Dựng snapshot lỗi; run cũ vẫn được phục vụ")
                    : null));
  }

  private List<Map<String, Object>> reviews(
      Map<String, Object> run, String status, String signStatus) {
    var rows =
        jdbc.queryForList(
            "SELECT r.*,s.sign_type_code,s.status sign_status,s.conflict_reasons snapshot_conflicts"
                + " FROM review r JOIN sign_snapshot s ON s.sign_id=r.sign_id AND s.run_id=? WHERE"
                + " r.input_seq<=? ORDER BY r.created_at,r.review_id",
            run.get("run_id"),
            run.get("input_seq"));
    Instant cutoff =
        ReliabilityCalculator.cutoffOf(((Number) run.get("evaluation_year")).intValue());
    var result = new ArrayList<Map<String, Object>>();
    for (var r : rows) {
      String visibleStatus = (String) r.get("status");
      // Quyết định sau cutoff không được trình bày như đã xảy ra ở năm được chọn.
      if (r.get("decided_at") != null
          && (!instant(r.get("decided_at")).isBefore(cutoff)
              || r.get("effective_at") != null && !instant(r.get("effective_at")).isBefore(cutoff)))
        visibleStatus = "pending";
      if (status != null && !status.equals(visibleStatus)
          || signStatus != null && !signStatus.equals(r.get("sign_status"))) continue;
      String before = (String) r.get("before_observation_id"),
          after = (String) r.get("after_observation_id");
      if (!inRun(run, before) || !inRun(run, after)) continue;
      result.add(
          map(
              "review_id",
              r.get("review_id"),
              "sign_id",
              r.get("sign_id"),
              "sign_type_code",
              r.get("sign_type_code"),
              "sign_status",
              r.get("sign_status"),
              "status",
              visibleStatus,
              "version",
              r.get("version"),
              "actionable",
              "pending".equals(r.get("status")),
              "conflict_reasons",
              parse(json, (String) r.get("snapshot_conflicts")),
              "before_observation_id",
              before,
              "after_observation_id",
              after,
              "before_evidence_url",
              evidenceUrl(before),
              "after_evidence_url",
              evidenceUrl(after)));
    }
    return result;
  }

  private boolean inRun(Map<String, Object> run, String id) {
    return id == null
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM sign_snapshot_observation WHERE run_id=? AND"
                    + " observation_id=?",
                Integer.class,
                run.get("run_id"),
                id)
            > 0;
  }

  private String evidenceUrl(String id) {
    return id == null
        ? null
        : "/api/v1/admin/observations/"
            + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8)
            + "/evidence";
  }

  @GetMapping("/reviews")
  public Map<String, Object> queue(
      @RequestParam(name = "evaluation_year") int year,
      @RequestParam(defaultValue = "pending") String status,
      @RequestParam(name = "sign_status", required = false) String signStatus,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(required = false) String cursor) {
    if (!Set.of("pending", "decided").contains(status))
      throw error(400, "INVALID_STATUS", "Trạng thái review sai");
    var run = snapshots.resolve(year, null);
    var response = signs.meta(run);
    response.putAll(
        cursors.page(
            reviews(run, status, signStatus),
            limit,
            cursor,
            map(
                "kind",
                "reviews",
                "run",
                run.get("run_id"),
                "status",
                status,
                "sign_status",
                signStatus,
                "limit",
                limit)));
    return response;
  }

  @GetMapping("/reviews/{reviewId}")
  public Map<String, Object> detail(
      @PathVariable String reviewId, @RequestParam(name = "evaluation_year") int year) {
    var run = snapshots.resolve(year, null);
    var review =
        reviews(run, null, null).stream()
            .filter(r -> reviewId.equals(r.get("review_id")))
            .findFirst()
            .orElseThrow(() -> error(404, "REVIEW_NOT_FOUND", "Ca không thuộc snapshot"));
    review.putAll(signs.meta(run));
    String sign = (String) review.get("sign_id");
    review.put(
        "sign_version",
        jdbc.queryForObject("SELECT version FROM sign WHERE sign_id=?", Integer.class, sign));
    review.put("before", evidence((String) review.get("before_observation_id")));
    review.put("after", evidence((String) review.get("after_observation_id")));
    var reliability = new ArrayList<Object>();
    for (int y : snapshots.years())
      if (y <= year)
        try {
          var earlier = snapshots.resolve(y, null);
          reliability.add(
              map(
                  "evaluation_year",
                  y,
                  "reliability",
                  signs
                      .item(signs.row((String) earlier.get("run_id"), sign), y, null)
                      .get("reliability")));
        } catch (vn.livesign.app.web.ApiException ignored) {
        }
    review.put("reliability_by_year", reliability);
    review.put(
        "replacement_candidates",
        signs.rows((String) run.get("run_id")).stream()
            .filter(
                s ->
                    !sign.equals(s.get("sign_id"))
                        && !Set.of("removed", "replaced").contains(s.get("status")))
            .map(s -> signs.item(s, year, null))
            .toList());
    return review;
  }

  private Map<String, Object> evidence(String id) {
    if (id == null) return null;
    var rows =
        jdbc.queryForList(
            "SELECT"
                + " observation_id,image_id,captured_at,captured_at_precision,outcome,visibility_method,evidence_ref"
                + " FROM observation WHERE observation_id=?",
            id);
    if (rows.isEmpty()) return null;
    var r = rows.getFirst();
    r.put("captured_at", instant(r.get("captured_at")));
    r.put("evidence_available", storage.exists((String) r.remove("evidence_ref")));
    r.put("evidence_url", evidenceUrl(id));
    return r;
  }

  @GetMapping("/observations/{observationId}/evidence")
  public ResponseEntity<byte[]> evidenceImage(@PathVariable String observationId) {
    var rows =
        jdbc.queryForList(
            "SELECT evidence_ref FROM observation WHERE observation_id=?", observationId);
    if (rows.isEmpty()) throw error(404, "EVIDENCE_NOT_AVAILABLE", "Không có bằng chứng");
    String ref = (String) rows.getFirst().get("evidence_ref");
    byte[] bytes = storage.bytes(ref);
    String content =
        ref.toLowerCase(Locale.ROOT).endsWith(".png")
            ? "image/png"
            : ref.toLowerCase(Locale.ROOT).endsWith(".webp") ? "image/webp" : "image/jpeg";
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(content))
        .body(bytes);
  }

  @PostMapping("/reviews")
  public ResponseEntity<?> open(
      @RequestBody JsonNode body, @AuthenticationPrincipal Jwt principal) {
    String sign = string(body, "sign_id"), reason = string(body, "reason");
    if (sign == null || reason == null || reason.isBlank())
      throw error(422, "INVALID_REVIEW", "Cần sign_id và lý do");
    var r =
        tx.execute(
            status -> {
              long seq =
                  jdbc.queryForObject(
                          "SELECT input_seq FROM data_state WHERE id=1 FOR UPDATE", Long.class)
                      + 1;
              var s = jdbc.queryForList("SELECT sign_id FROM sign WHERE sign_id=?", sign);
              if (s.isEmpty()) throw error(404, "SIGN_NOT_FOUND", "Không có biển");
              var existing =
                  jdbc.queryForList(
                      "SELECT review_id,sign_id,status,version FROM review WHERE sign_id=? AND"
                          + " status='pending'",
                      sign);
              if (!existing.isEmpty()) return map("existing", true, "review", existing.getFirst());
              var observations =
                  jdbc.queryForList(
                      "SELECT observation_id FROM observation WHERE target_sign_id=? AND"
                          + " outcome='seen' ORDER BY captured_at DESC LIMIT 1",
                      sign);
              if (observations.isEmpty())
                throw error(422, "EVIDENCE_REQUIRED", "Biển chưa có bằng chứng");
              String id = "review:" + UUID.randomUUID();
              db.insert(
                  "review",
                  map(
                      "review_id",
                      id,
                      "sign_id",
                      sign,
                      "conflict_reasons",
                      "[]",
                      "before_observation_id",
                      observations.getFirst().get("observation_id"),
                      "reporter_id",
                      principal.getSubject(),
                      "reason",
                      reason,
                      "input_seq",
                      seq));
              jdbc.update("UPDATE data_state SET input_seq=? WHERE id=1", seq);
              return map(
                  "existing",
                  false,
                  "review",
                  map("review_id", id, "sign_id", sign, "status", "pending", "version", 1));
            });
    snapshots.rebuild(null);
    return ResponseEntity.status(Boolean.TRUE.equals(r.get("existing")) ? 200 : 201)
        .body(r.get("review"));
  }

  @PostMapping("/reviews/{reviewId}/decision")
  public ResponseEntity<?> decide(
      @PathVariable String reviewId,
      @RequestBody JsonNode body,
      @AuthenticationPrincipal Jwt principal) {
    String decision = string(body, "decision"), reason = string(body, "reason");
    if (decision == null
        || !Set.of("confirm", "keep", "remove", "replace", "defer").contains(decision)
        || reason == null
        || reason.isBlank()
        || reason.length() > 4000
        || !body.path("expected_version").isIntegralNumber())
      throw error(422, "INVALID_DECISION", "Quyết định, lý do hoặc version không hợp lệ");
    Instant effective;
    try {
      effective = Instant.parse(Objects.requireNonNull(string(body, "effective_at")));
    } catch (Exception e) {
      throw error(422, "INVALID_DECISION", "effective_at phải là UTC instant");
    }
    Instant now = decisionClock.instant().truncatedTo(ChronoUnit.MILLIS);
    if (effective.isAfter(now))
      throw error(422, "INVALID_DECISION", "Không quyết định có hiệu lực ở tương lai");
    Map<String, Object> result =
        tx.execute(
            status -> {
              long seq =
                  jdbc.queryForObject(
                          "SELECT input_seq FROM data_state WHERE id=1 FOR UPDATE", Long.class)
                      + 1;
              var rows =
                  jdbc.queryForList("SELECT * FROM review WHERE review_id=? FOR UPDATE", reviewId);
              if (rows.isEmpty()) throw error(404, "REVIEW_NOT_FOUND", "Không có ca");
              var review = rows.getFirst();
              String sign = (String) review.get("sign_id");
              var s = jdbc.queryForMap("SELECT * FROM sign WHERE sign_id=? FOR UPDATE", sign);
              if (((Number) review.get("version")).intValue()
                      != body.path("expected_version").asInt()
                  || !"pending".equals(review.get("status"))
                  || body.has("expected_sign_version")
                      && body.path("expected_sign_version").asInt()
                          != ((Number) s.get("version")).intValue())
                throw error(
                    409,
                    "REVIEW_VERSION_CONFLICT",
                    "Ca hoặc biển đã thay đổi; tải lại để xem bằng chứng");
              if (Set.of("removed", "replaced").contains(s.get("status")))
                throw error(409, "SIGN_TERMINAL", "Biển đã được gỡ hoặc thay");
              String before = string(body, "before_observation_id"),
                  after = string(body, "after_observation_id"),
                  replacement = string(body, "replacement_sign_id");
              if (Set.of("remove", "replace").contains(decision)) {
                var old = evidenceRow(before);
                var newer = evidenceRow(after);
                if (!sign.equals(old.get("target_sign_id"))
                    || !"seen".equals(old.get("outcome"))
                    || !instant(newer.get("captured_at")).isAfter(instant(old.get("captured_at")))
                    || !storage.exists((String) old.get("evidence_ref"))
                    || !storage.exists((String) newer.get("evidence_ref")))
                  throw error(
                      422,
                      "INVALID_EVIDENCE",
                      "Cần ảnh trước/sau đúng tấm, đúng thứ tự và có file");
                if (effective.isBefore(instant(old.get("captured_at")))
                    || effective.isAfter(instant(newer.get("captured_at"))))
                  throw error(422, "INVALID_EFFECTIVE_AT", "effective_at phải nằm giữa hai ảnh");
                if ("remove".equals(decision)
                    && (!sign.equals(newer.get("target_sign_id"))
                        || !"not_seen".equals(newer.get("outcome"))))
                  throw error(
                      422, "INVALID_EVIDENCE", "Ảnh sau phải có not_seen hợp lệ của biển cũ");
                if ("replace".equals(decision)) {
                  if (replacement == null
                      || sign.equals(replacement)
                      || jdbc.queryForObject(
                              "SELECT COUNT(*) FROM sign WHERE sign_id=? AND status NOT IN"
                                  + " ('removed','replaced')",
                              Integer.class,
                              replacement)
                          == 0
                      || !replacement.equals(newer.get("target_sign_id"))
                      || !"seen".equals(newer.get("outcome")))
                    throw error(
                        422,
                        "INVALID_REPLACEMENT",
                        "Biển thay cần tồn tại riêng và có seen trong ảnh sau");
                }
              } else if ("confirm".equals(decision) || "keep".equals(decision)) {
                if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM observation WHERE target_sign_id=? AND outcome='seen'"
                            + " AND ingest_status='accepted'",
                        Integer.class,
                        sign)
                    == 0) throw error(422, "EVIDENCE_REQUIRED", "Thiếu seen của biển");
              }
              String oldStatus = (String) s.get("status"),
                  newStatus =
                      switch (decision) {
                        case "confirm", "keep" -> "confirmed_manual";
                        case "remove" -> "removed";
                        case "replace" -> "replaced";
                        default -> oldStatus;
                      };
              String reviewStatus = "defer".equals(decision) ? "pending" : "decided";
              int version = ((Number) review.get("version")).intValue() + 1;
              String history = "history:" + UUID.randomUUID();
              var afterSnapshot =
                  map(
                      "decision",
                      decision,
                      "status",
                      newStatus,
                      "replacement_sign_id",
                      "replace".equals(decision) ? replacement : null,
                      "evidence_watermark",
                      effective,
                      "review_id",
                      reviewId);
              db.insert(
                  "sign_history",
                  map(
                      "history_id",
                      history,
                      "sign_id",
                      sign,
                      "event_type",
                      "admin_decision",
                      "old_status",
                      oldStatus,
                      "new_status",
                      newStatus,
                      "old_sign_type_code",
                      s.get("sign_type_code"),
                      "new_sign_type_code",
                      s.get("sign_type_code"),
                      "before_snapshot",
                      write(json, map("status", oldStatus)),
                      "after_snapshot",
                      write(json, afterSnapshot),
                      "before_observation_id",
                      before,
                      "after_observation_id",
                      after,
                      "changed_by",
                      principal.getSubject(),
                      "effective_at",
                      timestamp(effective),
                      "recorded_at",
                      timestamp(now),
                      "reason",
                      reason,
                      "input_seq",
                      seq));
              jdbc.update(
                  "UPDATE sign SET status=?,replacement_sign_id=?,version=version+1,as_of_year=?"
                      + " WHERE sign_id=?",
                  newStatus,
                  "replace".equals(decision) ? replacement : null,
                  now.atZone(ZoneOffset.UTC).getYear(),
                  sign);
              jdbc.update(
                  "UPDATE review SET"
                      + " status=?,decision=?,replacement_sign_id=?,before_observation_id=COALESCE(?,before_observation_id),after_observation_id=COALESCE(?,after_observation_id),decided_by=?,reason=?,effective_at=?,decided_at=?,version=?,input_seq=?"
                      + " WHERE review_id=?",
                  reviewStatus,
                  decision,
                  "replace".equals(decision) ? replacement : null,
                  before,
                  after,
                  principal.getSubject(),
                  reason,
                  timestamp(effective),
                  timestamp(now),
                  version,
                  seq,
                  reviewId);
              jdbc.update("UPDATE data_state SET input_seq=? WHERE id=1", seq);
              return map(
                  "review_id",
                  reviewId,
                  "status",
                  reviewStatus,
                  "decision",
                  decision,
                  "sign_id",
                  sign,
                  "sign_status",
                  newStatus,
                  "replacement_sign_id",
                  "replace".equals(decision) ? replacement : null,
                  "version",
                  version,
                  "history_id",
                  history);
            });
    var runs = snapshots.rebuild(null);
    result.put("snapshot_runs", runs);
    boolean failed = runs.stream().anyMatch(r -> "failed".equals(r.get("status")));
    if (failed)
      result.put(
          "error",
          map(
              "code",
              "SNAPSHOT_BUILD_FAILED",
              "message",
              "Quyết định đã lưu. Tải lại ca và gọi snapshots/rebuild; không gửi lại quyết định."));
    return ResponseEntity.status(failed ? 503 : 200).body(result);
  }

  private Map<String, Object> evidenceRow(String id) {
    if (id == null) throw error(422, "EVIDENCE_REQUIRED", "Cần hai observation bằng chứng");
    var rows =
        jdbc.queryForList(
            "SELECT * FROM observation WHERE observation_id=? AND ingest_status='accepted'", id);
    if (rows.isEmpty()) throw error(422, "INVALID_EVIDENCE", "Observation bằng chứng không hợp lệ");
    return rows.getFirst();
  }
}
