# Đối chiếu hiện trạng trước khi viết tính năng

Đọc ngày 19/10/2026, trước thay đổi trên nhánh `docs/chuan-bi`. Căn cứ: các README,
config và CSV thật trong repo; bản PDF người dùng cung cấp có tiêu đề **LiveSign v3.3**,
ngày 14/10/2026, 19 trang. Bản gốc là
[Claude Doc](https://claude.ai/code/artifact/5fb25879-ca3e-4661-bb5e-a228fbe17c18).
Không sửa bản đề cương hoặc các README được yêu cầu chỉ đọc.

| Phần của đề cương | Có trước lần chuẩn bị này | Thiếu trước lần chuẩn bị này |
| --- | --- | --- |
| Ba kết quả; hình học | AI README gợi ý 3 outcome; `geometry.py` có góc dương bên phải | Cột, đơn vị, điều kiện nhìn được và quy tắc nhập mẫu |
| Danh mục lớp | 7 nhóm nháp; ánh xạ Zalo ghi là nhớ chưa kiểm chứng | 10–15 mã QCVN, phạm vi xe, ánh xạ annotation đã đối chiếu |
| Điểm tin cậy, vòng đời | README mô tả Beta và nghi thay đổi | Tham số có trạng thái; cặp người/năm; trạng thái và hợp đồng admin |
| Công nghệ, CSDL | README định hướng Spring Boot/MySQL, Kotlin/Vietmap, YOLOv8n | API/SQL/Compose; chưa có project Spring hoặc Android |
| Đánh giá B0–B3 | Kết quả YOLO-World PoC và số liệu coverage | Bộ mốc độc lập theo năm; train thử Việt Nam 2048px; quyết định giữ B3 |
| Tuần 1, phân công | Tool coverage/trip-check/rank, fetch-signs và server gán nhãn | Checklist, 6 việc có tiêu chí/fallback và tên người nhận |

## Những gì số liệu thật chứng minh

- [summary.json](../tools/mapillary-coverage/out/summary.json): Hoàn Kiếm có
  101.594 ảnh, 1.862 sequence, 3.692 biển gợi ý Mapillary, các năm 2016–2026
  (không có 2021 trong danh sách); 38% chiều dài đường có ảnh ít nhất hai năm.
- [hoankiem-segments.csv](../tools/mapillary-coverage/out/hoankiem-segments.csv):
  ba dòng đầu có score 149, 147, 138; tâm ô là trung bình tọa độ biển,
  **không phải tọa độ tim đường hoặc vết chuyến đi**. `rank-segments.mjs` dùng ô
  0,002 độ và cộng số loại, số biển nhiều người/năm, ứng viên thay đổi có giới hạn trên.
- [per_image.csv](../tools/poc/results/per_image.csv): **357 dòng lần kiểm tra
  biển–ảnh, 333 image_id riêng biệt**, 20 sign_id. Không gọi là 357 ảnh duy nhất.
- [summary.csv](../tools/poc/results/summary.csv): cùng 357 dòng, tỷ lệ phát hiện
  8% với confidence 0,10 và 2% với 0,25. Đây là YOLO-World `traffic sign`,
  chưa phải mô hình 12 lớp Việt Nam hoặc tỷ lệ phát hiện trên tập nhìn rõ.
- [images.csv](../tools/poc/out/images.csv) hiện có **353 dòng, 332 image_id**;
  chỉ **59 cặp (sign_id,image_id)** xuất hiện ở cả manifest và kết quả.
  Cần khôi phục manifest đúng tập đã chạy trước khi đóng tập. Chưa có `labels.csv` trong repo.
- [segment.json](../tools/poc/out/segment.json) và `fetch-signs.mjs`: PoC là cửa sổ
  500 m Trần Phú, Hà Đông, lọc ảnh 5–35 m. Không phải đoạn demo Hoàn Kiếm;
  không tái sử dụng ngưỡng 35 m cho bằng chứng âm của hợp đồng mới.
- CSV PoC không giữ GPS/hướng camera tuyệt đối, chỉ giữ ngày chụp và góc tương đối.
  Bbox model tính trên ảnh thumbnail nhưng `width/height` là metadata ảnh gốc.
  Vì vậy file mẫu giữ bbox thô trong `source_metadata`, không giả kích thước ảnh xử lý.

## Kết quả chuẩn bị cần đọc tiếp

[Setup](setup.md) · [Lớp](contracts/classes.md) · [Lần thấy](contracts/observation.md) ·
[API](contracts/api.md) · [CSDL](contracts/db-schema.md) · [Tham số](../config/params.yaml) ·
[Ứng viên demo](../data-small/demo-corridor.geojson) · [Quyết định](decisions.md) · [Tuần 1](week1.md).

Các file này đặc tả việc sẽ triển khai; không phải bằng chứng đã train, đã chạy app,
đã dựng backend hoặc đã hoàn thành nghiệm thu tuần 1.
