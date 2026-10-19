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
{"username":"nam","password":"<nhập trên máy, không lưu vào repo>"}
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
  hợp lệ; hướng thiếu thì giữ candidate, không tự gộp theo hướng.
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
Khoảng cách nhắc dự kiến `speed_mps × warning_min_lead_s`; tách với **30 m** của điều kiện nhìn được.
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
