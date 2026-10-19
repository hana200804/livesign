# Hợp đồng bộ mốc tấm biển theo năm

Thiết kế 1.1. Bộ mốc được tạo bằng xem **toàn tuyến** từ ảnh công khai, không chỉ
những feature Mapillary đã phát hiện. Một tấm vật lý là một gt_id; nhiều tấm trên
cùng cột là nhiều dòng. Không dùng bộ mốc để train/tune hoặc làm vị trí đầu vào
cho thuật toán đang được chấm.

## CSV UTF-8

Dùng CSV chuẩn dấu phẩy, quote kép cho ô JSON/ghi chú có dấu phẩy; thiếu giá trị
là ô rỗng, không ghi NaN hoặc 0 giả. ID và mã QCVN là string.

| Cột | Kiểu / ý nghĩa |
| --- | --- |
| `gt_id` | string, khóa tấm vật lý bền, không dùng feature ID làm khóa chuẩn |
| `sign_type_code` | Một trong 12 mã của classes.yaml; tấm đổi loại được lập gt_id mới và liên hệ trong notes |
| `lon`, `lat` | number độ, WGS84; cùng vị trí mốc của tấm, ghi phương pháp/độ bất định |
| `sign_facing_deg` | number [0,360) hoặc rỗng nếu không đủ cơ sở; hướng từ mặt trước ra người đọc |
| `first_year_seen`, `last_year_seen` | integer, năm đầu/cuối có ảnh xác nhận; **không** tự chứng minh tồn tại mọi năm ở giữa |
| `year_evidence` | JSON object `{năm:[image_id...]}` cho năm tấm có ảnh xác nhận |
| `absent_evidence` | JSON object `{năm:[image_id...]}` cho năm đã xem đủ để xác nhận tấm cũ không còn, không phải chỉ không có detection |
| `uncertain_years` | JSON array năm đã xem nhưng chưa đủ kết luận tồn tại/vắng |
| `labeler_id` | ID giả danh của người gán; không phải phân công công việc hoặc creator_id của ảnh |
| `double_labeled` | `true/false`; được người thứ hai gán độc lập hay chưa |
| `second_labeler_id` | ID khác người đầu khi double_labeled=true, ngược lại rỗng |
| `adjudication` | `not_needed/agreed/resolved/pending`; pending không được chấm như mốc đã chắc |
| `position_method` | `independent_image_review` hoặc `illustration`; mô tả chi tiết trong notes/manifest |
| `position_uncertainty_m` | number mét ≥0 hoặc rỗng khi chưa biết; không mặc định đạt 2–3 m |
| `data_kind` | `public_annotation/illustration`; illustration tuyệt đối không trộn kết quả thật |
| `notes` | Ghi chú cột chung, tấm cũ/mới, hướng, ảnh khó, lý do chưa chấm |

Manifest bộ mốc: gt_version, corridor/geometry hash, classes_version, years đã duyệt,
phương pháp tọa độ/hướng, danh sách ảnh/hash và giấy phép, quy tắc năm,
thỏa thuận chấm, tỷ lệ gán chéo/đồng ý, ngày khóa. Các chỉnh sửa sau khi biết kết quả
phải có lý do và version mới; không sửa mốc riêng cho một phương án thắng.

## Quy trình tạo và kiểm chéo

```text
ĐÓNG phạm vi tuyến ứng viên sau khi nhóm chốt, dựng danh sách ảnh theo năm 2023–2025
DUYỆT liên tục toàn tuyến/các hướng có ảnh, ghi mọi tấm kể cả Mapillary không gợi ý
GÁN mã, vị trí gần đúng, hướng mặt, bằng chứng từng năm; tách biển chung cột
CHỌN ngẫu nhiên ít nhất ceil(20%×số tấm) để gán độc lập lần hai
SO kết quả trước khi trao đổi: loại, tấm, tồn tại theo năm, vị trí/hướng
GIẢI bất đồng bằng xem thêm ảnh công khai và người phân xử; giữ bản gán ban đầu
GIỮ uncertain/pending khi ảnh không đủ; khóa manifest trước khi chạy chấm
```

Không biết thời điểm chính xác gỡ/thay giữa hai ảnh: chỉ gán năm đủ chứng cứ,
không nội suy first/last thành hiện trạng chắc chắn ngày 31/12. Một năm không có
ảnh không phải absent. Người gán chỉ xem ảnh trong/cận năm theo quy tắc đã đóng;
ảnh tương lai có thể hỗ trợ tìm ca nhưng phải công bố khi dùng, không dùng nó để
khẳng định có ảnh xác nhận năm trước.

**Cần nhóm quyết:** đề xuất chỉ chấm `gt_exists(T)` khi `year_evidence[T]` đủ rõ;
`absent_evidence[T]` dùng chấm false positive tấm cũ, năm uncertain/pending đưa vào
tập chưa chấm. Lý do: first/last và mất ảnh không chứng minh sự tồn tại giữa hai mốc.
Báo số tấm/năm không chấm và lý do để tránh làm đẹp precision bằng loại ca khó.

20% tấm là gán độc lập trước hòa giải, không phải 20% dòng ảnh; báo cả số được chọn,
số đạt đồng thuận ban đầu/tổng, số cần hòa giải và số còn pending. ID giả danh đủ
cho audit nội bộ, không đưa tên thật người phụ trách vào tài liệu thiết kế.

## Khớp dự đoán ↔ mốc một–một

Đầu vào: tấm bản đồ **được tính hiển thị trong bậc đánh giá tại T**, mốc tồn tại đã
chắc tại T, cùng vùng/12 lớp, hướng và vị trí đủ chấm. Ngưỡng đề xuất
`ground_truth.match_radius_m=5`, `ground_truth.max_facing_diff_deg=30`.
Bán kính này khác `merge_radius_m=20`, không tự lấy bán kính gộp làm dung sai chấm.

```text
TẠO cạnh giữa dự đoán và mốc khi cùng loại, distance<=5 m và angle_diff<=30°
CHỌN matching có số cặp lớn nhất; trong các cách bằng số cặp, tối thiểu tổng sai số
MỖI dự đoán và mỗi mốc xuất hiện tối đa một lần
DỰ ĐOÁN chưa khớp là FP, mốc tồn tại chưa khớp là FN trong vùng đủ chấm
LƯU mọi cặp/không khớp và nguyên nhân, không chỉ các cặp thành công
```

**Cần nhóm quyết:** khóa X=5 m/Y=30° và matching tối đa số cặp rồi tối thiểu
khoảng cách (hòa thì lệch hướng, rồi khóa ID) trước test; lý do là greedy theo thứ
tự dự đoán có thể bỏ một cặp hợp lệ. Thử nhạy X=3/5/10 m phải báo cả ba, không chọn
mức có kết quả tốt nhất sau khi xem test. Thiếu hướng dự đoán → chưa khớp trong phép
chấm hướng; thiếu hướng mốc → tập chưa đủ chấm, không tự coi góc lệch bằng 0.

Ví dụ minh họa cùng loại/hướng: G1 ở x=0 m, G2 ở x=6 m; P1 ở x=2 m,
P2 ở x=−3 m; X=5 m. P1 khớp được cả hai, P2 chỉ G1. Greedy P1→G1 làm còn
một cặp; matching tối đa chọn P1→G2 (4 m), P2→G1 (3 m) → hai cặp.
Hai prediction sát cùng G1 chỉ một TP; prediction dư là FP/tách nhầm.

Độ phức tạp lập cạnh O(P×G) nếu duyệt hết; chỉ mục không gian giảm ứng viên.
Ghép trọng số bằng Hungarian với dummy/điều kiện tối đa cặp O((P+G)³), phù hợp
đoạn nhỏ; phải phân biệt cạnh cấm với cạnh chi phí cao, không ghép tùy ý khi không đủ cạnh.
Mốc tọa độ không đủ tin cậy không dùng để tuyên bố sai số tuyệt đối; đánh giá
loại/tấm bằng ảnh và báo riêng số đo có thể/không thể thực hiện.

## Mẫu

[ground-truth-sample.csv](../../data-small/ground-truth-sample.csv) là **4 tấm minh họa**
để kiểm đọc CSV/JSON, tấm cũ/mới và nhãn chéo; không có bộ mốc đã duyệt thật trong repo.
ID ảnh `illustration:*` không tải được, position_uncertainty để rỗng.
Mẫu có một tấm cũ chỉ chắc 2023–2024 và vắng 2025, tấm thay mới từ 2025;
không dùng các dòng này để công bố precision/coverage thực tế.
