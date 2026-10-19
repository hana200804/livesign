# API LiveSign — Android ↔ Backend ↔ Web admin

Đặc tả **1.0**, chưa có controller hoặc dịch vụ chạy. Base path `/api/v1`;
JSON UTF-8, tên `snake_case`. Ví dụ dưới là **dữ liệu minh họa hợp đồng**, không phải
kết quả đã đo; chỉ [mẫu JSONL](observation-sample.jsonl) có nguồn PoC thật.
Danh mục dùng `qcvn12-2026-10-19` từ [classes.yaml](../../ai/configs/classes.yaml).

## Quy ước chung

- JWT trong `Authorization: Bearer <access_token>`; `user` đọc bản đồ/gửi quan sát;
  `importer` nhập dữ liệu AI; `admin` đọc hàng chờ và quyết định. User JWT là người
  gửi, **khác creator_id gốc**. Mật khẩu lưu bcrypt trong bảng user.
- Thời gian UTC, `captured_at` theo [observation.md](observation.md). ID là string;
  không ép ID Mapillary sang số JavaScript. Khoảng cách mét, tốc độ **m/s**, góc **độ**.
- Mọi truy vấn bản đồ phát lại bắt buộc `evaluation_year` thuộc 2023/2024/2025.
  Backend dựng snapshot chỉ bằng bằng chứng có trước cutoff tương ứng trong params;
  không lấy trạng thái mới nhất năm 2026 cho bản đồ năm 2023. Quyết định admin cũng có cutoff.
- Danh sách trả `{items, next_cursor}`; `limit` mặc định 100, tối đa 500 (đề xuất).
  Cursor opaque do server tạo, giữ nguyên khi gọi trang sau; ràng buộc cùng bộ lọc/snapshot.
- Đề xuất giới hạn 60 request/phút/tài khoản cho nhập lô; vượt trả 429 và `Retry-After`.
  Chốt mức vận hành sau khi đo; không coi đây là tải đã kiểm chứng.
- HTTP 400 sai JSON/query; 401 chưa đăng nhập; 403 thiếu vai trò; 404 ID không tồn tại;
  409 trùng ID khác nội dung/phiên bản admin cũ; 413 lô quá lớn; 422 sai nghĩa dữ liệu;
  429 giới hạn tốc độ. Lỗi envelope từ chối cả lô; lỗi từng dòng được trả theo mục nhập lô.

Ví dụ lỗi đơn:

```json
{"error":{"code":"INVALID_OBSERVATION","message":"not_seen cần đủ điều kiện nhìn được","fields":["facing_camera"],"request_id":"req-demo-1"}}
```

### Đăng nhập và danh mục

| Endpoint | Ai gọi | Mục đích |
| --- | --- | --- |
| `POST /auth/login` | Android/Web/importer | Nhận JWT; tài khoản do nhóm cấp, không tự tạo admin từ client |
| `GET /sign-types` | Cả ba phần | Đọc cùng danh mục mã, class_id, xe áp dụng và phiên bản |

```json
{"username":"demo_admin","password":"<nhập trên máy, không lưu vào repo>"}
```

```json
{"access_token":"<jwt>","token_type":"Bearer","expires_in_s":3600,"role":"admin"}
```

`GET /sign-types` (trích một phần danh sách; thực tế trả đủ 12):

```json
{"classes_version":"qcvn12-2026-10-19","items":[{"class_id":7,"sign_type_code":"P.131a","name_vi":"Cấm đỗ xe","vehicle_types":["car","truck","motorcycle"]}]}
```

## Nhận lần thấy theo lô

**`POST /observations/batch`** — importer hoặc user. Tối đa 500 dòng (đề xuất).
AI xuất JSONL rồi đóng gói vào `observations`; Android phát lại dùng cùng hợp đồng.
Trip trong envelope được tạo nếu chưa có; nếu đã có, source/sequence/creator phải khớp.
`trip_id`, `sequence_id`, `creator_id` của mọi dòng phải khớp trip đã khai.
Muốn nhập nhiều trip, gửi nhiều lô. `user_id` người gửi lấy từ JWT.

```json
{
  "schema_version":"1.0",
  "classes_version":"qcvn12-2026-10-19",
  "trip":{
    "trip_id":"mapillary:demo-sequence",
    "source":"mapillary",
    "sequence_id":"demo-sequence",
    "creator_id":"mapillary:demo-creator",
    "trace_ref":"trips/demo-sequence/trace.geojson"
  },
  "observations":[{
    "observation_id":"demo-observation-1",
    "source":"mapillary",
    "trip_id":"mapillary:demo-sequence",
    "sequence_id":"demo-sequence",
    "creator_id":"mapillary:demo-creator",
    "captured_at":"2025-06-02T02:00:00Z",
    "captured_at_precision":"instant",
    "image_id":"demo-image-1",
    "target_sign_id":"sign-demo-1",
    "source_sign_id":null,
    "source_sign_type":null,
    "sign_type_code":"P.131a",
    "camera_lon":105.84715,
    "camera_lat":21.02537,
    "camera_heading_deg":0.0,
    "sign_lon":105.84715,
    "sign_lat":21.02555,
    "sign_facing_deg":180.0,
    "distance_m":19.9,
    "off_deg":0.0,
    "is_pano":false,
    "focal":0.8,
    "image_width_px":2048,
    "image_height_px":1152,
    "bbox_px":[1000.0,420.0,1048.0,480.0],
    "detector_confidence":0.86,
    "outcome":"seen",
    "in_fov":true,
    "facing_camera":true,
    "sharp":true,
    "unoccluded":true,
    "visibility_method":"human",
    "model_version":"demo-yolov8n-qcvn12",
    "evidence_ref":"observations/demo-observation-1/crop.jpg",
    "source_metadata":null
  }]
}
```

HTTP 200 sau khi ghi bền; endpoint này đồng bộ, chưa hứa có job nền:

```json
{"accepted":1,"metadata_only":0,"duplicates":0,"rejected":0,"items":[{"observation_id":"demo-observation-1","ingest_status":"accepted","target_sign_id":"sign-demo-1","error":null}]}
```

- Gửi lại cùng `observation_id` và cùng nội dung → `duplicate`, không thêm bằng chứng.
  Cùng ID khác nội dung → dòng `rejected`, error code `OBSERVATION_ID_CONFLICT` (409).
  Server lưu hash payload chuẩn hóa; không dùng timestamp upload để làm ID mới.
- Dòng sai trả `rejected` và mã 422 trong `error`, các dòng hợp lệ vẫn được ghi;
  các số đếm luôn cộng bằng số dòng request. Nếu toàn lô lỗi từng dòng vẫn 200 với kết quả chi tiết.
- `unknown` thiếu GPS/loại/kiểm tra nhìn được có thể lưu `metadata_only` để truy vết.
  Dòng unknown đủ metadata có thể accepted, nhưng **luôn không tính điểm**.
- `seen` phải có bbox/confidence/model/metadata tối thiểu; `not_seen` phải có target,
  vị trí và toàn bộ điều kiện tại observation.md, đồng thời đã duyệt dùng bằng chứng âm.
  N/θ chưa chốt không cản lưu dương, nhưng không được tự chạy bước xác nhận tự động.
- Không có target ở seen: Backend chỉ tạo/gán biển khi có vị trí ước lượng và loại
  hợp lệ; hướng thiếu thì giữ candidate, không tự gộp theo hướng. Từ 1.2, `core` gom và
  định vị trong job snapshot; `target_sign_id` trong items là kết quả của job đó.
- Replay không tăng số cặp: cùng ảnh gốc giữ cùng ID logic; server kiểm tra trùng
  ảnh/target/model và tính cặp từ danh tính người chụp gốc.

## Biển trong bbox

**`GET /signs?bbox=105.846,21.024,105.852,21.028&evaluation_year=2025&limit=100`**

Android/Web đọc; bbox `[west,south,east,north]` trong WGS84. Không nhận bbox qua
kinh tuyến đổi ngày cho phạm vi Hà Nội. `status` tùy chọn, có thể lọc một trạng thái.
Mặc định trả cả candidate, unclear, suspected_change và biển đã gỡ/thay để admin xem;
client không suy “có trong bbox” là “được nhắc”.

```json
{
  "evaluation_year":2025,
  "classes_version":"qcvn12-2026-10-19",
  "items":[{
    "sign_id":"sign-demo-1",
    "sign_type_code":"P.131a",
    "sign_lon":105.84715,
    "sign_lat":21.02555,
    "sign_facing_deg":180.0,
    "status":"confirmed_manual",
    "version":1,
    "reliability":{
      "agreement_score":0.833333,
      "alpha":5.0,
      "beta":1.0,
      "positive_pair_count":4,
      "negative_pair_count":0,
      "last_seen_at":"2025-06-02T02:00:00Z",
      "positive_age_years":0,
      "last_evidence_at":"2025-06-02T02:00:00Z",
      "evidence_age_years":0,
      "conflict_reasons":[]
    }
  }],
  "next_cursor":null
}
```

`agreement_score` là quy ước, chưa hiệu chỉnh thành xác suất. Trong ví dụ 4 cặp dương
đều ở 2025, chưa có cặp âm: α=5, β=1. Backend dựng reliability theo snapshot;
không lấy `detector_confidence` làm điểm này.

## Biển phía trước theo vị trí + hướng + loại xe

**`GET /signs/ahead?lon=105.84715&lat=21.02510&heading_deg=0&speed_mps=11.11&vehicle_type=car&evaluation_year=2025&road_ref=demo-road`**

`lon/lat` WGS84; `heading_deg` [0,360); `speed_mps` ≥0; xe thuộc
`car | truck | motorcycle`. `road_ref` là đoạn đường đã map match, tùy chọn;
không có thì `road_match_status=unverified` và chưa được coi đạt kiểm tra đường song song.
Đề xuất Backend dùng vết map matching của Vietmap để chọn đúng đoạn/hướng; chỉ lọc
khoảng cách + góc không chứng minh đã loại được biển trên đường nhánh.
Khoảng cách nhắc dự kiến `speed_mps × warning_min_lead_s` (1.1: cộng thêm đệm, xem Thay đổi 1.1); tách với **30 m** của điều kiện nhìn được.
Chọn biển trong khoảng cách nhắc, `angle_diff(bearing(xe→biển),heading_deg)` không quá
`ahead_max_bearing_diff_deg`, và `angle_diff(sign_facing_deg,bearing(biển→xe))`
không quá `ahead_max_facing_diff_deg` (hai ngưỡng dự kiến 45° trong params).
Thiếu hướng mặt thì không trả biển đó để nhắc, trả thông tin qua bản đồ bbox để kiểm tra.
Khi tốc độ bằng 0, warning_distance_m=0, items rỗng; không chia cho 0 để tính lead_time_s.

```json
{
  "evaluation_year":2025,
  "road_match_status":"matched",
  "warning_distance_m":55.55,
  "items":[{
    "sign_id":"sign-demo-1",
    "sign_type_code":"P.131a",
    "sign_lon":105.84715,
    "sign_lat":21.02555,
    "sign_facing_deg":180.0,
    "status":"confirmed_manual",
    "distance_m":49.8,
    "lead_time_s":4.48,
    "warning_label":null
  }],
  "next_cursor":null
}
```

Ví dụ 4,48 giây cho thấy đã đi vào vùng nhắc nhưng trễ hơn mục tiêu ≥5 giây;
không gắn nhãn “nhắc kịp” cho nó. Đo lead time trên replay thật, có timestamp.
Phạm vi xe lấy từ sign_type; biển khác loại xe không vào items. Không tự suy tốc độ
tối đa từ danh mục hiện chưa có lớp tốc độ.

| status | Nhãn tiếng Việt | Endpoint ahead |
| --- | --- | --- |
| `candidate` | ỨNG VIÊN | Không trả để nhắc |
| `confirmed_auto` | XÁC NHẬN TỰ ĐỘNG | Nhắc bình thường |
| `confirmed_manual` | XÁC NHẬN THỦ CÔNG | Nhắc bình thường; thống kê tách auto |
| `unclear` | CHƯA RÕ | Nhắc, warning_label=`lâu chưa xác minh` |
| `suspected_change` | NGHI THAY ĐỔI | Nhắc, warning_label=`chưa chắc chắn` |
| `removed` | ĐÃ GỠ | Không nhắc |
| `replaced` | ĐÃ THAY | Không nhắc biển cũ; xét biển mới riêng |

Đây là chính sách sản phẩm v3.3. Khi đánh giá: B0 có ít nhất một seen, gộp 20 m;
B1 cần ≥N cặp dương không xét thời gian; B2 thêm tuổi cặp dương gần nhất ≤H (loại unclear);
B3 thêm không suspected_change theo điểm giảm dần và các dấu hiệu mâu thuẫn.
Không dùng danh sách ahead của sản phẩm làm trực tiếp mẫu số/tử số các bậc.

## Hàng chờ NGHI THAY ĐỔI và bằng chứng

**`GET /admin/reviews?status=pending&sign_status=suspected_change&evaluation_year=2025&limit=100`**

Chỉ admin. Một ca mở trên mỗi sign; nhiều lý do/bằng chứng gắn cùng ca, tránh tăng
khối lượng duyệt vì gửi lặp. Hàng chờ chỉ chứa bằng chứng trước cutoff đã yêu cầu.

```json
{
  "evaluation_year":2025,
  "items":[{
    "review_id":"review-demo-1",
    "sign_id":"sign-demo-2",
    "sign_type_code":"P.123a",
    "sign_status":"suspected_change",
    "status":"pending",
    "version":1,
    "conflict_reasons":["latest_k_pairs_negative"],
    "before_observation_id":"demo-before",
    "after_observation_id":"demo-after",
    "before_evidence_url":"/api/v1/admin/observations/demo-before/evidence",
    "after_evidence_url":"/api/v1/admin/observations/demo-after/evidence"
  }],
  "next_cursor":null
}
```

**`GET /admin/observations/{observation_id}/evidence`** — admin đọc file ảnh ở kho ngoài git;
trả `image/jpeg` hoặc 404 nếu chưa có. `evidence_ref` là đường dẫn server quản lý,
không cho client đọc tùy ý filesystem. URL không mang token Mapillary. Quyền dùng
và attribution ảnh/feature được nhóm kiểm tra theo [decisions.md](../decisions.md).

## Admin quyết định

**`POST /admin/reviews/{review_id}/decision`** — admin; có kiểm soát version.
Không cho client gửi trực tiếp `status=removed` vào sign.

```json
{
  "expected_version":1,
  "decision":"replace",
  "replacement_sign_id":"sign-demo-3",
  "before_observation_id":"demo-before",
  "after_observation_id":"demo-after",
  "effective_at":"2025-10-01T00:00:00Z",
  "reason":"Đã xem ảnh trước/sau: biển cũ được thay bằng tấm biển mới."
}
```

```json
{"review_id":"review-demo-1","status":"decided","decision":"replace","sign_id":"sign-demo-2","sign_status":"replaced","replacement_sign_id":"sign-demo-3","version":2,"history_id":"history-demo-1"}
```

Decision: `confirm` (xác nhận thủ công), `keep` (bác nghi thay đổi, xác nhận thủ công),
`remove` (đã gỡ), `replace` (đã thay, bắt buộc replacement_sign_id khác biển cũ),
`defer` (giữ trạng thái/ca pending, lưu lý do và tăng version).
`remove/replace` bắt buộc ảnh trước/sau; biển mới phải tồn tại và được xem bằng chứng;
nếu cần tạo ứng viên bằng tay dùng **`POST /admin/signs`** dưới đây.
Quyết định/history/sign cập nhật một transaction; lưu admin từ JWT, lý do,
`decided_at` server UTC và `effective_at` do admin xác nhận dựa trên ảnh.
Snapshot sau admin dùng effective_at và thời điểm quyết định có sẵn trong kịch bản;
đánh giá trước admin không áp dụng quyết định ghi sau cutoff. Nếu replay mô phỏng
quyết định lịch sử thì phải ghi riêng là mô phỏng. Không xóa quyết định cũ hoặc ghi đè lớp biển cũ.
Khi đánh giá tự động B0–B3, báo kết quả trước duyệt và phần sau duyệt riêng.

**`POST /admin/signs`** — admin bổ sung tấm biển đã xem; cần ảnh chứng minh:

```json
{"sign_type_code":"P.123b","sign_lon":105.84718,"sign_lat":21.02556,"sign_facing_deg":180.0,"evidence_observation_id":"demo-after","reason":"Tách tấm mới từ ảnh đã duyệt."}
```

```json
{"sign_id":"sign-demo-3","sign_type_code":"P.123b","status":"candidate","version":1}
```

Tạo ứng viên không tự xác nhận. Nếu nhiều loại xuất hiện cùng ảnh/cùng cột thì giữ
nhiều sign_id. Hệ thống chỉ đề nghị nghi thay đổi khi loại cũ không thấy dù nhìn được,
cùng vị trí/hướng mặt, đã loại trừ nhiều tấm cùng cột; quyền quyết gỡ/thay thuộc admin.

## Thay đổi 1.1

Phần **bổ sung**, giữ nguyên tên/ý nghĩa endpoint 1.0, role, outcome và trạng thái.
Các ví dụ mới đều minh họa. Trace theo [trip-trace.md](trip-trace.md); giao diện theo
[ui.md](../ui.md). Tất cả đường dẫn dưới vẫn có prefix `/api/v1`.

### Danh sách chuyến có thể phát lại

**`GET /trips?evaluation_year=2025&limit=100`** — user/importer/admin.
Chỉ trả chuyến tài khoản được đọc, có điểm trace hợp lệ trước cutoff; phân trang
items/next_cursor như 1.0. evaluation_year bắt buộc, lọc thời gian chụp không phải
thời gian nạp. Chuyến cắt qua biên năm chỉ trả representation trước cutoff.

```json
{
  "evaluation_year":2025,
  "items":[{
    "trip_id":"illustration:trip-01",
    "source":"mapillary",
    "sequence_id":"illustration:sequence-01",
    "creator_id":"illustration:creator-01",
    "started_at":"2025-06-02T02:00:00Z",
    "ended_at":"2025-06-02T02:00:48Z",
    "trace_schema_version":"1.1",
    "trace_available":true,
    "point_count":25,
    "data_kind":"illustration",
    "trace_url":"/api/v1/trips/illustration%3Atrip-01/trace?evaluation_year=2025"
  }],
  "next_cursor":null
}
```

point_count là số điểm representation đã lọc, không phải toàn sequence. ID mẫu không
tồn tại trên Mapillary; source chỉ mô tả cấu trúc fixture. Đổi year/filter khi dùng
cursor cũ → 400, không phục vụ nhầm snapshot.

### Trace và manifest của chuyến

**`GET /trips/{trip_id}/trace?evaluation_year=2025`** — cùng quyền đọc trip.
`Accept: application/x-ndjson` trả JSONL schema 1.1;
`Accept: application/json` trả manifest của **cùng representation đã lọc cutoff**.
Không đổi trace_ref cũ âm thầm từ GeoJSON sang JSONL: phải có file/hash mới và manifest.

```json
{
  "trip_id":"illustration:trip-01",
  "trace_schema_version":"1.1",
  "evaluation_year":2025,
  "cutoff_exclusive_utc":"2026-01-01T00:00:00Z",
  "sequence_id":"illustration:sequence-01",
  "creator_id":"illustration:creator-01",
  "data_kind":"illustration",
  "point_count":25,
  "segment_count":1,
  "time_precision":"instant",
  "speed_method":"synthetic_constant",
  "heading_method":"synthetic",
  "trace_sha256":"<hash của NDJSON trả về>",
  "source_trace_sha256":"<hash trace gốc trong kho>",
  "attribution":"Dữ liệu minh họa, không phải chuyến thật"
}
```

Một dòng NDJSON tương ứng, theo file mẫu:

```json
{"t":"2025-06-02T02:00:00Z","lon":105.84715,"lat":21.023,"heading_deg":0.0,"speed_mps":9.95,"image_id":"illustration:image-001","sequence_id":"illustration:sequence-01","creator_id":"illustration:creator-01","segment_id":0,"data_kind":"illustration"}
```

200 có Content-Type đúng, ETag theo hash representation; If-None-Match cho phép 304
sau kiểm quyền. Cache key gồm trip_id/year/hash. 404 nếu không có trip/quyền đọc;
422 `TRACE_NOT_REPLAYABLE` nếu chỉ có ngày hoặc không chuẩn hóa được timestamp.
Không bịa giờ/nhịp ảnh từ ngày. Đọc toàn file theo stream giữ thứ tự segment/t;
**Cần nhóm quyết:** giới hạn kích thước trace ở deployment sau khi đo sequence thật,
không dùng tùy tiện giới hạn số dòng của batch observation cho trace.

### Ảnh phát lại

**`GET /trips/{trip_id}/images/{image_id}?evaluation_year=2025`** — user/importer/admin.
Trả bytes image/jpeg hoặc Content-Type ảnh thực trong manifest, ETag hash ảnh và
attribution. Kiểm image_id thuộc trace được phép và chụp trước cutoff; không cho
client truy cập ảnh tương lai bằng cách đoán ID. Mọi ID path phải URL encode.
Không nhận path filesystem/URL tải tùy ý và không trả token Mapillary.

| Trường hợp | HTTP / client |
| --- | --- |
| Có ảnh | 200; giải mã, kiểm kích thước, đồng bộ theo image_id/t |
| Không có ảnh trong kho | 404 `IMAGE_NOT_AVAILABLE`; dừng/bỏ frame có ghi log |
| Hết JWT | 401; đăng nhập lại, không retry vô hạn |
| Mất mạng | Dùng cache có quyền/hash/attribution nếu có, hiển thị mất mạng |

**Cần nhóm quyết:** phục vụ ảnh từ kho đã chuẩn bị ngoài git thay vì proxy tức
thời, để replay ổn định và kiểm quyền/quota. Mẫu illustration không có ảnh thật:
client hiện placeholder “dữ liệu minh họa”, không cố tải từ Mapillary.

### Chi tiết biển và lịch sử

**`GET /signs/{sign_id}?evaluation_year=2025`** — user/importer/admin.
Response là một sign đủ các trường/reliability/version/classes_version của item bbox
1.0, thêm params_version, config_sha256, data_revision, road_ref,
evidence_available và history_url. 404 nếu sign chưa có trước cutoff; không lấy
cache sign năm hiện tại rồi đổi nhãn year. Các trường bổ sung ví dụ:

```json
{"params_version":"prep-2026-10-19","config_sha256":"<hash config đã đóng>","data_revision":"illustration-r001","road_ref":null,"evidence_available":false,"history_url":"/api/v1/signs/sign-demo-1/history?evaluation_year=2025"}
```

**`GET /signs/{sign_id}/history?evaluation_year=2025&limit=100`** — user/importer/admin.
Items tăng theo effective_at, recorded_at, history_id; cursor khóa cùng snapshot.
User không nhận thông tin tài khoản/ghi chú nội bộ; không có password_hash/JWT/raw secrets.

```json
{
  "evaluation_year":2025,
  "items":[{
    "history_id":"history-demo-1",
    "sign_id":"sign-demo-1",
    "event_type":"state_changed",
    "old_status":"candidate",
    "new_status":"confirmed_auto",
    "old_sign_type_code":"P.131a",
    "new_sign_type_code":"P.131a",
    "effective_at":"2025-06-02T02:00:00Z",
    "recorded_at":"2025-06-02T02:00:01Z",
    "reason":"Minh họa cấu hình thử đã chốt, không phải kết quả đo"
  }],
  "next_cursor":null
}
```

Lịch sử score/state suy từ ảnh dựng lại trên tập trước cutoff và version đóng;
không lấy after_snapshot năm sau làm vị trí năm cũ. Admin có hiệu lực khi effective_at
và decided_at cùng đủ điều kiện. recorded_at của lần nhập dữ liệu lịch sử không
biến captured_at thành mới; báo rõ đây là đánh giá retrospective.

### Chi tiết ca admin

**`GET /admin/reviews/{review_id}?evaluation_year=2025`** — admin.
Mở rộng item hàng chờ 1.0: sign_version, reliability_by_year, before/after
(observation_id, image_id, captured_at, captured_at_precision, outcome,
visibility_method, evidence_url), replacement_candidates là các sign khác đã có
bằng chứng. 404 nếu ca không thuộc snapshot; không ráp ảnh sau cutoff từ frontend.

POST decision 1.0 vẫn dùng expected_version của **review**, không phải sign.
Đề xuất kiểm thêm sign_version trong transaction: nếu thay đổi khi đang xem →409,
trả version mới, client tải detail và người duyệt xem lại. Không tự gửi lại remove/replace.
defer giữ pending và tăng version; confirm/keep không xóa quan sát âm hoặc nâng điểm giả.

**Cần nhóm quyết:** lưu watermark tập chứng cứ đã duyệt trong after_snapshot;
chỉ mở lại ca khi có bằng chứng hợp lệ mới. Lý do: keep không được lặp vô hạn khi
đọc lại cùng dữ liệu, cũng không được xóa âm để làm hết mâu thuẫn số học.

### Phiên bản snapshot và môi trường demo

Bổ sung params_version, config_sha256 và data_revision vào response bản đồ/chi tiết/
hàng chờ. Không có endpoint ghi “năm hệ thống”; evaluation_year theo từng request.
App loại response đến muộn của chuyến/năm cũ. N/θ null vẫn lưu candidate; không tự
bật confirmed_auto. Chấm bậc cần ngưỡng chưa đóng →422 `PARAMS_NOT_FINALIZED`.

Nhóm chốt 19/10 (decisions.md): demo admin năm quá khứ dùng CSDL riêng, manifest scenario_id
và nhãn “mô phỏng quyết định lịch sử” trên mọi màn. Không bỏ kiểm decided_at bằng
query trong đánh giá thật. Ảnh trước/sau có thể thật nhưng quyết định tái diễn vẫn
báo riêng là mô phỏng. Giữ kết quả trước/sau admin riêng theo hợp đồng 1.0.

### Đệm độ trễ khi nhắc

`/signs/ahead` tính `warning_distance_m = speed_mps × (warning_min_lead_s + warning_latency_buffer_s)`
(params, đệm dự kiến 1,5 s), thay cho công thức 1.0 không có đệm. Ví dụ 1.0 với
speed 11,11 m/s cho 55,55 m sẽ thành 72,22 m. Response không đổi trường; client không tự cộng đệm lần nữa.

### Danh sách bổ sung 1.1

- Khoảng cách nhắc của `/signs/ahead` cộng thêm `warning_latency_buffer_s` (nhóm chốt 19/10).

Manifest Accept JSON của trace được bổ sung `frames_metadata`, object theo image_id
chỉ chứa các frame trước cutoff: focal, is_pano, image_width_px, image_height_px,
geometry_source, heading_source. Các trường thiếu để null, kích thước phải đúng ảnh
bytes API sẽ trả. Như vậy app re-infer có metadata camera để tuân observation.md;
trace tối giản không bị tự thêm focal giả. Hash manifest thay đổi khi metadata đổi.
Với bộ lớn có thể tách representation metadata, nhưng chưa có endpoint mới cho
cách tách đó: bản đầu phục vụ trong JSON manifest, không trả ref nội bộ app không đọc được.

**`POST /admin/reviews`** — bổ sung cho màn chi tiết candidate/unclear cần admin
xác nhận thủ công; không tự đưa mọi candidate vào hàng chờ suspected_change.
Request minh họa: `{"sign_id":"sign-demo-1","reason":"Mở kiểm tra thủ công"}`.
Response 201: `{"review_id":"review-demo-manual","sign_id":"sign-demo-1","status":"pending","version":1}`.
Nếu đã có review pending cùng sign, trả 200 ca đó thay vì tạo lặp. Không đổi sign.status
khi chỉ mở ca; quyết định vẫn qua endpoint 1.0. Role admin, sign/evidence tồn tại;
403 thiếu quyền, 404 không có sign. Nhóm chốt 19/10: lối mở ca thủ công này **không
thuộc MVP**, làm ở mục Nên có của [requirements.md](../requirements.md#phạm-vi-mvp).

- Thêm GET trips, trace/manifest, ảnh replay, sign detail/history và review detail.
- Thêm POST admin/reviews để mở ca xác nhận thủ công; không đổi hàng chờ nghi.
- Thêm hash/cutoff/cache cho representation; làm rõ lỗi trace không đủ thời gian.
- Bổ sung provenance snapshot, version 409, watermark admin và môi trường demo dự kiến.
- Không sửa nghĩa, tên cột, trạng thái, quyền hoặc endpoint 1.0.

## Thay đổi 1.2

Bổ sung theo [kiến trúc 1.2](../architecture.md): dựng sẵn snapshot và phát lại không gọi
mạng theo từng điểm. Không đổi tên, quyền hay nghĩa các endpoint 1.0/1.1.

### Đọc từ snapshot dựng sẵn

`GET /signs`, `/signs/ahead`, `/signs/{sign_id}` và `/trips/{trip_id}/signs` đọc lần chạy
`snapshot_run` mới nhất có `status=ready` của năm được hỏi ([db-schema.md](db-schema.md),
Thay đổi 1.2); không tính lại điểm khi đọc. Response có thêm `snapshot_run_id`.
Chưa có lần chạy sẵn sàng cho năm đó → 503 `SNAPSHOT_NOT_READY`, client thử lại sau.

**Giữ một run cho cả phiên.** Các endpoint trên và `/signs/{sign_id}/history` nhận thêm
`snapshot_run_id` tùy chọn. Có tham số → đọc đúng run đó, kể cả khi đã `superseded`; run
khác năm được hỏi → 422; run đã bị dọn → 410 `SNAPSHOT_RUN_GONE`, client mở phiên mới.
Không có → run `ready` mới nhất, response trả `snapshot_run_id` để client dùng cho các lần
gọi sau (trang tiếp, chi tiết, history). History chỉ gồm dòng có `input_seq` không quá run
([db-schema.md](db-schema.md), Thay đổi 1.2), để quyết định ghi sau khi run công bố không
hiện trong lịch sử mà trạng thái của run chưa có.

### Lưu thành công nhưng dựng lỗi

`POST /observations/batch` và `POST /admin/reviews/{review_id}/decision` dựng lại run các
năm bị ảnh hưởng trước khi trả lời. Response 200 có thêm:

```json
{"snapshot_runs":[{"evaluation_year":2025,"status":"ready","run_id":"run-2025-illustration-02"}]}
```

Dữ liệu hoặc quyết định đã commit nhưng dựng lỗi → **503 `SNAPSHOT_BUILD_FAILED`**, body
vẫn đủ counts/items (lô) hoặc review/version mới (quyết định) như khi 200, cùng
`snapshot_runs` có `status:"failed"` ở năm lỗi; run cũ vẫn phục vụ. Client:

- Lô: gửi lại **đúng lô** đó. Các dòng thành `duplicate`, không nhân bằng chứng, nhưng server
  vẫn dựng lại năm có run chậm hơn `data_state.input_seq`.
- Quyết định: **không** gửi lại decision. Tải lại review/version, rồi gọi endpoint dựng lại dưới đây.

**`POST /admin/snapshots/rebuild`** — admin; body tùy chọn `{"evaluation_year":2025}`.
Dựng lại các năm có run chậm hơn dữ liệu đã lưu; năm đã khớp thì trả run hiện có, không
dựng lặp. Response như `snapshot_runs` ở trên. Server cũng tự làm việc này khi khởi động lại.
Bảng này là bản đồ sản phẩm (đã áp dụng quyết định admin); bản trước duyệt cho thực
nghiệm do CLI đánh giá xuất ra CSV, không qua API.

### Biển dọc chuyến để phát lại

**`GET /trips/{trip_id}/signs?evaluation_year=2025&vehicle_type=car`** — user/importer/admin.
Trả mọi biển của snapshot nằm trong `replay.corridor_buffer_m` (dự kiến 50 m) quanh trace
của chuyến, cùng tham số nhắc để app lọc biển phía trước **cục bộ**. Gọi một lần khi bắt đầu.

```json
{
  "evaluation_year":2025,
  "snapshot_run_id":"run-2025-illustration-01",
  "trip_id":"illustration:trip-01",
  "vehicle_type":"car",
  "alert_params":{
    "params_version":"prep-2026-10-19",
    "warning_min_lead_s":5,
    "warning_latency_buffer_s":1.5,
    "ahead_max_bearing_diff_deg":45,
    "ahead_max_facing_diff_deg":45,
    "alert_cooldown_s":15,
    "alert_rearm_distance_m":80
  },
  "items":[{
    "sign_id":"sign-demo-1",
    "sign_type_code":"P.131a",
    "sign_lon":105.84715,
    "sign_lat":21.02555,
    "sign_facing_deg":180.0,
    "facing_source":"aggregated",
    "location_method":"ray_least_squares",
    "status":"confirmed_auto",
    "applies_to_vehicle":true,
    "alertable":true,
    "warning_label":null
  }]
}
```

`alert_params` có thêm `corridor_buffer_m`. `alertable=false` khi biển thiếu hướng mặt, chỉ có `single_view`, là candidate/removed/
replaced hoặc không áp dụng cho loại xe — app vẫn vẽ trên bản đồ nhưng không nhắc.
`warning_label` theo bảng trạng thái 1.0. Không phân trang: một chuyến demo chỉ có vài chục
đến vài trăm biển; vượt `limit` 500 thì trả 413 để chia chuyến.

App lọc cục bộ đúng luật của `/signs/ahead` (khoảng cách
`speed × (warning_min_lead_s + warning_latency_buffer_s)`, hai ngưỡng góc, loại xe) với
**tham số lấy từ `alert_params`**, không chép số vào code app; chống nhắc lặp là trạng thái
phiên của app, chạy sau bước lọc. Biển ngoài `corridor_buffer_m` quanh trace là biển chuyến
không đi qua (sau điểm cuối, đường nhánh), không nhắc kể cả khi nằm trong D.

`/signs/ahead` giữ làm bản tham chiếu. Test đối chiếu: cùng `snapshot_run_id`, điểm, xe,
tham số; so danh sách **ứng viên trước chống lặp** của app với items của `/signs/ahead`
**giao** tập biển dọc chuyến. Chênh lệch chỉ được phép ở biển ngoài vùng đó.

### Danh sách bổ sung 1.2

- Các endpoint bản đồ đọc `sign_snapshot`; thêm `snapshot_run_id` và lỗi 503 `SNAPSHOT_NOT_READY`.
- Thêm `GET /trips/{trip_id}/signs` trả biển dọc chuyến + `alert_params` (có `corridor_buffer_m`) cho lọc cục bộ.
- Tham số `snapshot_run_id` tùy chọn cho mọi endpoint bản đồ/chi tiết/history; 410 `SNAPSHOT_RUN_GONE`.
- 503 `SNAPSHOT_BUILD_FAILED` khi đã lưu nhưng dựng lỗi; thêm `snapshot_runs` trong response ghi và `POST /admin/snapshots/rebuild`.
- Test đối chiếu so ứng viên trước chống lặp, trong vùng biển dọc chuyến.
- Item biển có thêm `location_method`, `facing_source`, `alertable`.
- `GET /signs` cho thêm role importer đọc, để batch AI lấy vị trí/hướng mặt khi tạo not_seen ở lượt 2.
