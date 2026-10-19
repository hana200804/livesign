# LiveSign

Hệ thống nhận diện biển báo giao thông và giữ bản đồ biển báo luôn cập nhật, dùng ảnh đường phố Mapillary ở Hà Nội làm nguồn dữ liệu.

Đồ án tốt nghiệp: *Xây dựng hệ thống hỗ trợ nhận diện và hiển thị biển báo giao thông trên bản đồ số*.

- **Đề cương (bản đang dùng, v3.3):** https://claude.ai/code/artifact/5fb25879-ca3e-4661-bb5e-a228fbe17c18
- **Câu hỏi nghiên cứu:** kết hợp nhiều nguồn ảnh khác nhau, ưu tiên quan sát mới và xử lý mâu thuẫn có giảm được biển sai trên bản đồ, mà vẫn giữ được độ phủ và không làm admin quá tải?
- **Khu demo và đánh giá:** lõi Hoàn Kiếm, khoảng 1,5 km (chốt ở việc N1).

## Luồng dữ liệu

```
Ảnh Mapillary (mỗi chuỗi ảnh = một chuyến đi)
  → nhận diện biển (YOLOv8n, TFLite trên app / batch trên máy)
  → lần thấy: thấy / không thấy dù nhìn được / không xác định
  → gộp biển (loại + khoảng cách + hướng)
  → điểm tin cậy Beta giảm dần theo thời gian + vòng đời biển
  → web admin duyệt ca NGHI THAY ĐỔI
  → app nhắc biển phía trước khi phát lại chuyến đi
```

## Cấu trúc repo

| Thư mục | Nội dung | Phụ trách |
| --- | --- | --- |
| [`ai/`](ai/) | Chuẩn bị dữ liệu train, train YOLOv8n, xuất TFLite, nhận diện hàng loạt | AI |
| [`backend/`](backend/) | Spring Boot + MySQL: API lần thấy, gộp biển, điểm tin cậy, web admin | Backend |
| [`android/`](android/) | App Android: bản đồ Vietmap, phát lại chuyến đi, nhận diện trên máy, nhắc biển | Android |
| [`tools/`](tools/) | Script Node đo độ phủ Mapillary, PoC tuần 1, công cụ gán nhãn | Cả nhóm |
| [`docs/`](docs/) | Ghi chú kỹ thuật, kết quả thí nghiệm, bản thảo quyển | Cả nhóm |
| `data/` | Dữ liệu lớn (ảnh, dataset, model). **Không đưa lên git** | — |

## Bắt đầu

Yêu cầu chung: Git, Node ≥ 18. Mỗi phần có yêu cầu riêng trong README của thư mục đó.

1. Clone repo.
2. Tạo token Mapillary tại https://www.mapillary.com/dashboard/developers rồi đặt vào biến môi trường (không ghi vào file trong repo):
   ```powershell
   $env:MAPILLARY_TOKEN = "MLY|..."
   ```
3. Đọc [CONTRIBUTING.md](CONTRIBUTING.md) để biết cách đặt nhánh, commit và mở PR.

## Tiến độ

Công việc được theo dõi bằng GitHub Issues, mỗi tuần một milestone. Mã việc (N1–N5, A1–A6, M1–M4, B1–B5) giống bảng chia việc trong đề cương.

## Dữ liệu và giấy phép

Ảnh và dữ liệu Mapillary dùng theo giấy phép CC BY-SA 4.0. Không commit ảnh tải về. Khi đưa ảnh vào quyển hoặc slide, ghi nguồn "© Mapillary contributors".
