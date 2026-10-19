# ai — Nhận diện biển báo

Train YOLOv8n nhận diện biển báo Việt Nam, xuất TFLite cho app, và chạy nhận diện hàng loạt trên ảnh Mapillary để tạo "lần thấy" cho backend.

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

## Định dạng file lần thấy (đầu vào của backend)

Thống nhất với Backend trước khi viết code. Gợi ý một dòng CSV:

```
image_id, sequence_id, creator_id, captured_at, lat, lon, compass_angle, sign_class, conf, bbox, outcome
```

`outcome`: `seen` | `not_seen` | `unknown`.
