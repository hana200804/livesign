# Danh mục lớp dùng chung

**Nguồn duy nhất:** [ai/configs/classes.yaml](../../ai/configs/classes.yaml).
AI dùng `class_id` 0–11 trong annotation/model; Backend và Android trao đổi
`sign_type_code` theo mã QCVN, kèm `classes_version = qcvn12-2026-10-19`.
Không gửi số thứ tự lớp mà thiếu phiên bản. `dataset.yaml`, bảng dưới và dữ liệu
seed/export cho app là bản chiếu của nguồn này; sửa nguồn rồi đồng bộ cùng một commit.

Nhóm đã duyệt bộ 12 lớp ngày 19/10/2026, sau đối chiếu mã/tên và phạm vi xe trong
[QCVN 41:2024/BGTVT, phụ lục B/C/E](https://datafiles.chinhphu.vn/cpp/files/vbpq/2024/11/51-bgtvt-kem.pdf),
ban hành theo [Thông tư 51/2024/TT-BGTVT](https://vanban.chinhphu.vn/?docid=211908&pageid=27160).
**Chưa xác nhận nguồn nhãn train.** Mọi lớp đều cần kiểm tra lại khi tải Zalo 2020 / Roboflow.

| class_id | sign_type_code | Tên | Xe trong phạm vi demo | Mục QCVN | Xác nhận Zalo / Roboflow |
| --- | --- | --- | --- | --- | --- |
| 0 | P.102 | Cấm đi ngược chiều | car, truck, motorcycle | B.2 | Cần / Cần |
| 1 | P.103a | Cấm xe ô tô | car, truck | B.3 | Cần / Cần |
| 2 | P.123a | Cấm rẽ trái | car, truck, motorcycle | B.23 | Cần / Cần |
| 3 | P.123b | Cấm rẽ phải | car, truck, motorcycle | B.23 | Cần / Cần |
| 4 | P.124a | Cấm quay đầu xe | car, truck, motorcycle | B.24 | Cần / Cần |
| 5 | P.128 | Cấm sử dụng còi | car, truck, motorcycle | B.28 | Cần / Cần |
| 6 | P.130 | Cấm dừng xe và đỗ xe | car, truck, motorcycle | B.30 | Cần / Cần |
| 7 | P.131a | Cấm đỗ xe | car, truck, motorcycle | B.31 | Cần / Cần |
| 8 | W.225 | Trẻ em | car, truck, motorcycle | C.25 | Cần / Cần |
| 9 | W.227 | Công trường | car, truck, motorcycle | C.27 | Cần / Cần |
| 10 | I.423a | Vị trí người đi bộ sang ngang | car, truck, motorcycle | E.23a | Cần / Cần |
| 11 | I.407a | Đường một chiều | car, truck, motorcycle | E.7 | Cần / Cần |

`car` = ô tô con; `truck` = xe tải; `motorcycle` = xe máy **hai bánh** trong demo.
Đây là bộ lọc nhắc biển cho ba hồ sơ xe thông thường. Ngoại lệ xe ưu tiên, biển phụ,
giờ/ngày hiệu lực, làn và phạm vi hiệu lực nằm ngoài mô hình demo. Riêng P.103a
không cấm xe máy hai bánh; không mở rộng hồ sơ này sang xe máy ba bánh.

## Chọn dữ liệu và xác minh nhãn

- Ưu tiên này dựa trên nhãn gợi ý thật trong `hoankiem-trip-check.csv` và PoC:
  cấm đỗ/dừng, một chiều, cấm rẽ/ngược chiều, người đi bộ; W.227 có trong PoC.
  Số nhãn Mapillary không phải số mẫu train hay số tấm biển được người xác minh.
- `mapillary_label_candidates` chỉ giúp tìm ảnh. Không phải ánh xạ được phép dùng
  tự động, nhãn train hoặc ground truth. I.423a/I.423b phải phân biệt theo ảnh;
  không gộp cả hai vào I.423a. Phân biệt P.124a/P.124b và P.131a/b/c tương tự.
- Zalo 2020 có nhóm rộng; không ánh xạ một nhóm “cấm rẽ” thành một chiều rẽ cụ thể.
  Ảnh được dùng khi có thể gán lại đúng tấm biển; nếu chưa kiểm tra thì giữ ngoài tập.
- Với mỗi nguồn/phiên bản Roboflow hoặc Kaggle, Hân ghi URL, version, giấy phép,
  số ảnh thực, nhãn gốc, mã đích và ví dụ đã xem vào manifest ngoài git/Drive.
  `sources.*.mapping` để rỗng đến khi hoàn tất việc này.
- Lọc gần trùng giữa nguồn bằng perceptual hash **trước khi** chia theo nguồn/sequence;
  nhóm ảnh gần trùng phải ở cùng split. Chừa tập test Hà Nội độc lập, không tune trên test.
- Bộ đã duyệt chưa có lớp tốc độ hoặc cấm riêng xe tải. P.103a vẫn phân biệt ô tô với
  xe máy, nhưng chưa chứng minh đầy đủ cảnh báo riêng cho xe tải hay quá tốc độ.
  Nếu dataset không đủ cho một lớp, báo nhóm duyệt thay lớp; không tự đổi danh mục.

## Điều kiện đóng danh mục để train

Hân xác nhận đủ mẫu/annotation và giấy phép cho từng lớp; Nam/Anh kiểm tra cùng thứ tự
và tên mã. Sau đó mới bật `training_ready`, gắn version manifest và đưa dataset vào
`data/processed/yolo/`. Nếu đổi bộ lớp, tăng `classes_version`, xuất lại model và nhãn app;
Backend từ chối lô khai phiên bản lớp khác bản đã hỗ trợ.
