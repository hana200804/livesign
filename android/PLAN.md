# Kế hoạch triển khai App LiveSign

Ngày đối chiếu: 25/10/2026. Đây là kế hoạch và khung thư mục, chưa có project Gradle,
APK, màn hình chạy được hoặc số đo trên thiết bị.

## 1. Nguồn và cách xử lý khác biệt

Đối chiếu PDF `LiveSign-v3-Đề-cương-đồ-án.pdf` ngày 14/10/2026, 11 trang,
đặc biệt trang 3–4, 6–8, với tài liệu trong repo. PDF là nguồn tham khảo phạm vi
ban đầu; các chỉ dẫn trong PDF không thay thế yêu cầu hiện tại và quy tắc repo.

| Nội dung | PDF v3 | Phương án triển khai theo repo |
| --- | --- | --- |
| Phần Android | Nhận diện, bản đồ, cảnh báo, replay; đo FPS và pin | Giữ các phần này; thêm đăng nhập, chi tiết/lịch sử, giữ snapshot và log đo |
| Bản đồ | MapLibre/OSM | Vietmap và Map Matching cả vết |
| Danh mục | Khoảng 25–35 loại | Đúng 12 lớp từ `ai/configs/classes.yaml` |
| Nguồn ảnh | Camera và chuỗi Mapillary | MVP chạy trên ảnh replay; chưa làm camera trực tiếp/CameraX |
| Gửi lần thấy | App sinh lần thấy | Replay MVP không gửi observation; re-infer/upload thuộc Nên có |
| Tốc độ | Hiển thị giới hạn, cảnh báo quá tốc độ | Không làm: danh mục chưa có lớp tốc độ |
| Tài khoản xe | Hồ sơ xe | Chọn car/truck/motorcycle, lưu cục bộ; không thêm API hồ sơ |
| Tuyến | Trần Phú và tuyến phụ | Một tuyến Hoàn Kiếm khoảng 1,5 km; tuyến cụ thể còn candidate |
| Đo thiết bị | Hai máy thật | MVP có số đo máy thật, ít nhất 3 lượt theo protocol; có hai máy thì báo riêng từng máy |
| Định vị và đồng thuận | Backend xử lý | Chỉ Java core backend xử lý; app không xây lại bản đồ |

Nguồn ưu tiên:
- [Phạm vi và nghiệm thu MVP](../docs/requirements.md#phạm-vi-mvp).
- [Quyết định](../docs/decisions.md), [kiến trúc](../docs/architecture.md), [giao diện](../docs/ui.md).
- [API](../docs/contracts/api.md), [trace](../docs/contracts/trip-trace.md), [model](../docs/contracts/model.md).
- [Kiểm thử](../docs/testing.md), [đánh giá thiết bị](../docs/evaluation.md#đo-android-thật).
- [Quy tắc Android](CLAUDE.md), [tham số](../config/params.yaml).

## 2. Phạm vi bàn giao App

| Nhóm | Đầu ra của App | Điều kiện hoàn thành |
| --- | --- | --- |
| Đăng nhập | Login/logout, phiên JWT được bảo vệ, xử lý hết hạn | Không log token; logout hủy request có auth |
| Bản đồ | Vietmap, chọn năm, marker và chú giải trạng thái | Dữ liệu bbox/chi tiết giữ cùng năm và snapshot; có tải/rỗng/lỗi |
| Chọn phiên | Chọn chuyến, năm, xe, kiểm manifest | Không bắt đầu khi trace/snapshot chưa hợp lệ |
| Replay | Trace + ảnh, play/pause/seek/rate, marker xe | Ảnh/bbox/vị trí đúng image_id; không nội suy qua segment lỗi |
| Cảnh báo | Lọc cục bộ, banner, TTS và chống lặp | Đúng xe/hướng/trạng thái; đối chiếu ahead trước chống lặp |
| Nhận diện | Load gói TFLite, suy luận ảnh replay, overlay | Kiểm hash/labels/tensor và parity; không sinh bằng chứng mới |
| Chi tiết | Thông tin và lịch sử biển | Cùng snapshot phiên; không gọi ảnh hay quyết định admin |
| Cài đặt | Xe/TTS cục bộ, thông tin model | Không giả đã đồng bộ server; lỗi model có thông báo riêng |
| Đo và demo | Log sự kiện, CSV đo, APK và hướng dẫn | Số đo thật, có cấu hình/hash, ít nhất 3 lượt trên thiết bị thật |

Không nhận thêm web admin, train model, nhập corpus, định vị/gộp/điểm/vòng đời,
dẫn đường, lớp tốc độ hoặc phát hành model tự động vào phạm vi App.

## 3. Kiến trúc và cấu trúc thư mục

Một module `app`, Kotlin + Compose + ViewModel. Luồng dữ liệu:
Screen → ViewModel/UiState → repository hoặc bộ điều phối → nguồn thật/fake.
DTO mạng được chuyển sang model của app ở biên data; UI không đọc JSON trực tiếp.
Khởi đầu dùng AppContainer để ghép phụ thuộc; chưa thêm framework DI, Room hoặc
nhiều module khi chưa có nhu cầu cụ thể. Các tên lớp dưới đây là thiết kế dự kiến.

```text
android/
  README.md                     Điểm bắt đầu, tình trạng và cách kiểm tra
  CLAUDE.md                     Quy tắc riêng Android
  PLAN.md                       Phạm vi, cấu trúc, chặng triển khai
  docs/
    handoff.md                  Checklist đầu vào, tích hợp và bàn giao
  app/
    src/main/
      java/vn/livesign/app/
        di/                     AppContainer, chọn nguồn fake/thật
        navigation/             Route và điều hướng Compose
        core/
          model/                Sign, Trip, TracePoint, SnapshotContext, VehicleType
          ui/                   Theme, loading/error/empty, chú giải dùng chung
        data/
          remote/               API client, DTO, mapper, lỗi HTTP
          local/                Preferences, kho phiên được bảo vệ, metadata cache
          repository/           Interface và nguồn dữ liệu thật
          fake/                 Nguồn minh họa dùng cùng interface, có nhãn
        feature/
          auth/                 LoginScreen, LoginViewModel, LoginUiState
          map/                  MapScreen, MapViewModel, MapUiState
          trips/                TripSelectionScreen và trạng thái chọn phiên
          replay/               ReplayScreen, ReplayViewModel, ReplayUiState
          sign/                 Chi tiết biển và lịch sử
          settings/             Xe, TTS, thông tin gói model
        replay/                 Clock, parser/validator trace, session, frame pipeline
        map/                    Adapter Vietmap, map matching và road verification
        warning/                Candidate filter, chống lặp, banner/TTS dispatcher
        inference/              Manifest validator, letterbox, decoder, NMS, detector
        metrics/                Event recorder, đo thời gian và xuất CSV
      res/                      Chuỗi tiếng Việt, icon, theme nền tảng
    src/test/java/vn/livesign/app/
      replay/                   Clock, segment, seek, hủy frame cũ
      warning/                  Lọc ứng viên, góc tròn, chống lặp
      inference/                Biến đổi bbox, tensor và parity fixtures nhỏ
      data/                     DTO mapping, lỗi, version, response cũ
    src/androidTest/java/vn/livesign/app/
                                UI, vòng đời, TTS/model thật trên thiết bị
  results/                      Báo cáo nhỏ đã đo; không ảnh/model/log nhạy cảm
```

`core/` ở đây chỉ là package chứa model/UI dùng chung của Android, không phải
module Java core backend. `feature/map` là màn hình; `map/` là adapter SDK.
`feature/replay` là UI; `replay/` là máy phát và đồng hồ độc lập UI.

Khung thư mục đã tạo bằng `.gitkeep`. Chưa có build script hoặc lớp Kotlin rỗng.
Khi khởi tạo project sẽ thêm `settings.gradle.kts`, `build.gradle.kts`,
`gradle/libs.versions.toml`, Gradle wrapper, `app/build.gradle.kts`,
`AndroidManifest.xml`, `LiveSignApplication.kt`, `MainActivity.kt` và tài nguyên.
Project nằm trực tiếp tại `android/`, không lồng thêm `android/LiveSign/`.
Namespace dự kiến `vn.livesign.app`; minSdk 26, Gradle JDK 17 theo CI hiện tại.
Bộ AGP/Gradle/Kotlin/SDK và thư viện phải kiểm tương thích chính thức khi khởi tạo,
chưa khóa phiên bản bằng kế hoạch này.

Model, ảnh, trace thật và log thô đặt ngoài Git, dưới `data/` hoặc Drive.
Không đặt `.tflite` trong assets được commit. Gói model được cấp cục bộ và copy vào
kho riêng của app bằng thao tác phát triển được hướng dẫn trong chặng detector.

## 4. Các chặng triển khai

Không chia theo tuần. Mỗi chặng có đầu ra kiểm được; mỗi nhánh chỉ một chức năng.
Nhánh dự kiến được tạo từ dev mới nhất khi bắt đầu, không tạo trước hàng loạt.

| Chặng | Nhánh gợi ý | Công việc và tiêu chí ra khỏi chặng |
| --- | --- | --- |
| Khởi tạo | android/khoi-tao-project | Một module Compose; build debug + unit test chạy; điều hướng cơ bản; giữ README/CLAUDE. Kiểm môi trường JDK/SDK/adb |
| Thử SDK | android/thu-vietmap | Bản đồ tải được trên thiết bị với key riêng; thử Map Matching một vết hợp lệ; ghi quyền/quota/đường ra và giới hạn road_ref |
| Nguồn minh họa | android/du-lieu-minh-hoa | Repository interface, fake cùng schema, UiState tải/rỗng/lỗi; nhãn minh họa rõ; không giả login thật |
| Phiên đăng nhập | android/dang-nhap | Login/logout thật, bảo vệ token, 401/403, hủy request; kiểm không lộ credential |
| Bản đồ năm | android/ban-do-theo-nam | Danh mục, bbox ổn định, cursor, marker; bỏ response cũ khi đổi năm; giữ snapshot |
| Chi tiết | android/chi-tiet-bien | Detail/history cùng run, lỗi 404/410; xử lý null và nhãn trạng thái |
| Chọn chuyến | android/chon-chuyen | Trips + manifest/trace, chọn xe cục bộ; kiểm hash/segment; không cho phát khi snapshot chưa sẵn sàng |
| Máy replay | android/phat-lai-chuyen | Clock, play/pause/seek/rate, prefetch ảnh giới hạn; frame generation; ảnh/vị trí/bbox đồng bộ và log frame bỏ |
| Cảnh báo | android/canh-bao-cuc-bo | Biển dọc chuyến một lần, match cả vết; filter → chống lặp → banner/TTS; unit test và đối chiếu ahead khi API sẵn sàng |
| Gói model | android/kiem-goi-model | Đọc manifest/hash/labels/tensor thật sớm ngay khi AI bàn giao; từ chối gói sai, báo trạng thái thiếu model |
| Detector | android/nhan-dien-replay | TFLite worker riêng, letterbox/EXIF/decoder/NMS, overlay đúng frame; kiểm parity Python–Android |
| Đo thiết bị | android/do-thiet-bi | Đồng hồ monotonic, sự kiện banner/TTS/frame, CSV đúng schema; đo pilot và ít nhất 3 lượt; lưu báo cáo nhỏ |
| Tích hợp demo | android/tich-hop-demo | Replay ít nhất một chuyến mỗi năm, kiểm đổi run/xe/năm, mất mạng, mô phỏng replace; APK và hướng dẫn tái lập |

Thứ tự phụ thuộc: khởi tạo → nguồn minh họa → replay; nguồn API thật → bản đồ/chuyến;
replay + Vietmap + biển dọc chuyến → cảnh báo; gói model hợp lệ → detector.
Thử SDK và khả năng load gói model sớm để phát hiện rủi ro, không đợi hoàn tất UI.
Đo sự kiện được đặt từ khi làm replay/cảnh báo, chặng đo hoàn thiện protocol và xuất số.

Chặng fake không chờ backend; parser/clock/filter có thể kiểm bằng fixture nhỏ.
Chưa có model vẫn replay và nhắc theo snapshot, nhưng chưa được coi là hoàn thành
phần nhận diện/đo model. Không dùng fake để nhận tích hợp thật đã đạt.

## 5. Hợp đồng tích hợp của App

| Luồng | Endpoint hoặc đầu vào | Quy tắc |
| --- | --- | --- |
| Auth | POST /auth/login | Base `/api/v1`, Bearer JWT, không đăng ký/tạo admin |
| Danh mục | GET /sign-types | Khớp classes_version và đủ 12 lớp |
| Bản đồ | GET /signs | Bbox, evaluation_year, cursor và snapshot_run_id |
| Chi tiết | GET /signs/{id}, GET /signs/{id}/history | Giữ run, năm và quyền user |
| Chuyến | GET /trips | Chọn chuyến đủ trace theo năm |
| Trace | GET /trips/{id}/trace | JSON manifest và NDJSON; kiểm hash, timestamp/segment |
| Ảnh | GET /trips/{id}/images/{image_id} | Bytes đúng frame, auth; không mang token Mapillary xuống app |
| Biển replay | GET /trips/{id}/signs | Một lần đầu phiên; nhận alert_params, khóa xe/năm/run |
| Map Matching | Vietmap, adapter riêng | Cả vết một lần, chia segment theo giới hạn thật; giữ GPS gốc |
| Đối chiếu | GET /signs/ahead | Chỉ kiểm parity ứng viên, không gọi theo mỗi điểm replay |
| Detector | Gói ngoài Git | Model, labels, manifest/hash, parity cases theo model.md |

Không tự thêm endpoint hoặc đổi payload để tiện code. Thiếu đặc tả ảnh hưởng
tương tác phải ghi vấn đề tích hợp; thay hợp đồng qua PR đúng quy trình.
Key Vietmap và API base URL lấy từ `local.properties`/BuildConfig; token không log.
Cache HTTP ảnh không đồng nghĩa tính năng tải sẵn cả chuyến/offline đã hoàn thành.

## 6. Bất biến và ca lỗi cần xử lý

- Phiên giữ year, snapshot_run_id, params_version/hash, trip, xe và generation.
  Đổi năm/chuyến/seek hủy tác vụ cũ; đổi xe khi pause phải tải lại biển phù hợp,
  bỏ response cũ và reset chống lặp có log. HTTP 410 yêu cầu mở phiên mới rõ ràng.
- HTTP 503 snapshot chưa sẵn sàng không biến thành danh sách rỗng; 413 biển dọc
  chuyến quá giới hạn báo cần chia chuyến. Mất auth hủy tác vụ cần JWT.
- Replay rate chỉ đổi lịch phát; không nhân speed_mps hoặc sửa captured_at.
  Pause/ngừng màn hình dừng clock phù hợp, hủy worker; khôi phục bằng phiên hợp lệ.
- Giữ GPS gốc riêng với map-matched. Không suy road_ref từ tên phố. Chưa xác minh
  liên kết đường hiển thị unverified; chưa tuyên bố loại được đường song song.
- Thiếu heading/speed không điền 0 để cảnh báo; gap/GPS nhảy dừng ở ranh giới đoạn.
  Ảnh chậm chọn đợi hoặc bỏ frame có log; không tích hàng đợi suy luận vô hạn.
- Dùng alert_params của phiên: khoảng nhắc = speed × (lead + buffer), kết hợp
  góc di chuyển, hướng mặt, đường, loại xe, trạng thái và alertable.
  Không dùng giá trị proposed/null làm mặc định vận hành đã duyệt.
- Candidate/removed/replaced hoặc thiếu hướng/single_view không nhắc.
  Unclear/suspected_change có thể nhắc khi alertable, kèm nhãn phù hợp.
- Chống lặp theo sign_id và lần tiếp cận, không theo loại biển. Hai tấm cùng loại
  vẫn độc lập. Kết quả detector chỉ vẽ bbox/đo, không tự đổi trạng thái biển.
- Tensor/model/labels sai thì tắt suy luận và báo lỗi riêng; không làm mất chức năng
  replay đã có đầu vào hợp lệ. Giữ attribution ảnh và bản đồ.

## 7. Kiểm thử và đo nghiệm thu

| Tầng | Ca tối thiểu |
| --- | --- |
| Unit replay | Timestamp/gap/segment, pause/rate/seek, generation cũ, image_id, thiếu hướng/tốc độ |
| Unit cảnh báo | P.103a không nhắc motorcycle; P.131a áp dụng cả ba; góc 359°/1°; sai hướng/trạng thái; hai tấm cùng loại; cooldown/rearm |
| Unit data | Cursor/run, response đến muộn, null, 401/403/404/410/413/422/503 và retry có kiểm soát |
| Detector parity | Ảnh ngang/dọc, EXIF, pad lẻ, không biển, hai loại gần nhau, pano; dung sai theo gói AI |
| Tích hợp | So ứng viên trước chống lặp với ahead giao tập biển dọc chuyến, cùng run/xe/trace/params; cập nhật snapshot giữa phiên |
| Thiết bị | Bản đồ thật, ảnh/model thật, vòng đời ứng dụng, TTS, mất mạng và lưu phiên |

Sau khi có project, chạy từ `android/` trên Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
# Khi đã có SDK/thiết bị và ca instrumentation:
.\gradlew.bat connectedDebugAndroidTest
# Chạy lint phù hợp trước bàn giao:
.\gradlew.bat lintDebug
```

Đo theo evaluation.md: replay 1x; tách FPS inference/hiển thị; latency p50/p95,
frame bỏ, decode/preprocess/inference/NMS/TTS. Ghi máy/chip/RAM/Android,
model/hash/input/delegate/threads, mạng/cache, nhiệt và pin. Protocol dự kiến:
warmup 30 s, cửa sổ pin 10 phút, ít nhất 3 lượt, không sạc, điều kiện cố định.
Thông số protocol còn dự kiến phải được khóa trước đo; không tự đặt ngưỡng FPS đạt.

Ghi t_gặp, t_banner, t_TTS_start để đo nhắc trước mục tiêu 5 s; dùng mốc gặp đã gán
hoặc trace map-matched kiểm được, không suy d/v để nhận đã đo kịp.
Đồng hồ monotonic phục vụ latency; timestamp trace phục vụ lead time, không trừ lẫn
hai miền đồng hồ. Log cùng run/session/sign/image để truy vết và đồng bộ.
Xuất `device-runs.csv` theo schema evaluation.md; log sự kiện chi tiết giữ ngoài Git.

Nghiệm thu App: build/test đạt; có một chuyến phát được và biển đủ nhắc ở mỗi năm
2023–2025; parity cảnh báo và detector đạt; demo thay biển dùng CSDL mô phỏng có
nhãn; báo đủ số đo thật và phần chưa kiểm. APK build được chưa chứng minh chạy máy thật.

## 8. Việc bắt đầu và các điểm chặn

1. Kiểm Android Studio/SDK, Gradle JDK 17, adb/thiết bị và cấu hình CI.
2. Khởi tạo Compose trong nhánh riêng, build APK cơ bản.
3. Thử Vietmap với key hợp lệ; xác minh quyền Map Matching và đầu ra road_ref.
4. Tạo repository fake cùng interface thật và replay fixture có nhãn minh họa.
5. Tích hợp một lát cắt thật: login → chọn chuyến → trace/ảnh → bản đồ → một cảnh báo.
6. Thêm detector đã kiểm parity, rồi mở rộng ba năm và đo nghiệm thu.

Điểm chặn từ ngoài App: backend/API và tài khoản chưa bàn giao; tuyến/corpus chưa
khóa; N/theta/theta_low còn null; model chưa có; key/quota Vietmap; thiết bị thật.
N còn null có thể không có biển tự xác nhận đủ nhắc. Không sửa cấu hình nhóm để
lấp chỗ thiếu; dùng fake có nhãn cho phần độc lập và ghi đúng phần chưa tích hợp.

Mỗi PR ghi làm gì, đã kiểm tra thế nào, phần chưa kiểm; mục Đổi hợp đồng nếu có.
Không tự merge và không commit model/ảnh/credential. Kế hoạch này không đổi hợp đồng.
