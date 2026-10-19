# data (không commit)

Mọi thứ trong thư mục này trừ file README bị `.gitignore` bỏ qua. Bản dùng chung của cả nhóm để trên Google Drive: _(điền link khi có)_.

Xếp theo cấu trúc này để script và config tìm đúng chỗ:

```
data/
  raw/                  # dataset tải về, giữ nguyên như gốc
    zalo2020/
    roboflow-<tên bộ>/
  processed/
    yolo/               # dataset đã gộp nhãn, định dạng YOLO
      images/{train,val,test}/
      labels/{train,val,test}/
  mapillary/            # ảnh + metadata Mapillary của đoạn demo
  runs/                 # kết quả train của Ultralytics (checkpoint, biểu đồ)
  models/               # file model chốt để dùng: *.pt, *.tflite
```
