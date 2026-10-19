# Thiết kế kiểm thử và seed demo

Chưa tạo test chạy được/CI/project trong lần này. Đây là các kiểm tra phải triển
khai cùng tính năng sau khi nhóm duyệt hợp đồng. Tên/đơn vị theo
[API](contracts/api.md), [thuật toán](algorithms.md), [model](contracts/model.md),
[trace](contracts/trip-trace.md), [bộ mốc](contracts/ground-truth.md).

## Unit test điểm và vòng đời — bắt buộc

Fixture chung chính là ví dụ **bốn năm** trong algorithms.md: N=3, θ=0,75,
θ_thấp=0,40, H=2, K=2, base=0,5; cấu hình test riêng, **không điền vào N/θ null**
trong cấu hình vận hành. IDs người chụp giả danh, không dùng tên người phụ trách.

| Ca | Đầu vào | Kết quả cần khẳng định |
| --- | --- | --- |
| Prior không là bằng chứng | Không có cặp | α=1,β=1,score=0,5; count=0, không tự xác nhận |
| Năm đầu của ví dụ | 2022, bốn cặp dương | α=5,β=1,score=5/6; count dương=4; confirmed_auto |
| Giảm một năm | 2023 không có bằng chứng mới | α=3,β=1,score=0,75; tuổi=1; giữ xác nhận |
| Có một âm mới | 2024 thêm một người âm | α=2,β=2,score=0,5; tuổi dương=2, tuổi mọi bằng chứng=0; chưa nghi |
| Có hai âm gần nhất | 2025 thêm người âm khác | α=1,5,β=2,5,score=0,375; (a)/(b) true; suspected_change, không unclear |
| Phục hồi | Thêm người dương mới 01/08/2025 | α=2,5,β=2,5,score=0,5; hết mâu thuẫn, về nguồn confirmed_auto |
| Chỉ cũ không mất | Chỉ bốn cặp năm 2022, xét 2025 | α=1,5,β=1,score=0,6; tuổi=3>H →unclear, không removed |
| Một người nhiều chuyến | Cùng creator/năm có 50 seen | Một cặp dương; tăng frame/sequence không tăng N |
| Dương ưu tiên | Cùng người/năm có âm rồi dương | Một cặp dương, không vừa cộng α vừa cộng β |
| Unknown | Thêm 100 unknown mới | α/β/count/last_evidence_at không đổi |
| Thiếu người | creator_id=null | Không tạo cặp độc lập; lưu audit theo hợp đồng |
| Biên θ | Candidate, count≥N, score đúng 0,75 | Được vào auto khi test config đã chốt |
| Biên θ_thấp | Predicate (b), đã xác nhận, score=0,40 | (b) false; không dùng ≤ ở ngưỡng thấp |
| Biên H/K | Tuổi đúng 2; mới có một cặp âm | Chưa unclear do tuổi; chưa đủ K để (a) true |
| Tham số null | N/θ hoặc θ_thấp chưa chốt | Không tự lấy proposed; không auto; (b) báo chưa tính được khi thiếu θ_thấp |
| Cặp cùng ngày mơ hồ | +,-,- không biết giờ, K=2 | Không khẳng định (a); vẫn xét độc lập các dấu hiệu khác |
| Cùng ngày toàn âm | Hai cặp cuối chắc âm, chỉ hòa nội bộ | (a) true, không cần bẻ hòa bằng ID |
| Tấm khác loại | Hai loại cùng ảnh/cột | Không kích hoạt thay loại; hai target khác nhau |
| Gỡ/thay | removed/replaced có quan sát mới | Không tự hồi sinh hoặc đổi loại biển cũ |

Số thực so bằng dung sai biểu diễn đã quy định trong test, không so chuỗi làm
tròn hiển thị; ví dụ 5/6 khác 0,833333 một lượng làm tròn nhỏ. Chuyển trạng thái
phải so từ điểm chưa làm tròn. Dung sai số học do triển khai thống nhất, không đổi
θ theo số hiển thị.

## Unit test hình học, gộp và visibility

| Ca | Đầu vào / kỳ vọng |
| --- | --- |
| Góc tròn | angle_diff(350,10)=20; camera Bắc, biển Đông →off=+90; Tây →−90 |
| Tâm ảnh | box_angle(W/2)=0; expected_x(0)=W/2; pano seam wrap nhất quán |
| Giao tia tính tay | (0,0) hướng45° và (10,0) hướng315° →(5,5), λ=7,071 m, phần dư0 |
| Tia song song/sau camera | Ma trận suy biến hoặc λ≤0 →không đủ dữ liệu/fallback có nguồn |
| Bbox khoảng cách | f=1600px,H=0,6m,h=48px→Z20m; H null không suy độ sâu |
| Gộp biên | Cùng loại, 20m, lệch30° được xét; >20m hoặc >30° không auto merge |
| Đối diện | Cách8m, mặt0°/180° không gộp |
| Chung cột/hai bbox | Khác loại cùng ảnh, hoặc hai bbox tấm cùng loại →không gộp nhầm |
| Nối chuỗi | A–B15m,B–C15m,A–C30m không được suy cả ba là một tấm chỉ do liên thông |
| Giữ ID | Retry/import lại cùng manifest/registry giữ sign_id; không sinh ID từ centroid mới |
| Thiếu hướng | facing=null không auto merge theo hướng; không lấy heading camera thay |
| Nhìn được tính tay | 2048×1152,f=0,8 →hFOV≈32,01°, vùng giữa≈27,21°; off20° trong ảnh |
| Khoảng cách âm | 30m đạt phần khoảng cách; 30,01m không âm |
| Hướng mặt | face180°, bearing biển→camera185° →lệch5°, không dùng bearing chiều ngược |
| Bốn cờ | Mỗi lần đặt một cờ null/false →unknown nếu không có seen đúng |
| Âm chưa bật | Bộ sinh trả unknown; client cố gửi not_seen →422, không tự chuyển âm thành accepted |
| Focal fallback | Hình học gợi ý dùng0,8; thiếu camera không đủ tạo âm |
| Letterbox | Ví dụ model.md:1280×720→640; hộp input256,288,384,352→ảnh512,296,768,424 |
| Tiling | Cùng tấm ở hai ô sau NMS chỉ một observation; hai loại chung cột không NMS xuyên lớp |
| Heading GPS | Bearing chuyển động có nhưng camera heading thiếu →không tạo âm dựa trên hướng giả |

## Unit test chọn biển và replay

| Ca | Kỳ vọng |
| --- | --- |
| Vận tốc | v10m/s→D50m; v0→items rỗng, không chia0; v âm không hợp lệ |
| Loại xe | P.103a chỉ car/truck; P.131a cả ba; enum khác bị từ chối |
| Hướng/đường | Chỉ đạt khoảng cách chưa đủ; đối diện/song song chưa map match không nhận matched giả |
| Trạng thái nhắc | unclear/suspected_change có nhãn; candidate/removed/replaced không nhắc |
| Chống lặp | Nhiều response cùng sign/lần tiếp cận chỉ một TTS; biển khác cùng loại vẫn nhắc riêng |
| Seek/chuyển năm | Reset có log, bỏ response/ảnh generation cũ |
| Tốc độ replay | 2x chỉ đổi lịch hiển thị; t/creator/speed_mps gốc không đổi |
| GPS/gap | Bước250m/s hoặc gap>10s theo config thử →tách segment, không nội suy qua lỗi |
| Cảnh báo trễ | 45m ở10m/s→4,5s, ghi **không kịp** mục tiêu5s |

## Kiểm tra hợp đồng dữ liệu nhỏ

| File | Kiểm tra bắt buộc |
| --- | --- |
| observation-sample.jsonl | 10 dòng JSON; raw khớp 10 dòng đầu per_image.csv; mọi outcome unknown, thiếu GPS/bbox không bị điền giả; không tạo sign từ mẫu này |
| trip-trace-sample.jsonl | 25 dòng, UTC tăng2s, cùng sequence/creator, [lon,lat] đúng hệ, heading0°, speed9,95, data_kind=illustration |
| ground-truth-sample.csv | 4 dòng dữ liệu; CSV parse đúng ô JSON, mã có trong danh mục; old/new khác gt_id; uncertain/pending không thành mốc chắc |
| demo-corridor.geojson | Giữ nguyên file cũ candidate, LineString và bbox hợp lệ; không suy routing_validated=true |
| classes/dataset | 12 mã/thứ tự khớp, vehicle_types hợp lệ, classes_version cùng model/API |
| Params | Mọi giá trị đã chốt giữ nguyên; mới có status dự kiến; không tự thay null bằng proposed |

File mẫu mới ở data-small có ≤50 dòng; file GeoJSON có sẵn dài hơn không bị viết
lại hoặc nhận là mẫu mới. ID `illustration:*` phải được chặn khỏi corpus đo thật.
Kiểm cả name/type/unit/enum, không chỉ JSON parse được. Liên kết nội bộ và Mermaid
parse phải đạt trước khi coi tài liệu hoàn tất.

## Tích hợp Spring và MySQL 8

**Cần nhóm quyết:** đề xuất Testcontainers MySQL 8 cho test tự động sau khi có
project; dùng Compose hiện có cho kiểm tra thủ công. Lý do: test cần MySQL thật
cho SRID/SPATIAL/FK, không dùng H2 để khẳng định tính tương đương.

| Luồng | Điều phải chứng minh |
| --- | --- |
| DDL | Tám bảng gốc và bổ sung trip tạo được; CHECK/FK/index/collation đúng; ID khác hoa thường không bị gộp |
| POINT | Ghi WKT với axis-order=long-lat; đọc lon105,84715/lat21,02537, SRID4326; truy vấn bbox/distance ra mét |
| Nạp một trip | Envelope/dòng khớp source/sequence/creator; accepted+metadata_only+duplicate+rejected bằng request count |
| Idempotency | Gửi cùng ID/hash hai lần chỉ một observation; cùng ID/payload khác có conflict đúng dòng |
| Partial/error | Dòng sai không làm count sai; lỗi hệ thống có retry an toàn theo ID, không báo thành công khi chưa ghi bền |
| Snapshot | Ảnh 2024 không đổi bản2023; geometry/cluster/history cũng không rò, không chỉ lọc score |
| Trip/ảnh | Image phải thuộc trip/cutoff; JSONL hash representation sau lọc, manifest count đúng; thiếu ngày/giờ trả lỗi rõ |
| Quyết định | Hai client cùng version chỉ một transaction quyết; 409 bắt tải lại; rollback không để sign/history/review lệch |
| ACL | user/importer không gọi admin decision/evidence; path traversal/URL tùy ý không đọc được file; không trả secret |
| Lưu history | removed/replaced không xóa FK/quan sát; replacement khác sign cũ; timestamp trước/sau được kiểm |
| Cặp | Cùng người/năm nhiều sequence chỉ một; unknown không tăng α/β/độ mới |

Ranh giới DB/service phải được thử cả hai: FK không thay kiểm visibility, CHECK
không thay role/version; SQL cho phép JSON không đồng nghĩa nội dung JSON đúng schema.
Không công bố “đã kiểm MySQL” từ việc chỉ parse Markdown/Compose; daemon chưa chạy
trong lần chuẩn bị trước, runtime vẫn cần kiểm khi có môi trường.

## Seed demo và kỳ vọng

Seed là **đặc tả dữ liệu**, không tạo script/ảnh/model trong lần này. Kho ngoài git
cần manifest scenario_id, config thử đã đóng, ảnh hợp lệ hoặc placeholder minh họa,
trace và registry riêng. Không tạo tài khoản/mật khẩu mặc định hard-code trong repo.

| Dữ liệu seed | Nội dung | Kết quả kỳ vọng |
| --- | --- | --- |
| Danh mục | 12 sign_type sinh từ classes.yaml | Model/app/backend cùng mã và xe |
| Tài khoản | Ba role test, mật khẩu cấp ngoài git và hash bcrypt | Kiểm ACL theo tác nhân, không tự cấp quyền admin từ client |
| Trường hợp điểm | Bốn năm trong algorithms.md, creator giả danh | Score/trạng thái đúng bảng, có history theo năm |
| Replay minh họa | File25 điểm và placeholder image_id tương ứng | Đọc/phát/tạm dừng được, không nhận là ảnh Mapillary thật |
| Tấm cũ/mới | Hai gt_id minh họa old/new, hai sign_id và review riêng | suspected_change→admin replace; biển mới cần xác nhận riêng |
| Lỗi có chủ đích | Một ảnh thiếu, một GPS nhảy, response409 | UI báo đúng và không làm sai trạng thái |
| PoC thật | 10 observation unknown có raw nguồn | Chỉ audit metadata, không giả thành seed xác nhận |

Kết quả demo đánh dấu illustration/simulation. Để demo ca thật phải thay bằng
manifest ảnh đã kiểm trước/sau và nguồn công khai, vẫn giữ quyết định tái diễn
lịch sử tách khỏi đánh giá tự động thật. Không đưa fixture N=3/θ=0,75 vào vận hành
như ngưỡng nhóm đã chốt.

## CI tối thiểu — tùy chọn, chưa tạo workflow

| Gate | Điều kiện bật / kết quả |
| --- | --- |
| Docs/data | Parse Markdown links/Mermaid, YAML/JSONL/CSV/GeoJSON, enum/version/unit/sample size |
| Backend | Khi project tồn tại: build JDK21, unit logic, integration MySQL thật |
| Android | Khi project tồn tại: compile và unit preprocess/decoder; máy thật đo riêng |
| Dữ liệu cấm | Không token/.env thật/model/ảnh/large dataset; giữ kiểm tra Git sẵn có nếu repo đã có |

Không viết workflow giả báo build thành công khi backend/android chưa khởi tạo.
**Cần nhóm quyết:** bật CI build sau khi có project, trước mắt kiểm docs/data; lý do
là gate cần kiểm thứ thật sự tồn tại, không tạo cấu hình đỏ vì thư mục chỉ có README.
