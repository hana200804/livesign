# android/ — App Kotlin: bản đồ Vietmap, phát lại, nhắc biển

Quy trình nhánh/PR ở [CLAUDE.md gốc](../CLAUDE.md). Thiết kế: [docs/architecture.md](../docs/architecture.md) (mục Phát lại đến cảnh báo), [docs/ui.md](../docs/ui.md).

- **Hợp đồng:**
  - Gọi API theo [api.md](../docs/contracts/api.md).
  - Đọc vết chuyến theo [trip-trace.md](../docs/contracts/trip-trace.md).
  - Chạy model theo [model.md](../docs/contracts/model.md): kích thước input, letterbox, NMS và thứ tự lớp phải khớp gói model.
- **Phát lại không gọi mạng theo từng điểm:** tải biển dọc chuyến một lần, Map Matching cả vết một lần, rồi lọc biển phía trước ngay trên máy. `/signs/ahead` chỉ dùng để test đối chiếu.
- **Bí mật:**
  - API key Vietmap để trong `local.properties` (đã nằm trong `.gitignore`), đọc qua `BuildConfig`. Không ghi key vào code hay `gradle.properties`.
  - File `.tflite` không commit.
- **Test trước khi mở PR:** `./gradlew testDebugUnitTest` trong `android/`. CI tự chạy khi đã có `android/gradlew`. Logic chọn biển hay khoảng cách nhắc phải có unit test, không chỉ thử bằng tay.
- **Chưa có project:** tạo theo [README.md](README.md) trong một nhánh `android/khoi-tao-project` riêng, rồi PR vào `dev`.
