# Hợp đồng lần thấy — AI / Android → Backend

Phiên bản đặc tả **1.0**, đề xuất để triển khai; cả nhóm cùng đọc.
Nguồn lớp: [classes.yaml](../../ai/configs/classes.yaml). Tham số: [params.yaml](../../config/params.yaml).
File trao đổi là **JSON Lines UTF-8**, mỗi dòng một lần kiểm tra một tấm biển trong
một ảnh, không phải một dòng cho cả ảnh. JSON dùng `null`, không dùng NaN, chuỗi rỗng
hoặc số 0 để biểu diễn thiếu. ID luôn là **string**, kể cả ID Mapillary toàn chữ số.

## Tên cột và kiểu

Các cột tùy chọn có thể bỏ hoặc đặt null. Khi chuyển thành request API, gửi nguyên object
của một dòng trong `observations[]`; không đổi tên. MySQL giữ cùng tên, riêng bbox
lưu dưới dạng JSON và thời gian ngày được chuẩn hóa như dưới đây.

| Cột | Kiểu JSON / đơn vị | Bắt buộc và ý nghĩa |
| --- | --- | --- |
| `observation_id` | string, ≤160 ký tự | Có; khóa ổn định khi gửi lại, không đổi theo lần upload |
| `source` | enum | Có; `mapillary` hoặc `android_replay`, đều có gốc từ ảnh công khai |
| `trip_id` | string, ≤160 | Có; một sequence hoặc một phiên phát lại |
| `sequence_id` | string, ≤128 | Có với Mapillary/phát lại; giữ ID gốc, không coi sequence là một người |
| `creator_id` | string/null, ≤128 | Người chụp gốc dạng `mapillary:<id>`; không phải người chạy Colab/người upload |
| `captured_at` | string | Có; RFC3339 UTC, hoặc `YYYY-MM-DD` khi chỉ có ngày |
| `captured_at_precision` | enum | Có; `instant` hoặc `day`; cấm giả độ chính xác cao hơn nguồn |
| `image_id` | string, ≤128 | Có; ID ảnh gốc; cùng ảnh có thể có nhiều dòng cho nhiều biển |
| `target_sign_id` | string/null, ≤160 | ID tấm biển trong LiveSign đang được kiểm tra; khác ID feature Mapillary |
| `source_sign_id` | string/null, ≤128 | ID feature biển gợi ý của Mapillary, chưa phải ground truth |
| `source_sign_type` | string/null, ≤160 | Nhãn gốc Mapillary, giữ để truy vết; không tự suy mã QCVN |
| `sign_type_code` | string/null | Mã trong danh mục; với not_seen là loại biển cũ đang kiểm tra |
| `camera_lon`, `camera_lat` | number/null, độ | WGS84; longitude [-180,180], latitude [-90,90]; cả hai cùng có hoặc cùng thiếu |
| `camera_heading_deg` | number/null, độ | Hướng trục camera; [0,360), Bắc=0, Đông=90, tăng theo chiều kim đồng hồ |
| `sign_lon`, `sign_lat` | number/null, độ | Vị trí biển ước lượng/đã biết, cùng hệ WGS84; không gọi GPS ảnh là vị trí thật của biển |
| `sign_facing_deg` | number/null, độ | Pháp tuyến hướng từ mặt trước biển ra phía người đọc, [0,360); không phải hướng xe |
| `distance_m` | number/null, mét | Khoảng cách camera–biển ≥0, cùng đơn vị mét với ngưỡng 30 m |
| `off_deg` | number/null, độ | Góc có dấu từ hướng camera tới biển, [-180,180), dương bên phải |
| `is_pano` | boolean/null | Ảnh panorama equirectangular hay phối cảnh; thiếu camera thì null |
| `focal` | number/null, không thứ nguyên | Tiêu cự chuẩn hóa theo **cạnh dài** ảnh, >0; không phải mm hay px |
| `image_width_px`, `image_height_px` | integer/null, pixel | Kích thước đúng ảnh được model xử lý trước resize/letterbox nội bộ; cùng có/cùng thiếu |
| `bbox_px` | array[4] number/null, pixel | `[x1,y1,x2,y2]`, gốc góc trên trái; x sang phải, y xuống; trên đúng ảnh ở hai cột kích thước |
| `detector_confidence` | number/null, [0,1] | Confidence của model nhận diện; không phải α/(α+β) của biển |
| `outcome` | enum | Có; **`seen` / `not_seen` / `unknown`** |
| `in_fov`, `facing_camera`, `sharp`, `unoccluded` | boolean/null | Bốn điều kiện kiểm tra nhìn được; null = chưa kiểm tra được, false = không đạt |
| `visibility_method` | enum | Có; `unverified`, `independent_model_audited`, `human`, `automatic_rules` |
| `model_version` | string/null, ≤128 | Tên bản nhận diện/manifest; bắt buộc nếu có confidence/bbox hoặc kết quả model |
| `evidence_ref` | string/null, ≤512 | Đường dẫn tương đối trong kho bằng chứng ngoài git, không phải đường dẫn trên máy cá nhân hoặc URL chứa token |
| `source_metadata` | object/null | Dữ liệu thô/provenance không đủ chuẩn đưa vào cột chính; không chứa credential |

`schema_version` và `classes_version` ở envelope lô API hoặc manifest kèm file;
mẫu hiện dùng schema 1.0 và `qcvn12-2026-10-19`.
Một phiên phát lại giữ nguyên `creator_id`, `captured_at`, `image_id`, `sequence_id`
của ảnh Mapillary; không biến người phát lại hoặc giờ upload thành bằng chứng mới.

Tên trong các README/PoC cũ là gợi ý/định dạng nguồn. Khi xuất hợp đồng mới:
`sequence→sequence_id`, `creator→creator_id` (thêm namespace), `captured→captured_at`
(giữ precision=day), `conf→detector_confidence`, `pano→is_pano`.
`sign_id` của PoC là `source_sign_id`, **không** phải `target_sign_id`.
`width/height` cũ không tự đổi sang `image_width_px/image_height_px` vì chưa biết
kích thước thumbnail; `lat/lon/compass_angle/sign_class` gợi ý trong AI README
được thay bằng các trường camera/biển và `sign_type_code` đã tách rõ ở hợp đồng này.

## Góc và tọa độ: khớp geometry.py

Vị trí lấy `computed_geometry`/`computed_compass_angle` nếu có, fallback sang
`geometry`/`compass_angle` và ghi nguồn trong metadata. GeoJSON luôn `[lon, lat]`;
bbox địa lý `[west,south,east,north]`. Khoảng cách dùng phép tính địa lý trả mét,
không so độ kinh/vĩ trực tiếp với 20 hay 30.

- Gọi `bearing(camera → sign)` là phương vị Bắc thuận chiều kim đồng hồ.
  `off_deg = ((bearing - camera_heading_deg + 180) mod 360) - 180`.
- `box_angle(cx,w,h,is_pano,focal)` trong
  [geometry.py](../../ai/livesign_ai/geometry.py) cho góc lệch cùng dấu, với
  `cx=(x1+x2)/2`. Phối cảnh: `atan((cx-w/2)/(focal*max(w,h)))` đổi sang độ;
  panorama: `(cx/w-0.5)*360`.
- `expected_x` là phép chiếu ngược theo chính quy ước trên. `angle_diff(a,b)`
  chỉ trả **độ lệch tuyệt đối [0,180]**, không dùng nó để tạo `off_deg` có dấu.
- Góc ngang phối cảnh lấy từ kích thước/tiêu cự; PoC giữ 85% nửa góc nhìn.
  Panorama có góc ngang 360° nhưng vẫn phải kiểm tra hướng mặt/độ nét/che khuất.
- Hướng mặt được so với `bearing(sign → camera)`, không so thẳng với hướng xe.
  Thiếu hướng mặt thì `facing_camera=null`; không tự lấy `camera_heading_deg`
  làm hướng mặt. Hướng xe khi tới biển thường gần hướng mặt cộng 180°.
- Geometry fallback focal=0,8 khớp file hiện có, nhưng dùng fallback do thiếu
  thông số camera **không đủ cơ sở tạo `not_seen`**.
- Bbox sau letterbox/resize phải đổi về ảnh xử lý gốc đã khai kích thước.
  Không ghép bbox thumbnail với kích thước ảnh full resolution.

## Quyết định outcome

| Outcome | Điều kiện | Có tính điểm không? |
| --- | --- | --- |
| `seen` | Phát hiện đúng loại/tấm biển, có bbox hợp lệ, confidence và manifest model; dữ liệu đủ truy vết | Dương nếu server xác minh đủ metadata và gán được vào tấm biển |
| `not_seen` | Có target_sign_id/loại cũ; không phát hiện biển cũ; khoảng cách **≤30 m**; cả bốn cờ **true**; camera/ảnh/bằng chứng đủ kiểm tra; bài thử cho phép dùng bằng chứng âm | Âm, trừ khi cùng cặp người/năm có seen |
| `unknown` | Không kiểm tra được/không đạt một điều kiện nhìn được, metadata thiếu, hoặc chưa quyết định được đúng tấm/loại | Không cộng α, β, không tạo cặp, không cập nhật độ mới |

Với `not_seen`, `bbox_px=null`, `detector_confidence=null` của loại cũ.
Không phát hiện đơn thuần, `mapillary_saw=false`, hoặc confidence thấp đều
**không đủ** kết luận `not_seen`. Phát hiện một loại khác được ghi bằng dòng `seen`
riêng; không xóa biển cũ, không coi hai biển cùng cột là một lần thay loại.
Khi `negative_evidence_enabled=null/false`, server từ chối not_seen bằng 422,
không âm thầm đổi nghĩa kết quả.

Trên tập đánh giá: model đọc ảnh độc lập gán khả năng nhìn được, người duyệt ngẫu
nhiên 60 ảnh và mọi ảnh không chắc; lưu người/model/phiên bản và tỷ lệ đồng ý.
Nhãn **khả năng nhìn được** tách khỏi outcome của YOLO đang được đo.
Trong app phát lại, quy tắc tự động thiếu cơ sở thì trả unknown; không giả định có
người gán tay mỗi lượt. Đây là giới hạn của phần triển khai tự động.

## Nhãn khả năng nhìn được (tách khỏi outcome)

CSV gán nhãn khả năng nhìn được dùng cùng khóa `source_sign_id`, `image_id`,
`creator_id`, `captured_at` như hợp đồng; thêm `visibility_label`:
`visible | not_visible | unknown`, `labeler`, `labeled_at`, lý do và phiên bản model gán.
Đây là nhãn độc lập dùng làm mẫu số phép đo, **không phải** outcome YOLO:
`visible` nhưng YOLO không thấy chỉ trở thành not_seen khi đủ toàn bộ điều kiện hợp đồng
và đã được nhóm duyệt dùng bằng chứng âm. Ảnh nhìn không được/không chắc vẫn unknown.
60 ảnh random là 60 `image_id` khác nhau; nếu một ảnh có nhiều target thì duyệt các target tương ứng.

## Gộp bằng chứng theo cặp

Chỉ lấy dòng đã nhập `accepted`, có người chụp, thời gian, loại và liên kết biển
được kiểm tra. Tại một cutoff, mỗi `(target_sign_id, creator_id, năm chụp)`:

1. Có ít nhất một `seen` → cặp dương, kể cả cũng có not_seen.
2. Không seen nhưng có not_seen hợp lệ → cặp âm.
3. Chỉ unknown → không có cặp bằng chứng.

`w=0,5^(T-t)`, `α=1+Σw_dương`, `β=1+Σw_âm`, `agreement_score=α/(α+β)`.
Đếm cặp dương thật để so N; không đếm prior hoặc số frame/sequence.
Hiển thị riêng điểm đồng thuận, tuổi bằng chứng dương, và lý do mâu thuẫn.
Độ cũ CHƯA RÕ xét **mọi bằng chứng hợp lệ mới nhất kể cả âm**; không có ảnh mới
không chứng minh biển đã bị gỡ. Không dùng dữ liệu sau cutoff hoặc giờ nạp.

## Mẫu có nguồn thật

[observation-sample.jsonl](observation-sample.jsonl): **10 dòng đầu** của
`tools/poc/results/per_image.csv`, giữ khóa `poc:<sign_id>:<image_id>` và toàn bộ dòng
thô ở `source_metadata.raw`, kèm số thứ tự dòng dữ liệu. `captured` chỉ có ngày nên
`captured_at_precision=day`. Không thêm GPS, hướng camera hoặc nhãn QCVN chưa được xem ảnh.

Mọi outcome mẫu là **unknown**: PoC chưa kiểm tra bốn cờ, YOLO-World chưa nhận
đúng 12 lớp, và không có kích thước thumbnail đáng tin trong CSV. `conf` gốc được
giữ làm `detector_confidence`; bbox gốc nằm trong raw, `bbox_px=null` để tránh sai hệ pixel.
Backend có thể lưu dạng `metadata_only`, không tạo sign hay điểm từ các dòng này.
Mẫu minh họa cấu trúc và provenance; không phải 10 nhãn ground truth.

Nếu chỉ có ngày, MySQL lưu `captured_at` ở 00:00:00 UTC với precision=day như
**quy ước lưu**, không khẳng định giờ chụp thật. Khi xuất lại, trả `YYYY-MM-DD`.
Mốc đánh giá đề xuất dùng năm UTC. Trường hợp nằm sát biên năm mà nguồn chưa rõ múi giờ
phải giữ ngoài tập chấm cho đến khi đối chiếu timestamp gốc.

## Thay đổi 1.1

Chỉ bổ sung provenance trong `source_metadata`, không thêm/đổi tên cột, outcome
hay quy tắc điểm 1.0. File JSONL PoC mẫu giữ nguyên; `schema_version=1.0` của payload
cũ vẫn hợp lệ. Số 1.1 là phiên bản tài liệu, không tự ép client đổi schema payload.

### Metadata tùy chọn dùng chung

| Nhánh object | Trường đề xuất | Dùng để |
| --- | --- | --- |
| `source_metadata.positioning` | method, source_sign_id, support_image_ids, ray_residual_m, ray_condition_number, fallback_reason | Truy vết vị trí, không biến feature Mapillary thành mốc chuẩn |
| `source_metadata.inference` | model_manifest_sha256, input_size_px, box_units, tile_origin_px, full_image_size_px, confidence_min, nms_iou | Kiểm tọa độ ảnh và parity batch/app |
| `source_metadata.provenance` | data_revision, original_captured_at, geometry_source, heading_source, visibility_audit_ref | Phân biệt dữ liệu gốc/dẫn xuất và nguồn kiểm tra nhìn được |

method dự kiến: `ray_least_squares`, `mapillary_feature`, `bbox_range`,
`camera_proxy`; đây là enum **provenance**, không phải status/outcome mới.
Ví dụ minh họa, không phải dòng mẫu PoC đã xác minh:

```json
{
  "positioning":{"method":"ray_least_squares","support_image_ids":["illustration:cam-1","illustration:cam-2"],"ray_residual_m":0.0,"ray_condition_number":1.0,"fallback_reason":null},
  "inference":{"model_manifest_sha256":"<hash manifest>","input_size_px":640,"box_units":"full_image_pixels_xyxy","tile_origin_px":[0,0],"full_image_size_px":[2048,1152],"confidence_min":0.25,"nms_iou":0.45},
  "provenance":{"data_revision":"illustration-r001","geometry_source":"computed_geometry","heading_source":"computed_compass_angle","visibility_audit_ref":null}
}
```

Toàn object trên là giá trị source_metadata. Bbox output của graph TFLite có thể
normalized nhưng **bbox_px cấp trên luôn pixel toàn ảnh**; phải unpad/unscale và
cộng offset tile trước. Không đưa bbox normalized vào box_angle().

### Quan sát phát lại và ảnh bằng chứng

- Replay thuần giữ nguyên dữ liệu và không sinh lượt độc lập mới. Re-infer Android
  dùng trip phiên riêng/source android_replay, model_version khai thật, nhưng creator,
  captured_at và sequence/image gốc không đổi. Không sửa source dưới cùng observation_id
  rồi mong API coi là duplicate; payload khác phải bị xung đột đúng hợp đồng.
- `evidence_ref` cho seen có thể là crop tấm biển; not_seen cần ảnh/ROI khu vực kỳ
  vọng và kiểm tra độc lập, không tạo crop một tấm mà detector không thấy.
  Manifest trip giữ ảnh toàn khung/camera gốc để admin và phép tính geometry kiểm lại.
- Nếu model đọc ảnh kiểm visibility khác model YOLO đang đo, lưu version/audit ref
  riêng trong provenance; detector_confidence không chứa confidence model gán visibility.
- Thiếu heading camera gốc nhưng có bearing từ GPS: không tự thay camera_heading_deg
  bằng bearing để tạo not_seen. Thiếu focal dùng fallback chỉ phục vụ gợi ý hình học.
- Kích thước source_metadata.raw của PoC không thay image_width_px cấp trên; không
  sửa file mẫu cũ để thêm GPS/hướng/bbox “đẹp” chưa có trong nguồn.

### Danh sách bổ sung 1.1

- Bổ sung các nhánh provenance/positioning/inference tùy chọn trong JSON có sẵn.
- Làm rõ đổi tọa độ tensor/tile về bbox pixel toàn ảnh, không đổi quy ước góc.
- Liên kết hợp đồng model/trace và cách tránh tăng bằng chứng do replay.
- Giữ nguyên 10 dòng mẫu và mọi điều kiện seen/not_seen/unknown 1.0.

## Thay đổi 1.2

Theo [kiến trúc 1.2](../architecture.md), định vị biển chạy trong module `core` sau khi gom
các lần thấy cùng tấm; batch AI không tự quyết vị trí cuối của biển.

- Với `seen` từ batch AI: `sign_lon`, `sign_lat`, `sign_facing_deg` cấp trên là **gợi ý**
  từ một ảnh hoặc `null`. `core` quyết `sign.location` theo thứ tự giao tia → feature Mapillary
  → camera_proxy ([algorithms.md](../algorithms.md)). Không bắt batch AI điền các cột này.
- Thêm `source_metadata.positioning.feature_candidates`: danh sách feature Mapillary cùng loại
  ánh xạ được, phía trước camera, trong 30 m, gần tia nhìn — mỗi phần tử
  `{source_sign_id, source_sign_type, lon, lat, ray_distance_m}`. `core` chọn theo luật liên kết
  feature; batch AI không chọn thay.
- `not_seen` được tạo ở **lượt 2**, sau khi đã có snapshot: `target_sign_id`, `sign_lon`,
  `sign_lat`, `sign_facing_deg` chép từ biển trong snapshot cùng năm, thêm
  `source_metadata.provenance.snapshot_run_id` để truy vết.

```json
{"positioning":{"method":null,"feature_candidates":[{"source_sign_id":"illustration:feature-1","source_sign_type":"regulatory--no-parking--g1","lon":105.84716,"lat":21.02554,"ray_distance_m":1.2}]}}
```

Ví dụ trên là giá trị `source_metadata` minh họa. `method=null` vì vị trí chưa được `core` quyết.

### Danh sách bổ sung 1.2

- Vị trí/hướng biển trong `seen` từ batch AI chỉ là gợi ý; `core` quyết vị trí cuối.
- Thêm `positioning.feature_candidates` và `provenance.snapshot_run_id`.
- Làm rõ not_seen được tạo ở lượt 2 sau snapshot.
