# android — App LiveSign

Bản đồ Vietmap, phát lại chuỗi ảnh + vết GPS của Mapillary, nhận diện biển bằng TFLite trên máy và nhắc biển phía trước. Replay MVP không gửi observation; re-infer/upload thuộc phần Nên có.

- [Kế hoạch, phạm vi và cấu trúc thư mục](PLAN.md).
- [Checklist tích hợp và bàn giao](docs/handoff.md).
- [Quy tắc Android](CLAUDE.md).

Hiện có tài liệu và khung thư mục, chưa có project Gradle hoặc APK. Khung dùng một module `app`, Kotlin + Compose; phần thư mục trống được giữ bằng `.gitkeep`. Các chặng triển khai và tiêu chí kiểm tra nằm trong kế hoạch.

## Khởi tạo project

Project chưa được tạo. Người phụ trách tạo bằng Android Studio (Empty Activity, Kotlin, minSdk 26) với thư mục gốc là `android/` này.

API key Vietmap để trong `local.properties` (đã nằm trong `.gitignore`), đọc qua `BuildConfig`. File model `.tflite` không commit; ghi đường dẫn Drive vào đây.
