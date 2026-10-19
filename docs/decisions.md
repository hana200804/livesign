# Quyết định chuẩn bị LiveSign

Cập nhật **19/10/2026**. Nguồn: đề cương v3.3 (PDF 19 trang, ngày 14/10/2026),
file repo trong [bảng đối chiếu](preparation-audit.md), và phản hồi trực tiếp của nhóm.
“Đã chốt” dưới đây có thể là phạm vi/công thức; không có nghĩa mọi giá trị thử nghiệm đã chốt.

## Đã chốt

| Nội dung | Quyết định / bằng chứng | Nguồn | Hạn hoặc ngày chốt |
| --- | --- | --- | --- |
| Phạm vi lần chuẩn bị | Chỉ hợp đồng, config, khung, tài liệu; không code tính năng, issue, khởi tạo project; đưa lên một PR nháp để cả nhóm xem | Nhóm qua người dùng | 19/10 |
| Phân công | Không chia việc sẵn; mỗi người tự nhận việc, ghi tên vào issue/PR khi bắt đầu; không báo cáo theo tuần | Nhóm qua người dùng | 19/10 |
| Định vị cho thang B0–B3 | Cả B0–B3 dùng chung định vị giao tia → feature Mapillary (camera_proxy không vào tập chấm, đếm riêng); cách làm phổ biến (vị trí ảnh + gộp 20 m) là dòng so sánh PB riêng | Nhóm qua người dùng | 19/10 |
| Hướng mặt biển | Ước lượng bằng trung bình tròn hướng ngược tia của các lần thấy ([algorithms.md](algorithms.md)); `facing.min_views=2`, `min_resultant_length=0,5` còn dự kiến | Nhóm qua người dùng | 19/10 |
| Đệm độ trễ khi nhắc | `D = speed × (warning_min_lead_s + warning_latency_buffer_s)`; đệm 1,5 s còn dự kiến, chỉnh sau khi đo; API 1.1 đã ghi | Nhóm qua người dùng | 19/10 |
| Thuật toán một nơi | Định vị, hướng mặt, gộp, điểm, vòng đời viết một lần trong module Java `core` không phụ thuộc Spring; dịch vụ và CLI đánh giá cùng dùng; script Python chỉ chấm điểm ([architecture.md](architecture.md)) | Nhóm qua người dùng | 19/10 |
| Snapshot dựng sẵn | Bảng `snapshot_run` + `sign_snapshot`, dựng sau mỗi lô/quyết định admin; API chỉ đọc run `ready` mới nhất | Nhóm qua người dùng | 19/10 |
| Phát lại không gọi mạng từng điểm | `GET /trips/{trip_id}/signs` một lần + Map Matching cả vết một lần; app lọc biển phía trước cục bộ; `/signs/ahead` để test đối chiếu | Nhóm qua người dùng | 19/10 |
| Định vị sau khi gom lần thấy | Batch AI gửi tư thế camera, bbox, `feature_candidates`; `core` định vị sau khi gom; not_seen tạo ở lượt 2 sau snapshot | Nhóm qua người dùng | 19/10 |
| Danh mục | 12 mã trong classes.yaml; đã đối chiếu phụ lục B/C/E QCVN chính thức; nguồn nhãn train còn mở | Nhóm duyệt có điều kiện; đã hoàn tất đối chiếu QCVN | 19/10 |
| Vùng ứng viên | Giữ vùng quanh ba ô rank đầu Hoàn Kiếm; **chưa chốt tuyến demo** | Nhóm qua người dùng | 19/10 |
| N và θ | Giữ **null** ở giá trị vận hành; chỉ ghi ứng viên N=3, θ=0,75. V3.3 nêu N dự kiến 2 và dải 2–3, chưa phải chốt | Nhóm qua người dùng | 19/10 |
| Dữ liệu | Chỉ Mapillary và dataset công khai; không có bước nhóm đi thu dữ liệu | Nhóm / v3.3 | Trước mọi lần nạp |
| Stack lõi | Kotlin/CameraX/TFLite/Vietmap/TTS; Spring Boot một khối + JWT; MySQL 8; Python/Ultralytics; Web React/Leaflet | V3.3 | Trước khởi tạo |
| Thành phần tùy chọn | MinIO có thể thay bằng ổ đĩa ban đầu; Redis/RT-DETR/FastAPI chỉ khi thật sự cần, sau mốc nghiệm thu phù hợp và ý kiến GVHD | V3.3, còn hỏi GVHD về phiếu giao đề tài | Trước mở rộng |
| Ba outcome | seen / not_seen / unknown; âm phải đủ điều kiện nhìn được ≤30 m; unknown không cập nhật điểm/độ mới | V3.3 + yêu cầu hiện tại | Trước tích hợp |
| Đơn vị bằng chứng | Cặp (creator_id, năm), dương ưu tiên nếu cùng cặp có cả seen và not_seen; prior không tính N | V3.3 | Trước triển khai điểm |
| Công thức tham chiếu | α=1+Σw dương; β=1+Σw âm; w=0,5^(T−t); điểm/độ mới/mâu thuẫn hiển thị riêng | V3.3; hệ số được thử trên dữ liệu trước mốc chấm | Trước đánh giá |
| Quyền gỡ/thay | Chỉ admin quyết sau khi xem ảnh trước/sau; không xóa sign/history | V3.3 | Trước web admin |
| Mốc đánh giá | Hết 2023, hết 2024, hết 2025; bộ mốc độc lập theo từng năm; auto/manual báo riêng | V3.3 | Trước đóng tập |
| Tuần 1 và hạn đồ án | Tuần 1 từ 19–25/10; kế hoạch 9 tuần kết thúc 20/12/2026, phù hợp khoảng giữa–cuối 12; ngày bảo vệ chính thức còn chờ | V3.3 + yêu cầu người dùng | Theo lịch trường |

## Còn mở — danh sách nhóm phải giải quyết

Không giao sẵn người chốt: ai nhận việc liên quan thì đưa số liệu, cả nhóm quyết.

| Quyết định | Đề xuất / dữ liệu cần có | Hạn dự kiến |
| --- | --- | --- |
| Tuyến demo cụ thể ~1,5 km | Xem GeoJSON ứng viên; kiểm tra chuỗi ảnh đi theo đường thật, hai hướng có ảnh khi có thể; xem ảnh thay đổi, không lấy centroid làm tim đường | 25/10 |
| N, θ và θ_thấp | Ứng viên 3 / 0,75 / 0,40; đo phân bố cặp và precision/coverage trước mốc test; N=1 chỉ là fallback cần nhóm duyệt lại | 25/10, trước triển khai tự xác nhận |
| H, K, hệ số giảm | Giá trị thử H=2 năm, K=2 cặp, base=0,5; xác nhận trên dữ liệu phát triển, không tune trên tập chấm | Trước khóa thí nghiệm |
| Bán kính/hướng gộp | 20 m và 30° là dự kiến; kiểm tra biển đối diện, hai tấm cùng cột và các đường song song trong thử 500 m | 25/10 |
| Quy tắc thứ tự cặp gần nhất | Đề xuất dùng thời điểm hợp lệ cuối cùng của mỗi cặp; nếu chỉ có ngày hoặc hòa thời điểm làm đổi kết luận K âm thì đánh dấu chưa đủ cơ sở, không tự suy thứ tự từ ID | Trước triển khai mâu thuẫn |
| Hệ năm / timestamp | Đề xuất chuẩn hóa UTC; ngày PoC chỉ precision=day; khôi phục timestamp gốc để xử lý sát biên năm | Trước đóng manifest |
| Khôi phục tập PoC | 357 dòng kết quả/333 ảnh; manifest hiện 353 dòng/332 ảnh, chỉ khớp 59 cặp. Khôi phục đúng manifest/ảnh gốc theo ID, không chạy fetch lại rồi coi là cùng tập | Trước gán nhãn/train thử |
| Ánh xạ và đủ mẫu 12 lớp | Tải Zalo/Roboflow/Kaggle, ghi version/giấy phép, số ảnh, nhãn chi tiết; nếu không đủ thì đề nghị nhóm thay lớp | Tuần 1–2 |
| Giữ hay bỏ B3 | ≥50% giữ; <30% bỏ; [30%,50%) chưa có quyết định trong v3.3, nhóm xem lỗi/độ tin cậy trước khi chọn. Chưa bật bằng chứng âm | 25/10 |
| Tài khoản/key/Drive | Mỗi người token Mapillary riêng; key/quota Vietmap; PAT chỉ đọc cho Colab; cả nhóm kiểm tra Drive | Trước làm việc tuần 1 |
| Điều khoản dataset / feature Mapillary | Ghi rõ giấy phép từng dataset version; tách điều khoản ảnh và dữ liệu feature; không công bố lại dữ liệu thô khi chưa kiểm tra | Trước dùng/chia sẻ dữ liệu |
| Khả năng chạy MySQL/SDK | Docker daemon hiện chưa kết nối; MySQL DDL chưa chạy. JDK/Studio/TFLite/key trên từng máy chưa được xác nhận | 25/10 |
| Chỉ tiêu tốc độ và gộp | Đo thật FPS/độ trễ và lỗi gộp; v3.3 không cho ngưỡng FPS hay % gộp chính xác cụ thể nên không tự gắn ngưỡng nghiệm thu mới | 25/10 |
| Bố cục API/SQL | Đặc tả 1.0 là bản đề xuất triển khai; chốt phân trang, rate limit, snapshot/history và lưu ảnh trước khi viết controller | Trước khởi tạo/viết tính năng |
| Registry gộp ổn định | **Cần nhóm quyết:** nhập tăng dần theo thứ tự cố định, giữ observation→sign và registry ID bền; chạy lại toàn khu ở workspace riêng để tránh đổi khóa review | Trước viết gộp |
| Cặp chỉ có ngày | **Cần nhóm quyết:** coi ngày là khoảng thời gian; chỉ kết luận K cặp cuối âm khi mọi thứ tự phù hợp đều cho kết quả đó, tránh suy thứ tự từ ID | Trước viết mâu thuẫn |
| Ưu tiên trạng thái | **Cần nhóm quyết:** mâu thuẫn trước cũ; giữ nguồn xác nhận khi phục hồi; θ chỉ để vào xác nhận, θ_thấp để nghi; candidate có thể nghi khi đủ chứng cứ | Trước viết vòng đời |
| Kiểm tra âm tự động | **Cần nhóm quyết:** chỉ âm khi kiểm tra độc lập đã audit; ngưỡng nét/che cần đo, thiếu kiểm tra trả unknown để tránh âm giả | Trước bật B3 |
| Chống nhắc và liên kết đường | **Cần nhóm quyết:** chống lặp theo lần tiếp cận (15 s/80 m dự kiến), thống nhất road_ref Vietmap–CSDL; D=v×(5 s+đệm), đo trễ thật để chỉnh đệm | Trước viết cảnh báo |
| Gói model cho app | **Cần nhóm quyết:** thử input 640 + float16 weights trước, đối chiếu 416/int8 bằng số đo và parity; tensor thực/exporter version phải có manifest để tránh giải mã nhầm | Trước bàn giao model |
| NMS model | **Cần nhóm quyết:** NMS theo lớp ở client với confidence 0,25/IoU 0,45 dự kiến, khóa bằng tập phát triển, giữ nhiều loại cùng cột | Trước đóng model manifest |
| Chuẩn replay và tốc độ | **Cần nhóm quyết:** tốc độ theo đoạn GPS/thời gian gốc, gap >10 s hoặc bước >40 m/s tách đoạn, heading camera thiếu để null, mặc định 1x; các ngưỡng cần đo | Trước chuẩn hóa trace |
| Replay và re-infer | **Cần nhóm quyết:** replay thuần không tạo quan sát mới; re-infer Android dùng trip phiên riêng, giữ sequence/creator/time gốc và model_version, tránh đổi source dưới cùng ID | Trước tích hợp app–API |
| Hiện trạng mốc theo năm | **Cần nhóm quyết:** chỉ chấm tồn tại/vắng khi có bằng chứng của năm, giữ uncertain/pending riêng; không suy first/last thành hiện trạng chắc mọi năm | Trước khóa bộ mốc |
| Ghép mốc một–một | **Cần nhóm quyết:** X=5 m/Y=30° dự kiến, matching tối đa số cặp rồi tối thiểu sai số; báo nhạy 3/5/10 m để tránh ưu thế từ thứ tự greedy/dung sai | Trước chấm kết quả |
| Cấp ảnh và trace replay | **Cần nhóm quyết:** kho ảnh chuẩn bị ngoài git, API kiểm trip/image/cutoff; giới hạn dung lượng trace sau khi đo để replay ổn định và giữ đúng quyền dữ liệu | Trước cung cấp dữ liệu app |
| Chứng cứ admin đã duyệt | **Cần nhóm quyết:** lưu watermark tập chứng cứ trong history, giữ điểm/âm gốc và chỉ mở lại ca khi có bằng chứng mới để tránh vòng lặp keep | Trước triển khai quyết định admin |
| Demo quyết định lịch sử | **Cần nhóm quyết:** CSDL demo riêng, manifest scenario_id và nhãn mô phỏng; không bỏ kiểm decided_at trong đánh giá thật | Trước chuẩn bị demo |
| Lưu metadata frame | **Cần nhóm quyết:** thêm metadata trace vào trip, image lookup ở manifest bất biến ngoài git, chưa thêm bảng ảnh; đo trước khi tăng độ phức tạp CSDL | Trước migration CSDL |
| Split và phiên bản dataset | **Cần nhóm quyết:** khóa nguồn/version sau kiểm đếm, giữ nhóm trùng cùng split và test Hà Nội độc lập; chưa cam kết tỷ lệ khi thiếu count thực | Trước train |
| Augment biển | **Cần nhóm quyết:** tắt flip ngang, chỉ thử augment không làm đổi nghĩa biển trên validation; không để thư viện tự đổi cấu hình giữa các run | Trước khóa train config |
| Tiling batch | **Cần nhóm quyết:** so toàn ảnh với ô 640/chồng 20% trên cùng tập phát triển; chọn bằng recall/time, không suy tiling luôn tốt từ PoC | Trước chạy batch chính |
| Đối sánh bài thử B3 | **Cần nhóm quyết:** đúng lớp/tấm + IoU≥0,50 với bbox gán độc lập, khóa trước thử để không nhầm tấm đúng hướng | Trước quyết định B3 |
| Cấu hình triển khai còn thiếu | **Cần nhóm quyết:** cơ chế JWT signing, đường dẫn evidence và base URL app; bổ sung .env.example trong thay đổi cấu hình riêng được duyệt, không hard-code trong tài liệu | Trước triển khai |
| Cài đặt xe trên app | **Cần nhóm quyết:** lưu lựa chọn xe/TTS cục bộ, chưa thêm API ghi hồ sơ; tránh mở endpoint chỉ vì schema có bảng | Trước viết cài đặt |
| Mở ca xác nhận thủ công | **Cần nhóm quyết:** POST admin/reviews tạo hoặc lấy ca pending của candidate/unclear, rồi dùng decision hiện có; không giả candidate là nghi thay đổi | Trước viết màn chi tiết admin |
| Tập chọn tham số | **Cần nhóm quyết:** dùng dữ liệu ≤2022 để chọn tham số cho ba mốc 2023–2025; nếu thiếu thì công bố ngưỡng quy ước và phân tích nhạy, không tune trên năm được chấm | Trước chạy thang đối chứng |
| Kịch bản tiêm mô phỏng | **Cần nhóm quyết:** khóa seed, 10 biển giả/5 tấm đổi, prior giả và thứ tự replay trong CSDL riêng; giữ control/censored và báo riêng ca thật | Trước mô phỏng |
| Quy trình đo thiết bị | **Cần nhóm quyết:** ít nhất 3 lượt 1x, làm nóng 30 s, pin 10 phút; khóa t_gặp/TTS và ngưỡng FPS/latency/pin sau pilot, không bịa chỉ tiêu | Trước đo app |
| Tích hợp MySQL để test | **Cần nhóm quyết:** Testcontainers MySQL 8 cho tự động, Compose cho kiểm tay; không dùng H2 để kết luận SRID/SPATIAL/FK tương đương | Khi có project |
| Gate CI | **Cần nhóm quyết:** kiểm docs/data trước, build/test JDK21 và Android chỉ khi project tồn tại; chưa tạo workflow trong lần tài liệu | Trước bật CI build |
| JDK Android giữa checklist và CI | **Cần nhóm quyết:** .github/workflows/ci.yml hiện Java17 cho Android, setup.md chọn JDK21; khóa AGP/Gradle rồi thống nhất phiên bản. Giữ nguyên nguồn trong lần chỉ viết tài liệu | Trước build Android |

## Đối chiếu nguồn cho bộ thiết kế 1.1

Repo là nguồn ưu tiên theo yêu cầu hiện tại. PDF ở gốc repo là **LiveSign v3.2**,
15 trang; không sửa hoặc commit PDF. Các khác biệt có bằng chứng:

| Nội dung | PDF v3.2 ở gốc | Repo hiện tại dùng |
| --- | --- | --- |
| Tuyến chính | Trần Phú; Hoàn Kiếm là đối chứng | README/GeoJSON: lõi Hoàn Kiếm, tuyến khoảng 1,5 km còn candidate; Trần Phú chỉ PoC |
| Bản đồ Android | PDF v3.2 trang 8 dùng MapLibre/OSM | android/README.md và decisions.md dùng Vietmap SDK + Map Matching; hình học OSM trong GeoJSON chỉ giúp chọn tuyến |
| Nhãn nhìn được | Gán tay mẫu 200–300 ảnh | observation.md: model độc lập + audit 60 ảnh và mọi ảnh không chắc |
| Danh mục/threshold | 10–15 loại, N dự kiến 2–3 | classes.yaml đã duyệt 12 mã; N/θ vẫn null, có proposed riêng |
| Tổ chức thực hiện | PDF có phân công và lịch | Theo quyết định repo mới: không giao người sẵn, tài liệu thiết kế không dùng mã việc hoặc lịch theo kỳ |

Giữ nguyên các ngưỡng đã chốt trong params. Những tham số thiết kế bổ sung đều
`status: dự kiến`; chưa phải kết quả đo. Bộ thiết kế căn cứ trạng thái repo trên
nhánh `docs/chuan-bi`, commit nguồn `f0433eb`; phần hợp đồng 1.0 được giữ nguyên,
các bổ sung đặt trong mục Thay đổi 1.1.

## Ba câu hỏi gửi GVHD trong v3.3

Chưa có bằng chứng đã gửi hoặc đã được trả lời. Nhóm tự gửi; lần chuẩn bị này không gửi thay.

| Câu hỏi | Đề xuất kèm lý do | Trạng thái / ai chốt | Hạn dự kiến |
| --- | --- | --- | --- |
| Cô có chấp nhận đánh giá bằng ảnh Mapillary cho đồ án không? | Xin chấp nhận dữ liệu công khai và đánh giá lịch sử phát lại, kèm số liệu/giới hạn tuần 1 | Còn mở; GVHD TS. Đào Thị Thúy Quỳnh | Hết tuần 1 |
| Trọng tâm quản lý độ tin cậy bản đồ và câu hỏi nghiên cứu của v3.3 có phù hợp không? | Giữ trọng tâm, chứng minh bằng B0–B3, precision/coverage và số ca admin thay vì chỉ mAP model | Còn mở; GVHD | Hết tuần 1 |
| Phiếu giao đề tài có bắt buộc DETR, Redis, MinIO hay được chuyển sang phần mở rộng? | Xin giữ tùy chọn để tập trung luồng lõi theo stack v3.3 | Còn mở; GVHD | Trước khởi tạo/thêm thành phần |

## Vòng đời phải giữ khi triển khai

- Cặp dương đầu tiên → candidate. Đủ N cặp dương thật **và** agreement_score ≥θ →
  confirmed_auto; N/θ null thì chưa được tự chạy bước này.
- Admin xác nhận khi chưa đủ N → confirmed_manual; báo riêng với auto.
- Biển đã xác nhận, quá H năm không có bằng chứng hợp lệ mới (cả dương lẫn âm)
  → unclear. Mất độ mới không đồng nghĩa bị gỡ.
- Mâu thuẫn: K cặp gần nhất âm; hoặc score <θ_thấp sau khi từng xác nhận; hoặc
  cùng vị trí/hướng có loại mới và loại cũ không thấy dù nhìn được, đã loại trừ
  nhiều tấm chung cột → suspected_change.
- unclear/suspected_change có cặp dương **mới** và hết mâu thuẫn → trở lại xác nhận;
  giữ nguồn auto/manual của lần xác nhận trước trong history. Không tự hồi sinh removed/replaced.
- Chỉ admin đưa sang removed/replaced. Ảnh trước/sau, lý do, người quyết và history bắt buộc.
  Nhắc app theo bảng trạng thái ở [api.md](contracts/api.md), chấm B0–B3 theo định nghĩa đánh giá riêng.

## Giới hạn phải thể hiện trong quyển

Độc lập theo tài khoản chỉ là đại diện gần đúng; điểm Beta là quy ước chưa hiệu chỉnh.
Các ca giả/thay loại trong CSDL là mô phỏng; không chứng minh hiệu quả ngoài đường.
Mapillary feature chỉ là gợi ý/đối chứng, không dùng train hay làm bộ mốc chuẩn.
Bộ mốc phải duyệt toàn đoạn, 20% tấm biển gán chéo; mô phỏng 10 biển giả + thay 5 biển,
ca thật tìm từ ảnh công khai nhiều năm và báo riêng. Không nêu tên/so sánh với đồ án cụ thể;
phương án đối chứng gọi là **cách làm phổ biến hiện nay**.
