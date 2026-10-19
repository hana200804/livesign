# Quyết định chuẩn bị LiveSign

Cập nhật **19/10/2026**. Nguồn: đề cương v3.3 (PDF 19 trang, ngày 14/10/2026),
file repo trong [bảng đối chiếu](preparation-audit.md), và phản hồi trực tiếp của nhóm.
“Đã chốt” dưới đây có thể là phạm vi/công thức; không có nghĩa mọi giá trị thử nghiệm đã chốt.

## Đã chốt

| Nội dung | Quyết định / bằng chứng | Nguồn | Hạn hoặc ngày chốt |
| --- | --- | --- | --- |
| Phạm vi lần chuẩn bị | Chỉ hợp đồng, config, khung, tài liệu; không code tính năng, issue, khởi tạo project; đưa lên một PR nháp để cả nhóm xem | Nhóm qua người dùng | 19/10 |
| Phân công | Không chia việc sẵn; mỗi người tự nhận việc, ghi tên vào issue/PR khi bắt đầu; không báo cáo theo tuần | Nhóm qua người dùng | 19/10 |
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
