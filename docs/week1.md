# Tuần 1 — kiểm tra tính khả thi

**19–25/10/2026**. Hân = AI; Nam = Backend + Web; Anh = Android, đã được người dùng xác nhận.
Bảng là kế hoạch, **chưa thực hiện** gán nhãn, train, khởi tạo project hoặc code nghiệp vụ.
Các bước viết code/khởi tạo ở tuần 1 chỉ bắt đầu sau khi nhóm cho phép riêng.
Trước hết mỗi người hoàn thành [setup.md](setup.md). Các quyết định còn mở ở [decisions.md](decisions.md).

## Sáu việc

| Việc | Người làm | Đầu ra | Tiêu chí xong hết tuần 1 | Nếu không đạt thì… |
| --- | --- | --- | --- | --- |
| Chốt đoạn demo Hoàn Kiếm | Cả ba: Hân kiểm ảnh/nguồn, Nam bảng rank/cặp người–năm, Anh đối chiếu đường/phát lại | Tuyến liền ~1,5 km, bbox, tên phố, ID sequence/ảnh và ứng viên thay đổi đã xem; cập nhật GeoJSON sau khi nhóm chốt | Nhóm ghi nhận chốt tuyến; có ảnh + metadata cho 20 biển trên một đoạn thử 500 m; xem trước/sau 3–5 ứng viên theo kế hoạch, ít nhất xác định 1–2 ca thật rõ để demo, tiếp tục hoàn thiện bộ mốc tuần 2–3 | Chọn vùng rank kế tiếp trong Hoàn Kiếm bằng ảnh công khai; giảm tuyến còn 1 km khi cần theo mốc tuần 3; báo thiếu ca thật, không coi maybe_removed/maybe_new là thay đổi đã xác minh |
| Gán nhãn tập 357 lần kiểm tra PoC | Hân dẫn: model đọc ảnh độc lập; cả ba duyệt ngẫu nhiên 60 ảnh, mỗi người 20, và chia các ảnh chưa chắc để xem thêm | Manifest đã khôi phục, CSV khả năng nhìn được cho 357 cặp/333 ảnh và biên bản kiểm chéo | Ít nhất 200–300 dòng được gán 3 nhãn theo mốc nghiệm thu; mục tiêu đóng đủ 357 cặp. Báo tỷ lệ đồng ý người–model, số ảnh không chắc, nguyên nhân che/mờ/lệch hướng | Khôi phục đúng ảnh theo ID, không thay bằng tập fetch mới; nếu chưa đủ nhãn thì giữ unknown, giảm phạm vi B3, không lấy model không phát hiện làm nhãn âm |
| Train thử và quyết định giữ/bỏ B3 | Hân train/đo; cả ba xem kết quả để chốt phạm vi | Checkpoint thử trên Colab/Drive, manifest model/dataset, tỷ lệ phát hiện đúng trên ảnh nhìn rõ **2048px**, quyết định bằng văn bản | Mẫu số là các cặp được xác minh nhìn rõ và đúng lớp mục tiêu; tử số là phát hiện đúng tấm/loại. **≥50% giữ B3; <30% bỏ B3 và giữ B0–B2; [30%,50%) trình nhóm quyết**, không tự kết luận. Ghi số mẫu, confidence/đối sánh dùng trong phép đo | Thiếu mẫu số thì chưa đủ kết luận; không thay bằng tỷ lệ 2–8% trên toàn bộ PoC 1024px. Nếu <30%, tắt bằng chứng âm thực nghiệm, đánh giá mâu thuẫn bằng mô phỏng và công bố giới hạn |
| Chuẩn bị dataset công khai | Hân; Nam/Anh review danh mục dùng chung | Kiểm kê nguồn/version/giấy phép, mapping nhãn đúng 12 mã, nhóm gần trùng và manifest split | Tải/đếm thực tế Zalo/Roboflow/Kaggle; kiểm từng lớp, lọc gần trùng giữa nguồn; split theo nguồn/sequence, giữ nhóm trùng cùng split. Tuần 1 có kiểm kê/mẫu sạch, hoàn thiện bộ sạch tuần 2 | Không đủ dữ liệu lớp nào thì báo nhóm duyệt đổi lớp; giữ mapping rỗng/chưa training_ready cho nguồn chưa rõ; dùng nguồn công khai khác phù hợp, không tự thu dữ liệu |
| App khung và TFLite trên máy thật | Anh; Hân bàn giao model mẫu ngoài git | Ví dụ/app khung Vietmap chạy trên thiết bị Android thật, TFLite mẫu, biên bản FPS/độ trễ/model máy | Key Vietmap dùng được, bản đồ tải được; TFLite suy luận được trên ảnh mẫu/phát lại, ghi FPS và thời gian đo, không chỉ chạy emulator. Chưa yêu cầu model chính có độ chính xác cuối | Giảm input 416 hoặc xử lý nửa số frame theo v3.3 nếu chậm; dùng model mẫu trong lúc chờ model 12 lớp; báo lỗi SDK/key cụ thể. Khởi tạo project vẫn cần nhóm cho phép trước |
| CSDL và gộp thử 500 m | Nam; Hân cung cấp metadata, Anh đối chiếu bản đồ | DDL đã review, MySQL 8 chạy Docker, bảng/ảnh bản đồ gộp thử B0 trên 500 m bằng detection Mapillary có sẵn | Nhập đúng cột/đơn vị, POINT 4326/SPATIAL; gộp cùng loại 20 m theo B0, ghi ca gộp nhầm/tách nhầm bằng mắt, giữ nhiều tấm chung cột. Lưu cả creator_id/captured_at; không dùng feature Mapillary làm ground truth | Nếu chưa có bản đồ 500 m gộp tương đối đúng thì cắt mở rộng ngay, sửa dữ liệu/đơn vị trước; chưa đẩy sang độ tin cậy hay admin. N ít người chụp có thể đề xuất N=1 nhưng **nhóm phải duyệt**, không tự thay config |

## Nhãn nhìn được khác outcome

CSV gán nhãn khả năng nhìn được dùng cùng khóa `source_sign_id`, `image_id`,
`creator_id`, `captured_at` như hợp đồng; thêm `visibility_label`:
`visible | not_visible | unknown`, `labeler`, `labeled_at`, lý do và phiên bản model gán.
Đây là nhãn độc lập dùng làm mẫu số phép đo, **không phải** outcome YOLO:
`visible` nhưng YOLO không thấy chỉ trở thành not_seen khi đủ toàn bộ điều kiện hợp đồng
và đã được nhóm duyệt dùng bằng chứng âm. Ảnh nhìn không được/không chắc vẫn unknown.
60 ảnh random là 60 `image_id` khác nhau; nếu một ảnh có nhiều target thì duyệt các target tương ứng.

## Thứ tự và bằng chứng nghiệm thu

Đầu tuần kiểm môi trường, khôi phục manifest và kiểm ảnh vùng ứng viên; không chạy lại
fetch-signs rồi ghi đè PoC đã đo. Gán nhãn nhìn rõ trước phép đo train thử. Chuẩn bị
dataset, thử máy thật và review SQL có thể tiến hành độc lập theo vai trò.

Cuối ngày 25/10, cả ba review: tuyến đã chốt hay chưa; 20 biển/500 m có metadata;
357 cặp đã xử lý bao nhiêu; 60 ảnh đã kiểm chéo; tỷ lệ model có sẵn trên PoC;
tỷ lệ model Việt Nam trên ảnh nhìn rõ 2048px; quyết định B3; FPS máy thật;
lỗi gộp và bản đồ 500 m. Ghi thiếu mục nào và quyết định cắt phạm vi, không đánh dấu
xong dựa trên tài liệu hoặc config. Không tự tạo issue/push/PR trong lần chuẩn bị này.

Tuần 2–3 tiếp tục bộ mốc độc lập theo năm trên toàn tuyến, 20% tấm biển gán chéo,
tìm ca thay đổi thật. Không chọn N/θ bằng tập chấm B0–B3 ở hết 2023/2024/2025.
