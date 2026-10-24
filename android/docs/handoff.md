# Checklist tích hợp và bàn giao App

Đây là checklist cần kiểm, chưa xác nhận các đầu vào đã có.

## Đầu vào backend

- [ ] API base URL truy cập được từ thiết bị, tài khoản user thử cấp riêng.
- [ ] Login, sign-types đủ 12 lớp và classes_version đúng.
- [ ] Snapshot ready và một chuyến có ảnh thật, trace/manifest/hash mỗi năm 2023–2025.
- [ ] Mỗi chuyến có biển alertable; trả snapshot_run_id và alert_params đầy đủ.
- [ ] Detail/history/cursor giữ run; có fixture lỗi 401/403/410/413/422/503.
- [ ] /signs/ahead và tập ứng viên đối chiếu, cùng điểm/xe/run/params.
- [ ] Kịch bản mô phỏng replace, ảnh và scenario_id; nhãn mô phỏng ở giao diện.

## Đầu vào AI

- [ ] TFLite ngoài Git; model_version và SHA-256.
- [ ] labels.txt đúng 12 lớp/thứ tự, classes_version.
- [ ] Manifest tensor thật: shape/layout/dtype/quantization/box_units.
- [ ] Tiền xử lý, EXIF, letterbox, NMS và ngưỡng đã khóa.
- [ ] Parity cases có hash ảnh, lớp/bbox/confidence và dung sai được duyệt.

## Môi trường và dữ liệu

- [ ] Android Studio/SDK, JDK 17, adb và máy thật truy cập được backend.
- [ ] Key Vietmap, quyền/quota SDK và Map Matching được kiểm; không lưu key trong Git.
- [ ] Đầu ra đường được xác minh; thiếu road_ref ghi unverified.
- [ ] Attribution ảnh/map, quyền cache, corpus/trace thật đủ dùng.
- [ ] Không dùng 25 điểm illustration để gọi ảnh hoặc tuyên bố đã đo dữ liệu thật.

## Khi bàn giao

- [ ] APK và hướng dẫn build/cấu hình/chạy; model và ảnh qua kho ngoài Git.
- [ ] Unit test, build, lint; ghi instrumentation/thiết bị nào đã kiểm.
- [ ] Parity ahead và model, demo mỗi năm, xử lý snapshot đổi giữa phiên.
- [ ] Log cảnh báo và device-runs.csv có version/hash/cấu hình; không bí mật.
- [ ] Ít nhất 3 lượt đo thật, ghi protocol, số đo và giới hạn.
- [ ] Liệt kê phần thiếu đầu vào/chưa đo; không coi fixture là kết quả thật.
