# Thiết kế thực nghiệm và cách báo kết quả

Chưa có kết quả LiveSign B0–B3, model 12 lớp hoặc phép đo app mới. Số có sẵn ở
[PoC summary](../tools/poc/results/summary.csv) và [coverage summary](../tools/mapillary-coverage/out/summary.json)
chỉ dùng mô tả nguồn dữ liệu, không điền thay số thực nghiệm chưa chạy.
Tuyến chính khoảng 1,5 km lõi Hoàn Kiếm **chưa chốt**; GeoJSON đang candidate.

## Cấu hình một lần chạy có thể lặp lại

Manifest kết quả gồm run_id, commit, corpus/registry/gt version, hash tuyến,
params_version + hash config, model_version/exporter, classes_version,
evaluation_year/cutoff, seed, chế độ trước/sau admin và data_kind.
T giữ [2023,2024,2025]; cutoff UTC độc quyền lần lượt 01/01 năm sau theo params.
Không dùng observation, vị trí gộp, ảnh/cặp tham chiếu hoặc quyết định biết sau cutoff.
Ảnh được nạp sau này vẫn có captured_at lịch sử; ghi rõ retrospective, không nhận
là thử nghiệm hệ chạy trực tiếp tại thời điểm chụp.

**Cần nhóm quyết:** khóa tập phát triển **trước T đầu tiên**, đề xuất chỉ dùng dữ
liệu ≤2022 để chọn tham số cho cả ba mốc. Lý do: chọn ngưỡng bằng 2023 rồi báo
2023 như test độc lập sẽ rò kết quả. Nếu thiếu dữ liệu phát triển, công bố cấu hình
do nhóm quy ước và chỉ phân tích độ nhạy, không gọi đó là ngưỡng tối ưu đã kiểm chứng.
N/θ/θ_thấp còn null: đánh dấu bậc phụ thuộc là not_run, không tự dùng proposed để chấm chính.

## Thang B0–B3

| Bậc | Tập tấm được tính có trên bản đồ tại T |
| --- | --- |
| B0 | Ít nhất một seen hợp lệ trước cutoff; cùng loại gộp 20 m, vị trí camera; không yêu cầu N/tuổi |
| B1 | Cùng đầu vào/cluster B0, ≥N cặp dương thật, không xét thời gian; **không thêm θ làm điều kiện riêng của B1** |
| B2 | B1 và tuổi cặp dương gần nhất ≤H; biển cũ hơn bị loại khi chấm |
| B3 | B2 và không ở suspected_change theo điểm giảm dần và dấu hiệu (a)(b)(c); cần bài thử âm cho phép |

Giữ nguyên detector, source corpus, tọa độ và luật gộp ở cả bốn bậc khi muốn quy
cải thiện cho độ đa dạng/độ mới/mâu thuẫn. Gộp có hướng, giao tia hoặc feature
Mapillary là các phép so thêm riêng. Không thay detector/định vị đồng thời lúc
thêm B3 rồi nhận toàn bộ chênh lệch là tác dụng của âm.
Score tính mọi cặp hợp lệ, dấu hiệu (b) chỉ sau khi từng xác nhận theo vòng đời;
thuật toán phục hồi/ưu tiên cần được nhóm chốt trong [algorithms.md](algorithms.md).

Bản đồ **sản phẩm** vẫn nhắc unclear/suspected_change kèm nhãn theo API; bảng này
là phép lọc chấm, không phải thay chính sách sản phẩm. B1 không đồng nghĩa mọi tấm
đều confirmed_auto; auto/manual thống kê từ lịch sử xác nhận, không suy từ bậc đang chấm.

### So với Mapillary và cách làm phổ biến hiện nay

B0 là cách làm phổ biến hiện nay đã định nghĩa trong repo. Lớp feature Mapillary
là đối chứng bên ngoài trên cùng tuyến/12 lớp, nhãn được đối chiếu đúng mã.
Không có snapshot feature tại cuối T thì **không được** lấy feature hiện tại rồi
lọc first_seen/last_seen để nhận là ảnh chụp đầy đủ quá khứ. Ghi not_available ở
mốc thiếu snapshot; nếu dùng lớp hiện tại chỉ báo phép so tại thời điểm hiện tại,
không đặt cạnh B0–B3 quá khứ như cùng điều kiện.

## Bộ mốc và các mẫu số

Theo [ground-truth.md](contracts/ground-truth.md), khóa vùng/năm đủ ảnh và danh
sách uncertain/pending trước chấm. Duyệt toàn tuyến; ít nhất 20% tấm gán chéo độc lập.
Mốc tồn tại tại T cần ảnh xác nhận năm đó; first/last không tự lấp năm thiếu.

Gọi D là các tấm **được bậc đang chấm tính hiển thị** trong vùng/năm đủ đánh giá;
G là các tấm mốc chắc còn tồn tại tại T trong cùng vùng/12 lớp. Matching một–một
cùng loại, ≤X mét, ≤Y° theo hợp đồng mốc; TP là số cặp, FP=|D|−TP, FN=|G|−TP.

| Chỉ số | Công thức / mẫu số |
| --- | --- |
| Precision | TP / |D|: trong biển bản đồ hiển thị, bao nhiêu khớp mốc |
| Coverage | TP / |G|: trong biển mốc còn tồn tại tại T, bao nhiêu được khớp |
| Tải admin | Số review_id phân biệt cần duyệt, đếm ca mới và ca mở lại riêng; thêm số thao tác thực tế |
| Tỷ lệ tự xác nhận | Số tấm confirmed_auto / số tấm từng được xác nhận (auto hoặc manual) trong cùng điều kiện |
| Nhạy dung sai | Báo đủ X=3/5/10 m với Y đã khóa; không chỉ chọn mức có số đẹp |

Mẫu số 0 → null/“không tính được”, không tự 0% hay 100%. Prediction sai loại,
thiếu hướng hoặc tách nhầm không loại khỏi |D| chỉ vì không ghép được; đó là FP.
Trong vùng đã duyệt toàn diện, prediction không có mốc cũng là FP. Chỉ vùng/tấm
được mask vì GT uncertain **từ trước** mới ngoài mẫu số; lưu excluded_gt_count,
excluded_prediction_count và lý do, không mask thêm sau khi biết phương án thua.
Tấm cũ đã có absent_evidence năm T nếu vẫn hiển thị là FP.

Ví dụ số **minh họa cách tính**: 10 tấm mốc tồn tại, bản đồ có 8 tấm, 6 cặp khớp
→TP=6, FP=2, FN=4, precision=6/8=0,75, coverage=6/10=0,60. Hai prediction cùng
khớp một mốc chỉ được một TP. Đây không phải kết quả LiveSign đã đo.

### Trước và sau admin

Chạy `pre_review` với quyết định tự động/quan sát trước cutoff; sau đó `post_review`
trên cùng đầu vào, ghi mỗi quyết định và số ca/thời gian người duyệt. Không xóa
khỏi kết quả trước duyệt những ca admin đã sửa. Báo chuyển dịch auto→manual,
không cộng manual thành auto. Quyết định năm sau không lọt vào chấm lịch sử thật;
demo tái diễn quyết định dùng scenario riêng, ghi rõ mô phỏng.

Tải admin không phải số tấm cuối cùng pending: phải tính cả ca đã duyệt, defer,
mở lại do bằng chứng mới. Một sign nhiều lý do cùng ca không được đếm nhiều lần;
retry HTTP/đọc lại hàng chờ không là thao tác duyệt mới.

## Mô phỏng 10 biển giả và thay 5 biển

Thiết kế kiểm logic, không nhận là phát hiện 15 thay đổi ngoài đường.

1. Copy corpus/CSDL demo riêng, cố định seed và manifest scenario_id; không sửa bộ
   mốc thật hoặc xóa quan sát gốc. Lưu danh sách sign bị tác động và thời điểm tiêm.
2. Chọn **10 vị trí đã duyệt không có tấm đó** trong ảnh công khai, loại/hướng nằm
   trong danh mục; tạo biển giả với prior history đã xác nhận cho phép thử mâu thuẫn.
   Mọi bằng chứng dương giả dùng creator namespace simulation trong seed CSDL riêng,
   ghi scenario_id ở source_metadata.provenance; không dùng ID người chụp thật để
   giả họ đã thấy. Không thêm enum simulation vào observation.source hoặc trip.data_kind.
3. Với **5 tấm thật khác nhau**, copy bản đồ rồi đặt loại cũ sai theo kịch bản tại
   mốc tiêm, giữ loại đúng trong ảnh và GT. Không thay nhãn ảnh/test để chiều model.
   Loại cũ/mới cần có trong 12 lớp, giữ hướng/vị trí và lịch sử tiêm riêng.
4. Phát chuỗi bằng chứng sau thời điểm tiêm; âm mô phỏng chỉ ở điểm đạt visibility
   trong kịch bản. Nếu không có ảnh/chứng cứ đủ, đánh dấu censored/không kiểm được,
   không tự cấp 2 cặp âm đúng ý để tuyên bố xử lý ca thật thành công.
5. Giữ nhóm biển thật không tiêm để đo nghi nhầm; báo riêng 10 giả, 5 thay, control.

**Cần nhóm quyết:** khóa manifest tiêm, cách tạo prior giả và thứ tự replay trước
khi chạy; lý do là lịch sử xác nhận và vị trí nhìn được quyết định độ khó, không
thể chọn ca sau khi thấy detector thành công.

Đo `pairs_to_suspect`, `trips_to_suspect`, thời gian từ tiêm đến nghi, phát hiện
hay chưa, lý do (a)/(b)/(c), số biển control nghi nhầm. Lượt không có creator/unknown
không tăng cặp; âm/dương cùng người/năm ưu tiên dương có thể khiến ca không nghi,
đó là giới hạn phải báo. Chưa nghi đến cuối chuỗi là **right-censored**, không ghi
0 cặp hay loại khỏi trung vị. Cần tối thiểu số ca phát hiện/tổng, số censored trước
khi báo trung vị trên các ca có phát hiện.

Ca thật: xem ảnh trước/sau của candidate change do dữ liệu tìm ra, phân thành thay
thật/che/mờ/sai loại/không đủ ảnh. Báo số xác minh và số chưa đủ; không cộng với
15 ca mô phỏng thành một tỷ lệ chung.

## Định dạng đầu ra và bảng trong quyển

Script chấm sau này xuất các file **ngoài kết quả hiện có**, không viết script ở lần này:

| File kết quả | Một dòng / cột trọng tâm |
| --- | --- |
| map-metrics.csv | run_id, corpus_version, gt_version, params_version, config_sha256, model_version, evaluation_year, tier, review_mode, data_kind, result_status, tp, fp, fn, displayed_count, gt_existing_count, precision, coverage, admin_cases, auto_confirmed_count, manual_confirmed_count, auto_confirmation_ratio, excluded_gt_count, excluded_prediction_count |
| matches.csv | run_id, year, tier, prediction_sign_id, gt_id nullable, distance_m, facing_diff_deg, match_result, failure_reason |
| conflict-cases.csv | scenario_id/real_case_id, data_kind, sign_id, injected_kind, detected, pairs_to_suspect, trips_to_suspect, censored, false_alarm, reasons |
| model-metrics.csv | model_version, classes_version, test_manifest_hash, sign_type_code, object_count, map50, map50_95, recall, inference_mode |
| device-runs.csv | run_id, device_model, android_version, chipset, delegate, threads, input_size_px, model_version, replay_rate, duration_s, fps, latency_p50_ms, latency_p95_ms, battery_delta_pct, dropped_frames, network_mode |

result_status: measured/not_run/not_available; số chưa đo để trống CSV, không dùng
chuỗi “cần đo” trong cột số. Bảng trong quyển chuyển null thành “cần đo/không tính được”.
data_kind trong **file kết quả** là public_evaluation/simulation/illustration;
đây không phải enum của bảng trip hoặc payload observation. Mỗi nhóm xuất/báo riêng.
Các file có nhiều dòng thật không đưa vào data-small; chỉ mẫu nhỏ mới ở repo.

| Bậc / năm | Precision trước / sau duyệt | Coverage trước / sau | Ca admin | Xác nhận auto / manual |
| --- | --- | --- | --- | --- |
| B0 / 2023 | Cần đo / cần đo | Cần đo / cần đo | Cần đo | Cần đo |
| B1 / 2023 | Chưa chạy: N chưa chốt | Chưa chạy | Cần đo | Cần đo |
| B2 / 2024 | Cần đo | Cần đo | Cần đo | Cần đo |
| B3 / 2025 | Chưa được quyết định giữ B3 | Chưa được quyết định | Cần đo | Cần đo |

Đây là mẫu bố cục, báo cáo thật phải có đủ tổ hợp bậc×3 năm×pre/post hoặc lý do
không chạy, mẫu số/count và CI nếu tính được. Bootstrap theo tấm/creator/sequence,
không coi các frame gần nhau là mẫu độc lập để thu hẹp CI giả.

## Đánh giá model độc lập

Test Hà Nội khóa theo nguồn/sequence/nhóm gần trùng, không trùng fine-tune/calibration.
Tính AP mỗi lớp tại IoU=0,50; mAP50 là trung bình các lớp đủ định nghĩa của evaluator;
mAP50-95 trung bình các ngưỡng 0,50..0,95 bước 0,05. Báo lớp không có object là
không đánh giá được, không tự cho AP=1; ghi evaluator/version để cách macro thống nhất.
Recall tại confidence/NMS đã khóa; số đếm theo lớp/bbox nhỏ/pano/ngày đêm.
So PyTorch/TFLite và float16/int8 bằng cùng test, không chỉ so kích thước file.

Bài thử B3 là phép đo phát hiện trên **ảnh nhìn rõ** riêng ở [ai-pipeline.md](ai-pipeline.md),
không thay bằng mAP tổng. Mốc ≥50%/<30% của params không đổi; khoảng giữa còn mở.

## Đo Android thật

| Phép đo | Quy trình dự kiến và mẫu số |
| --- | --- |
| FPS | Cùng clip/trace 1x; làm nóng 30 s, đo khung hoàn tất suy luận/giây; tách FPS hiển thị và FPS inference, báo frame bỏ |
| Độ trễ | Monotonic clock từ nhận frame đến bbox/banner; tách decode/preprocess/inference/NMS/network/TTS; p50/p95, không chỉ trung bình |
| Pin | Mỗi phiên 10 phút, ít nhất 3 lần; cùng độ sáng/âm lượng/mạng, thiết bị không sạc, ghi mức pin/nhiệt đầu/cuối |
| Nhắc trước | Với mỗi tấm đủ điều kiện, t_gặp−t_banner và t_gặp−t_TTS_start; dùng timestamp trace gốc, ≥warning_min_lead_s=5 mới kịp |
| Nhắc sai | Tấm khác nhánh/song song, sai loại xe, stale snapshot, lặp cùng lần tiếp cận; báo tử/mẫu số rõ |

Thông tin bắt buộc: máy/chip/RAM/Android, model/version/input, delegate/threads,
replay_rate, cache/network mode, thời gian cửa sổ, nhiệt và pin. `measurement.device_repeats=3`,
warmup=30 s, pin=10 phút là **dự kiến**; ngưỡng đạt FPS/latency/pin **cần đo/chốt**,
không lấy chỉ tiêu của máy khác làm kết quả.

Có thể dùng [Android Batterystats](https://developer.android.com/topic/performance/power/setup-battery-historian)
để bổ sung attribution năng lượng; phần trăm pin hệ thống có độ phân giải thô,
không tự đổi sang mAh khi chưa có phép đo/hiệu chuẩn. Ghi công cụ và cách lấy số.
Đo đường đi tới tấm theo map-matched trace hoặc mốc tiếp cận đã gán độc lập; biển
không có lần đi qua xác định được là chưa đủ đo lead time, không tự lấy khoảng
cách Euclid/velocity để chứng minh cảnh báo kịp.

**Cần nhóm quyết:** khóa protocol ít nhất 3 lượt ở 1x, định nghĩa t_gặp/TTS và ngưỡng
FPS/độ trễ/pin sau phép đo pilot; lý do là target 5 s không thay cho số đo end-to-end.
D=v×5 s có thể cho lead<5 sau API/lấy mẫu; báo tỷ lệ trễ thật, không sửa công thức
chỉ trong script chấm để mọi lượt thành kịp.

## Phân tích thất bại

| Nhóm lỗi | Cách nhận diện | Chỉ số bị ảnh hưởng |
| --- | --- | --- |
| Sai vị trí | Đúng loại/tấm bằng ảnh nhưng ngoài X m; mốc đủ chắc mới nói sai số tuyệt đối | Precision/coverage, nhắc nhánh |
| Gộp nhầm | Một sign chứa hai tấm thật; chung cột/hai bên đường | FN, cảnh báo sai |
| Tách nhầm | Nhiều sign cho một tấm thật | FP, cảnh báo lặp |
| Sai loại | Bbox đúng tấm nhưng mã khác; nhầm trái/phải | FP và FN theo lớp |
| Không đủ bằng chứng | Thiếu creator/camera/ảnh rõ, chỉ unknown, quá ít cặp | Coverage, số ca manual |
| Âm giả | Detector bỏ sót/che/mờ nhưng tạo not_seen | Nghi nhầm, tải admin |
| Rò thời gian | Dùng vị trí/cặp/decision sau cutoff | Kết quả không hợp lệ, phải chạy lại |

Mỗi ca giữ image_id/observation/sign/GT, năm, version, lý do và ảnh có nguồn ngoài
git. Một ca có nhiều lỗi được gắn nhiều tag nhưng đếm tổng ca phân biệt. Báo cả
thất bại chưa giải thích; không loại chúng khỏi thống kê chỉ để câu chuyện gọn hơn.
