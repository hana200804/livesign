# Tài liệu LiveSign

Repo là nguồn ưu tiên; tài liệu thiết kế không phải bằng chứng đã triển khai/đo.
Khác biệt với PDF cũ ở gốc được ghi trong decisions.md. Những chỗ **Cần nhóm quyết**
giữ ở trạng thái đề xuất; ngưỡng đã chốt không được tự đổi. Các tài liệu thiết kế
mới không có phân công tên người, mã việc hoặc kế hoạch theo kỳ.

## Phân tích, thiết kế và thực nghiệm

- [requirements.md](requirements.md) — tác nhân, sáu use case, yêu cầu chức năng và phi chức năng.
- [architecture.md](architecture.md) — thành phần, luồng tuần tự, vòng đời, triển khai và cấu hình môi trường.
- [algorithms.md](algorithms.md) — định vị, gộp, nhìn được, điểm/vòng đời, chọn biển phía trước; pseudocode và ví dụ tính tay.
- [ai-pipeline.md](ai-pipeline.md) — manifest dataset, lọc trùng/split, train, batch 2048px/tiling và bài thử B3.
- [ui.md](ui.md) — wireframe ASCII app/web, lỗi/mất mạng/409, demo và ánh xạ endpoint.
- [evaluation.md](evaluation.md) — B0–B3 theo năm, precision/coverage/tải admin, mô phỏng, model và đo thiết bị thật.
- [testing.md](testing.md) — test logic bắt buộc, tích hợp MySQL, kiểm hợp đồng và đặc tả seed demo.

## Hợp đồng dùng chung

- [contracts/classes.md](contracts/classes.md) — danh mục 12 mã QCVN, loại xe và xác minh nhãn nguồn.
- [contracts/observation.md](contracts/observation.md) — cột/đơn vị/outcome/geometry và bổ sung provenance 1.1.
- [contracts/api.md](contracts/api.md) — endpoint 1.0, bổ sung phát lại/chi tiết/history/admin 1.1 và JSON minh họa.
- [contracts/db-schema.md](contracts/db-schema.md) — tám bảng MySQL 8, POINT/SPATIAL/FK và metadata trip bổ sung 1.1.
- [contracts/model.md](contracts/model.md) — model/labels/manifest, tensor TFLite, letterbox/NMS và parity app.
- [contracts/trip-trace.md](contracts/trip-trace.md) — JSONL chuyến đi, thời gian/tốc độ/hướng, segment và phát lại.
- [contracts/ground-truth.md](contracts/ground-truth.md) — CSV tấm biển theo năm, gán chéo và matching một–một.
- [contracts/observation-sample.jsonl](contracts/observation-sample.jsonl) — 10 dòng PoC thật có raw nguồn, outcome unknown; không phải ground truth.
- [trip-trace-sample.jsonl](../data-small/trip-trace-sample.jsonl) — 25 điểm minh họa để kiểm đọc trace, không có ảnh/chuyến thật.
- [ground-truth-sample.csv](../data-small/ground-truth-sample.csv) — 4 tấm minh họa để kiểm CSV/năm/nhãn chéo, không phải bộ mốc đã duyệt.
- [demo-corridor.geojson](../data-small/demo-corridor.geojson) — đường bám OSM quanh vùng rank Hoàn Kiếm; vẫn là ứng viên chưa chốt.

## Quyết định, môi trường và nguồn cấu hình

- [decisions.md](decisions.md) — quyết định repo, bảng Còn mở, ba câu hỏi GVHD và đối chiếu PDF.
- [setup.md](setup.md) — checklist môi trường/tài khoản theo phần và lệnh kiểm tra hiện có.
- [preparation-audit.md](preparation-audit.md) — số liệu thật từ repo, giới hạn PoC và kiểm tra giai đoạn chuẩn bị.
- [params.yaml](../config/params.yaml) — tên/giá trị/trạng thái tham số, params_version; tham số mới 1.1 đều dự kiến.
- [classes.yaml](../ai/configs/classes.yaml) — nguồn duy nhất mã lớp/thứ tự/xe áp dụng.
- [dataset.yaml](../ai/configs/dataset.yaml) — bản chiếu danh mục cho Ultralytics; chưa training_ready.
- [geometry.py](../ai/livesign_ai/geometry.py) — quy ước góc được hợp đồng sử dụng, không sửa trong lần thiết kế.
- [docker-compose.yml](../docker-compose.yml) — MySQL 8 đọc biến môi trường, chưa chứng minh DDL đã chạy.
- [.env.example](../.env.example) — tên biến hiện có; giá trị bí mật chỉ điền trên máy, không commit.

## Dùng cho quyển

Chương Phân tích thiết kế dùng requirements, architecture, algorithms, ui và các
hợp đồng. Chương Thực nghiệm dùng ai-pipeline, evaluation, testing và số **đã đo**
có manifest; bảng “cần đo” không được chuyển thành kết quả. Sơ đồ Mermaid là nguồn
để render hình, ví dụ tính tay là fixture minh họa, không là số thực nghiệm.

Đề cương v3.3 tham chiếu tại [Claude Doc](https://claude.ai/code/artifact/5fb25879-ca3e-4661-bb5e-a228fbe17c18).
Kết quả lớn/ảnh/model và artifact thực nghiệm để ngoài git; khi có báo cáo mới,
ghi ngày, nguồn/hash, cấu hình và giới hạn. Phương án đối chứng gọi là **cách làm
phổ biến hiện nay**: vị trí GPS ảnh + gộp cùng loại 20 m, không so với đồ án cụ thể.

## Kiểm tra bộ tài liệu thiết kế

Đã parse 10 sơ đồ Mermaid và 21 khối JSON, kiểm liên kết nội bộ, mẫu trace/CSV,
16 endpoint UI với API, ví dụ điểm bốn năm/FOV/letterbox, tham số cũ không đổi và
ba hợp đồng 1.0 chỉ được thêm cuối file. 23 test AI hiện có trong repo chạy đạt;
đó không phải các test nghiệp vụ mới trong testing.md đã được triển khai.
Chưa chạy DDL MySQL, train/export model hoặc app trên thiết bị thật trong lần này.
