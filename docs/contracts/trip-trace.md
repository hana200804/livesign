# Hợp đồng vết chuyến đi để phát lại

Schema thiết kế 1.1. Một chuyến là một sequence Mapillary hoặc một phiên replay;
không phải một người chụp mới. File **JSONL UTF-8**, một dòng một ảnh/điểm,
không dùng NaN. Chỉ nhập metadata/ảnh công khai đã kiểm quyền sử dụng.

## Bản ghi và manifest

| Trường | Kiểu / đơn vị | Quy tắc |
| --- | --- | --- |
| `t` | string UTC RFC3339 | Timestamp chụp gốc với độ chính xác thực; phải tăng trong segment |
| `lon`, `lat` | number, độ | WGS84, `[lon,lat]`; giữ GPS gốc, không ghi đè bằng điểm map match |
| `heading_deg` | number/null, độ | Hướng camera [0,360), Bắc=0 thuận kim đồng hồ; ưu tiên computed_compass_angle |
| `speed_mps` | number/null, m/s | Không âm; thiếu tốc độ gốc thì tính từ GPS/thời gian, ghi phương pháp trong manifest |
| `image_id` | string | ID ảnh, không ép thành số; link lấy ảnh qua API được cấp quyền |
| `sequence_id` | string | Một giá trị cho toàn file; không nối hai sequence vào một chuyến giả |
| `creator_id` | string/null | `mapillary:<id>` người chụp gốc; thiếu thì không đủ tính bằng chứng độc lập |
| `segment_id` | integer ≥0 | Tăng sau gap/GPS lỗi; không nội suy hoặc tính tốc độ qua ranh giới |
| `data_kind` | enum | `public_metadata` hoặc `illustration`; minh họa không vào thí nghiệm thật |

Manifest đi kèm ngoài git gồm `trip_id`, `trace_schema_version=1.1`,
`trace_ref`, `trace_sha256`, source URL/ngày lấy, sequence/creator,
`heading_method`, `speed_method`, `time_precision`, số điểm/segment, params_version
+ hash, danh sách điểm loại và lý do, attribution. `trace_ref` của trip 1.0 giữ nguyên
là tham chiếu kho dữ liệu; file JSONL là định dạng bổ sung, không đổi nghĩa cột cũ.

`trace_ref` phải có manifest khai media type; không dựa mỗi phần mở rộng. API 1.0
minh họa trace.geojson; với dữ liệu cũ giữ nguyên file/định dạng đó, importer có thể
chuẩn hóa sang JSONL **bản mới có hash riêng**, không đổi bytes âm thầm dưới cùng ref.

## Tạo từ sequence Mapillary

```text
ĐỌC tất cả trang ảnh của một sequence, giữ image_id và creator_id gốc
LẤY computed_geometry/heading nếu có; fallback geometry/compass_angle và ghi provenance
ĐỔI timestamp epoch thực sang UTC; sắp t, kiểm trùng image_id/timestamp
GIỮ ảnh có GPS/thời gian; điểm chỉ có ngày không dựng được replay theo giây
VỚI mỗi cặp điểm liên tiếp cùng đoạn:
  Δt=t_sau−t_trước; d=khoảng cách địa lý mét
  nếu Δt<=0 hoặc d/Δt vượt max_gps_speed_mps: tách đoạn/kiểm tra, không sửa timestamp
  nếu Δt>max_gap_s: tách đoạn, speed ở ranh giới=null
  nếu thiếu tốc độ gốc và cặp hợp lệ: speed_mps=d/Δt
GIỮ heading camera null nếu nguồn thiếu; bearing GPS chỉ là hướng di chuyển gợi ý
XUẤT JSONL + manifest + danh sách lỗi; không tải ảnh/model vào git
```

Đề xuất gán tốc độ đoạn i→i+1 cho điểm i; điểm cuối dùng tốc độ đoạn trước nếu
hợp lệ và cùng segment, nếu không null. Timestamp bằng nhau nhưng GPS khác nhau
không được chia cho 0 hoặc bẻ hòa bằng thêm mili giây giả.
Heading chuyển động từ GPS không thay `camera_heading_deg` khi tính tia nhìn:
camera có thể quay bên hông/xe đứng yên. Bản ghi thiếu hướng camera thì replay
vẫn xem ảnh được, nhưng không tự tạo bằng chứng âm hoặc cảnh báo theo hướng chắc chắn.

Ví dụ tính tay: hai điểm cách 19,9 m, chụp cách 2 s → speed=9,95 m/s;
camera heading=0°; không nhân thành 19,9 m/s khi trình diễn 2x.
Bước nhảy 500 m/2 s=250 m/s vượt ngưỡng thử 40 → tách đoạn và báo lỗi.
`replay.max_gap_s=10`, `max_gps_speed_mps=40` đều dự kiến, không phải tốc độ giới hạn đường.

**Cần nhóm quyết:** dùng quy tắc tốc độ theo đoạn, ngắt gap/GPS như trên và phát mặc
định 1x; giữ heading camera thiếu là null. Lý do: không biến GPS nhảy hoặc bearing
xe thành thông số camera đã đo.

## Đồng hồ replay, map matching và ghi lần thấy

| Tình huống | Xử lý |
| --- | --- |
| Phát 1x | Đồng hồ replay bám `t`; nhắc biển dùng speed_mps thật, đo bằng timestamp gốc |
| Trình diễn 0,5x/2x | Chỉ đổi lịch hiển thị `Δt/rate`; không đổi t/captured_at/speed_mps, không trộn với kết quả đo 1x |
| Seek/đổi năm/chuyến | Hủy request cũ, tăng generation của phiên, reset chống nhắc và ghi log reset |
| Ảnh tải chậm | Dừng hoặc bỏ frame có log; không gắn bbox ảnh cũ lên vị trí ảnh mới |
| GPS bị tách đoạn | Dừng ở ranh giới, báo thiếu vết; không phát cảnh báo trong đoạn chưa có định vị hợp lệ |
| Map matching | Giữ tọa độ gốc; tọa độ/road_ref đã khớp là lớp dẫn xuất riêng, ghi nguồn/phiên bản |
| Mất mạng | Dùng trace/ảnh cache đã có nếu được phép, báo rõ; không giả đã gửi observation hoặc tải snapshot mới |

Endpoint bổ sung ở [api.md](api.md): `GET /trips`, `GET /trips/{trip_id}/trace`,
`GET /trips/{trip_id}/images/{image_id}`. Response trace JSONL có hash/manifest;
ảnh trả bytes, không để app tự dùng Mapillary token chung trong URL.

Quan sát tạo ra dùng `captured_at=t`, `camera_lon=lon`, `camera_lat=lat`,
`camera_heading_deg=heading_deg`. `trip_id=mapillary:<sequence_id>` cho nhập gốc;
phiên re-infer Android dùng `replay:<session_id>:<sequence_id>`, `source=android_replay`
và giữ sequence/creator/time gốc. Quan sát AI đã nhập không gửi lại thành observation
khác source/payload dưới cùng ID; replay chỉ phát lại thì không tạo bằng chứng mới.
Re-infer dùng model_version mới có ID riêng, nhưng gom cùng creator/năm nên không
tăng số cặp độc lập; không cho người phát lại trở thành người chụp mới.

**Cần nhóm quyết:** tách phiên re-infer và replay thuần như trên; lý do là source/trip
trong lô 1.0 bất biến, thay source dưới cùng observation_id sẽ đúng là lỗi 409.

## File mẫu

[trip-trace-sample.jsonl](../../data-small/trip-trace-sample.jsonl) có **25 điểm minh họa**,
không phải trace Mapillary thật. CSV PoC trong repo không có GPS/hướng tuyệt đối và
timestamp giây nên không đủ dựng trace thật. Các ID `illustration:*` không tồn tại
trên Mapillary, không dùng để tải ảnh, map matching thật hoặc chấm kết quả.
Manifest mẫu: sequence `illustration:sequence-01`, creator `illustration:creator-01`,
time_precision=instant, speed_method=synthetic_constant, heading_method=synthetic,
trace_schema_version=1.1. Vị trí đặt gần vùng ứng viên chỉ để kiểm đơn vị/đọc file,
không khẳng định nằm trên đường đúng một chiều.

Điều kiện đọc: 25 dòng JSON hợp lệ, UTC tăng 2 s, cùng sequence/creator,
segment_id=0, lon/lat hữu hạn, heading=0°, speed=9,95 m/s. Khoảng cách tính từ
tọa độ có sai khác làm tròn nhỏ; đây không phải phép đo vận tốc ngoài đường.
