# Hợp đồng model ↔ Android

Thiết kế 1.1, **chưa có model được xuất/đo**. Danh mục duy nhất là
[classes.yaml](../../ai/configs/classes.yaml), 12 lớp, `classes_version=qcvn12-2026-10-19`.
Hợp đồng này dành cho **YOLOv8n detect** của repo, không áp mặc định tensor của các
đời YOLO khác. `ai/requirements.txt` cho phép nhiều phiên bản Ultralytics; mỗi lần
export phải ghi **phiên bản thực và cấu trúc tensor thật**, không suy từ tên file.

## Gói bàn giao ngoài git

| File | Nội dung bắt buộc |
| --- | --- |
| `.tflite` | Bytes model, SHA-256, cỡ file; chỉ lưu `data/models/` hoặc Drive chung |
| `labels.txt` | 12 dòng mã QCVN đúng class_id 0–11, không tiêu đề/BOM; sinh từ classes.yaml |
| `model-manifest.json` | model_version, classes_version, params_version + hash config, exporter/runtime versions, shape/dtype/quantization, chuẩn xử lý |
| `parity-cases.jsonl` | Ảnh kiểm tra có quyền dùng, hash ảnh, bbox/conf/class mong đợi và dung sai; ảnh ngoài git |

`model_version` đề xuất: `livesign-yolov8n-qcvn12-r001-i640-fp16`.
Đổi checkpoint, thứ tự lớp, input, precision, exporter, NMS hoặc xử lý ảnh → tăng revision;
không ghi đè bytes dưới cùng tên version. App kiểm hash/classes_version trước khi load;
Backend lưu nguyên model_version vào observation, không dùng tên “latest”.

Manifest minh họa schema, **không đại diện model đã tạo**:

```json
{
  "model_version":"livesign-yolov8n-qcvn12-r001-i640-fp16",
  "classes_version":"qcvn12-2026-10-19",
  "params_version":"prep-2026-10-19",
  "config_sha256":"<hash của file params đã đóng>",
  "artifact_sha256":"<hash của bytes tflite>",
  "exporter_version":"<phiên bản thực>",
  "status":"illustration",
  "input":{"shape":[1,640,640,3],"layout":"NHWC","dtype":"float32","color":"RGB","scale":null,"zero_point":null},
  "output":{"shape":[1,16,8400],"layout":"BCN","dtype":"float32","box_units":"normalized_xywh","scale":null,"zero_point":null},
  "letterbox":{"width":640,"height":640,"center":true,"padding_rgb":[114,114,114],"auto_stride_pad":false},
  "nms":{"location":"client","class_agnostic":false,"confidence_min":0.25,"iou":0.45}
}
```

**Cần nhóm quyết:** đề xuất thử **640 + float16 weights** trước, so với **416**
và int8 trên đúng máy thật. Lý do: biển nhỏ cần độ phân giải, còn tốc độ/bộ nhớ cần đo.
`model.input_size_px/precision` vẫn null, `proposed` không phải cam kết xuất.
Float16 weights không đồng nghĩa tensor vào/ra đều float16; phải đọc tensor details.
Int8 dùng tập hiệu chuẩn đại diện từ train/validation đã đóng, không dùng test Hà Nội;
đo mAP/recall sau lượng tử hóa, lưu danh sách calibration và scale/zero_point.

## Tiền xử lý xác định được

Ảnh giải mã và xoay theo EXIF trước; ghi kích thước sau xoay là W,H trong observation.
Không mirror ảnh để “hợp UI” vì sẽ đổi hướng rẽ. App có thể mirror preview riêng,
bbox và geometry luôn trên ảnh dữ liệu chưa mirror.

| Bước | Công thức / quy tắc |
| --- | --- |
| Đổi màu | RGB, bỏ alpha theo quy ước manifest; không gửi BGR của OpenCV vào app |
| Letterbox | Input vuông S=640 hoặc 416; `r=min(S/W,S/H)`, `new_w=round(W×r)`, `new_h=round(H×r)` |
| Pad nguyên | `left=floor((S-new_w)/2)`, `top=floor((S-new_h)/2)`; phần dư sang right/bottom |
| Resize | Bilinear; cố định cùng thuật toán/rounding trong parity cases; không auto pad theo stride |
| Float | Pixel /255 theo RGB, không trừ mean/chia std khác nếu manifest không khai |
| Int8/uint8 | Từ giá trị float chuẩn hóa: `q=round(real/scale)+zero_point`, chặn theo dtype tensor |
| Metadata | Lưu r, kích thước resize, pad từng ảnh; không tái đoán pad từ bbox sau khi chạy |

Thiết kế letterbox theo quy tắc đã chọn; cần kiểm tương đương với
[LetterBox của Ultralytics](https://docs.ultralytics.com/reference/data/augment/#ultralytics.data.augment.LetterBox),
đặc biệt padding/làm tròn. Nếu exporter hoặc wrapper có tiền xử lý khác, gói phải
khai rõ và tăng model_version; không áp xử lý hai lần.

## Tensor đầu ra và giải mã

Với YOLOv8 detect thông thường không nhúng NMS: tensor có 4 kênh bbox và 12 score
lớp; không có kênh objectness riêng để nhân. Dạng tham chiếu `[1,16,N]`, N=8400
ở 640, N=3549 ở 416 (ba mức stride 8/16/32). Kênh bbox là tâm x/y, rộng/cao.
Nhánh TFLite của [Detect v8.3.0](https://raw.githubusercontent.com/ultralytics/ultralytics/v8.3.0/ultralytics/nn/modules/head.py)
có chuẩn hóa bbox theo kích thước input. Đây là **tham chiếu có phiên bản**, không
khẳng định model chưa xuất của nhóm đã có đúng tensor đó.

App nhận đúng một kiểu layout/đơn vị đã khai trong manifest. Nếu tensor thực là
`[1,N,16]` thì adapter được kiểm parity chuyển trục; không đoán trục bằng ảnh đang chạy.
`box_units=normalized_xywh` → nhân x/w với S, y/h với S trước khi bỏ letterbox;
`input_pixels_xywh` → không nhân S lần nữa. Nếu graph đã nhúng NMS hoặc khác số kênh,
gói không thuộc hợp đồng hiện tại; cần bàn giao schema phiên bản khác trước khi load.

```text
ĐỌC tensor details, kiểm shape/dtype/scale/zero_point khớp manifest
GIẢI lượng tử nếu cần: real=(q-zero_point)×scale
CHUYỂN layout về [N,16], đổi bbox sang pixel input theo box_units
VỚI mỗi anchor: lấy class_id có score lớn nhất; loại dưới confidence_min
ĐỔI tâm/rộng/cao sang x1,y1,x2,y2
NMS theo lớp với model.nms_iou, không triệt hai loại khác nhau chung cột
BỎ pad/resize về ảnh W,H, clip bbox trong ảnh, loại hộp rỗng
XUẤT class_id→sign_type_code theo labels, bbox_px, detector_confidence, model_version
```

NMS client chạy trên Android và batch Python với cùng ngưỡng từ
`model.confidence_min=0,25`, `model.nms_iou=0,45` (**dự kiến**).
Chọn score lớn nhất/class cho mỗi anchor để tránh tự nhân một tấm thành nhiều loại;
các bbox hai loại khác nhau còn lại không tự gộp địa lý thành một biển.
**Cần nhóm quyết:** dùng NMS theo lớp ở client, khóa ngưỡng trên tập phát triển;
lý do là dễ kiểm parity và không phụ thuộc tùy chọn exporter nhúng NMS.

Ví dụ W=1280,H=720,S=640: r=0,5, ảnh resize 640×360, left=0,top=140.
Output normalized `(cx=0,5,cy=0,5,w=0,2,h=0,1)` → hộp input
`[256,288,384,352]`; bỏ pad/chia r → bbox ảnh `[512,296,768,424]`.
Gửi `image_width_px=1280,image_height_px=720`; không gửi bbox input 640 kèm ảnh gốc.
Với crop/tile, cộng offset tile sau phép đổi này, rồi NMS trên toàn ảnh.

## Điều kiện bàn giao và lỗi

| Kiểm tra | Kết quả cần có |
| --- | --- |
| Danh mục | 12 labels đúng byte/thứ tự; API sign-types cùng classes_version |
| Đọc tensor | Shape/layout/dtype/quantization thật được ghi, không chỉ log tên `.tflite` |
| Parity | Cùng ảnh trên Python wrapper và app: lớp, bbox, confidence so trong dung sai do nhóm duyệt |
| Trường hợp ảnh | Ngang/dọc, pad lẻ, xoay EXIF, không có biển, hai loại gần nhau, ảnh pano |
| Hiệu năng | FPS, độ trễ, nhiệt, RAM, pin **cần đo** theo [evaluation.md](../evaluation.md) |
| Không khớp | Từ chối load, hiển thị model không tương thích; không tiếp tục xuất quan sát sai nhãn |

`model_version`, classes_version và hash đủ phân biệt các lần chạy. Bbox geometry
tính trên toàn ảnh sau đổi về pixel; `box_angle` không nhận tọa độ normalized đầu ra.
TFLite không tự trả `sign_facing_deg` hoặc not_seen; các phần đó dùng thuật toán/hợp đồng
lần thấy, không tự suy từ score model.
