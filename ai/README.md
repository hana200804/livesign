# ai — Nhận diện biển báo

Train YOLOv8n nhận diện biển báo Việt Nam, xuất TFLite cho app, và chạy nhận diện hàng loạt trên ảnh Mapillary để tạo "lần thấy" cho backend.

## Việc thuộc phần này

| Mã | Việc | Tuần |
| --- | --- | --- |
| A1 | Gán trước 357 ảnh bài thử bằng model đọc ảnh, cả nhóm duyệt 60 ảnh | 1 |
| A2 | Train nhanh YOLOv8n trên bộ Roboflow, chạy trên ảnh 2048px → quyết giữ hay bỏ B3 | 1 |
| A3 | Tải dataset, lọc ảnh trùng, gộp nhãn về 10–15 loại biển | 1–2 |
| A4 | Train YOLOv8n chính, đóng gói TFLite | 2–3 |
| A5 | Nhận diện hàng loạt đoạn demo, xuất file lần thấy cho B2 | 4–5 |
| A6 | Fine-tune ~300 ảnh Hà Nội, đánh giá, viết chương mô hình | 6–7 |

## Cấu trúc dự kiến

```
ai/
  configs/      # file data.yaml, bảng gộp nhãn
  scripts/      # tải + lọc dataset, export TFLite, batch inference
  notebooks/    # notebook Colab
  results/      # bảng số liệu nhỏ (CSV), ảnh minh họa
```

Dataset và checkpoint để trong `data/` (không commit) hoặc Drive chung. Ghi đường dẫn Drive vào đây khi có.

## Dữ liệu train

- Zalo AI Challenge 2020 – Traffic Sign Detection (7 nhóm biển)
- Các bộ biển báo Việt Nam trên Roboflow / Kaggle

Chia train/test **theo nguồn** để tránh ảnh gần trùng lọt sang tập test.

## Định dạng file lần thấy (đầu ra A5)

Thống nhất với Backend trước khi viết code. Gợi ý một dòng CSV:

```
image_id, sequence_id, creator_id, captured_at, lat, lon, compass_angle, sign_class, conf, bbox, outcome
```

`outcome`: `seen` | `not_seen` | `unknown`.
