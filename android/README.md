# android — App LiveSign

Bản đồ Vietmap, phát lại chuỗi ảnh + vết GPS của Mapillary như một chuyến đi, nhận diện biển bằng TFLite trên máy, gửi lần thấy lên server, nhắc biển phía trước.

## Việc thuộc phần này

| Mã | Việc | Tuần |
| --- | --- | --- |
| M1 | Xin API key Vietmap; app khung hiển thị bản đồ; chạy TFLite mẫu trên máy thật | 1 |
| M2 | Phát lại chuỗi ảnh + vết GPS, nhận diện TFLite, gửi lần thấy lên server | 2–4 |
| M3 | Map Matching Vietmap; nhắc biển theo loại xe, khoảng cách theo vận tốc, nhãn "chưa chắc chắn" | 5–6 |
| M4 | Đo FPS, độ trễ, pin; quay video demo | 7 |

## Khởi tạo project

Project chưa được tạo. Người phụ trách tạo bằng Android Studio (Empty Activity, Kotlin, minSdk 26) với thư mục gốc là `android/` này.

API key Vietmap để trong `local.properties` (đã nằm trong `.gitignore`), đọc qua `BuildConfig`. File model `.tflite` không commit; ghi đường dẫn Drive vào đây.
