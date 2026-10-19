# Thuật toán LiveSign

Đặc tả thiết kế 1.1, **chưa triển khai**. Tên trường theo [lần thấy](contracts/observation.md),
trạng thái theo [quyết định](decisions.md); tham số từ [params.yaml](../config/params.yaml).
Các ví dụ tính tay đều **minh họa**, không phải kết quả thực nghiệm.
Các đoạn `text` là pseudocode, không phải script chạy.

## Quy ước và ranh giới

| Đại lượng | Quy ước |
| --- | --- |
| Tọa độ trao đổi | WGS84 `[lon, lat]`, góc độ; MySQL POINT SRID 4326 |
| Tọa độ tính hình học cục bộ | `(x,y)` mét, Đông/Bắc, gốc gần đoạn demo; đổi về WGS84 trước khi gửi |
| Góc hướng | Bắc=0°, Đông=90°, tăng thuận chiều kim đồng hồ, chuẩn hóa [0,360) |
| Góc tương đối | `off_deg` trong [-180,180), dương bên phải camera |
| `angle_diff(a,b)` | Chênh lệch tuyệt đối nhỏ nhất [0,180] trong [geometry.py](../ai/livesign_ai/geometry.py) |
| Thời gian | `captured_at` gốc, không phải thời gian upload; cutoff độc quyền theo evaluation_year |
| Không biết | `null`/`unknown`; không dùng 0, model bỏ sót, hoặc thiếu ảnh để kết luận âm |

Không dùng nhãn mốc để chọn tham số, định vị, gộp hoặc xác nhận biển. Dữ liệu sau
cutoff không được dùng để sửa tọa độ/cluster/ảnh bằng chứng của snapshot cũ.

## Ước lượng vị trí biển

### Ba phương án

| Cách | Đầu vào → đầu ra | Ưu / giới hạn | Vai trò đề xuất |
| --- | --- | --- | --- |
| Feature Mapillary | `source_sign_id` + geometry feature → sign_lon/sign_lat | Có sẵn, ít tính toán; vị trí/loại do Mapillary suy ra, có nhiễu, không được gọi ground truth | Dự phòng có gắn provenance; so đối chứng riêng |
| Giao tia bình phương tối thiểu | ≥2 lần thấy cùng tấm, camera GPS + heading + bbox + thông số ảnh → vị trí, phần dư, điều kiện nghiệm | Không cần biết kích thước biển; nhạy GPS/góc, sai liên kết tấm, gần song song | Phương án chính của **thử nghiệm định vị nâng cao**, chưa bật mặc định |
| Camera + khoảng cách từ bbox | GPS camera + hướng tia + tiêu cự + kích thước vật lý biết trước → vị trí | Một ảnh cũng tính được; biển nghiêng/kích thước chưa biết làm sai khoảng cách lớn | Chỉ thử khi có hiệu chuẩn; thiếu kích thước → chưa đủ dữ liệu |

**Cần nhóm quyết:** đề xuất giao tia làm phương án chính khi nghiệm đạt kiểm tra,
dự phòng feature Mapillary có nguồn; nếu cả hai thiếu thì lấy GPS camera và gắn
`location_method=camera_proxy` để giữ ứng viên, không nhận là vị trí thật.
Lý do: giao tia có thể kiểm tra phần dư; feature có sẵn; bbox không đủ để suy độ sâu.
Đây là mở rộng định vị. **Phép so B0–B3 chính giữ cùng vị trí camera + gộp 20 m**,
không đổi định vị/hướng giữa các bậc rồi gán mọi cải thiện cho điểm tin cậy.

### Giao tia: công thức và pseudocode

Đầu vào: tập cùng tấm/loại `seen` đã kiểm tra, bbox trên đúng ảnh đã khai kích thước,
camera heading/GPS thật, `is_pano/focal`, cutoff. Đầu ra: tọa độ hoặc lý do không đủ
dữ liệu; `location_method`, `ray_residual_m`, `ray_condition_number` trong provenance.
Tham số: `positioning.min_ray_crossing_deg`, `max_condition_number`, `max_ray_residual_m`.

Với camera `p_i=(x_i,y_i)`, tính
`a_i = box_angle(cx_i,w_i,h_i,is_pano_i,focal_i)` bằng geometry.py;
`ψ_i = wrap360(camera_heading_deg_i + a_i)`; `u_i=(sin ψ_i, cos ψ_i)`.
Đường tia: `p_i + λ_i u_i`, chỉ chấp nhận `λ_i>0`.
Đặt `P_i = I − u_i u_iᵀ`, giải `A q=b`, trong đó
`A=ΣP_i`, `b=ΣP_i p_i`. Đây là bình phương khoảng cách vuông góc tới các đường tia;
kiểm tra λ sau khi giải để loại giao phía sau camera. Không gán trọng số confidence
thành độ chính xác GPS khi chưa hiệu chuẩn.
Góc giao để sàng hai đường là `min(angle_diff(ψ1,ψ2),180−angle_diff(ψ1,ψ2))`;
hai hướng gần đối nhau cũng suy biến như gần song song, không coi 180° là góc giao tốt.

```text
LẤY các seen trước cutoff, cùng tấm, có camera/bbox/thông số hợp lệ
LOẠI ảnh lặp; nếu chỉ một vị trí camera hoặc tia gần song song: KHÔNG ĐỦ DỮ LIỆU
CHIẾU camera sang hệ Đông/Bắc cục bộ, tính u từ heading + box_angle
TẠO A, b bằng cách cộng từng tia
NẾU A suy biến hoặc condition_number(A) vượt ngưỡng: dùng dự phòng
GIẢI q; tính λ_i = dot(q-p_i,u_i), residual_i = norm(P_i(q-p_i))
NẾU λ_i không dương hoặc residual lớn: kiểm tra sai liên kết, lọc tia rồi giải lại
NẾU không còn ≥2 tia hình học tốt: dùng dự phòng
TRẢ q đổi về WGS84 + danh sách ảnh đã dùng + phần dư + params_version
```

Độ phức tạp: cộng n tia O(n), giải ma trận 2×2 O(1), bộ nhớ O(n) nếu giữ phần dư;
mỗi vòng lọc O(n), giới hạn số vòng trong manifest, không lặp đến khi vừa tập test.
Sàng góc mọi cặp O(n²); có thể chọn cặp rộng góc trước, không quảng cáo toàn thuật toán O(n).

Ví dụ mét: camera thứ nhất `(0,0)`, ψ=45°; camera thứ hai `(10,0)`, ψ=315°.
`P_1=[[0.5,-0.5],[-0.5,0.5]]`, `P_2=[[0.5,0.5],[0.5,0.5]]`;
`A=I`, `b=(5,5)` → biển `(5,5)`. Hai λ đều `√50=7,071 m`, phần dư 0.
Hai tia cùng ψ=0° làm A hạng 1 → không có nghiệm 2D xác định.

### Khoảng cách từ bbox và sai số

Với ảnh phối cảnh, tiêu cự pixel `f_px=focal×max(w,h)`. Nếu biết chiều cao thật H
và tấm gần trực diện, độ sâu dọc trục `Z≈f_px×H/h_box`; từ góc ngang a có thể
xấp xỉ khoảng cách trên mặt phẳng `D≈Z/cos(a)` (chưa xét góc cao/độ nghiêng).
Đặt `q=p+D×u`. Không áp công thức này trực tiếp cho panorama equirectangular.

Ví dụ minh họa: `f_px=1600`, H=0,6 m, h_box=48 px → Z=20 m; a=0° → D=20 m.
Nếu biển thật H=0,9 m thì Z=30 m: sai 10 m dù bbox không đổi.
`positioning.bbox_physical_height_m=null` nghĩa là chưa được bật phương án này.
Độ phức tạp một bbox O(1), n bbox O(n); bộ nhớ O(1) ngoài dữ liệu nguồn.

Mục tiêu tham chiếu **khoảng 2–3 m** (`ground_truth.reference_error_target_m=3`, dự kiến)
là yêu cầu cần chứng minh, **chưa phải sai số kỳ vọng được đo**. Báo trung vị/P90
sai số chỉ cho mốc đủ tin cậy, kèm độ bất định mốc. Sai heading 3° ở 30 m tạo lệch
ngang khoảng `30×tan(3°)=1,57 m`, chưa gồm GPS và liên kết sai; vì thế chưa thể hứa 2–3 m.
Ảnh dùng focal fallback, camera nhảy, pano không đúng hình chiếu, tia quá gần nhau,
khác tấm cùng loại, hoặc mốc không chắc → giữ lý do thiếu dữ liệu; không ép một tọa độ đẹp.

## Gộp biển và giữ sign_id

Đầu vào: seen hợp lệ có vị trí ước lượng, loại, hướng mặt (có thể thiếu), registry sign,
ảnh/cặp đồng xuất hiện. Đầu ra: target_sign_id, sign mới hoặc ca cần kiểm tra.
Tham số: `geometry.merge_radius_m`, `geometry.merge_max_facing_diff_deg`.

Một ứng viên gộp phải cùng `sign_type_code`, cách ≤20 m (dự kiến), lệch hướng mặt
≤30° (dự kiến), không bị bằng chứng hai tấm trong cùng ảnh loại trừ. Hai hướng 350°
và 10° lệch 20°, **không** phải 340°. Heading camera không thay hướng mặt biển.
Hướng mặt thiếu → giữ candidate riêng theo hợp đồng, không tự gộp theo hướng.

```text
SẮP lô theo captured_at, image_id, observation_id để kết quả có thể lặp lại
VỚI mỗi seen chưa có target hợp lệ:
  TÌM sign cùng loại trong bán kính bằng SPATIAL index rồi tính mét chính xác
  LỌC theo hướng mặt; bỏ sign removed/replaced khỏi gộp tự động
  BỎ cặp đã biết là hai tấm cùng ảnh hoặc khác nhánh đường chắc chắn
  NẾU đúng một ứng viên không mơ hồ: gán vào sign đó
  NẾU không có: tạo candidate với sign_id mới và lưu registry
  NẾU có nhiều ứng viên gần ngang nhau: giữ riêng/đưa kiểm tra, không chọn ngẫu nhiên
  KHÔNG đổi loại sign cũ để hấp thụ biển loại mới
SAU lô: tính lại chỉ sign bị ảnh hưởng và các hàng xóm cần kiểm tra mâu thuẫn
```

**Cần nhóm quyết:** đề xuất nhập tăng dần với registry ID bền, xử lý theo thứ tự cố
định; chạy lại toàn khu chỉ trong workspace thí nghiệm độc lập. Lý do: tránh đổi
sign_id/khóa review khi lô đến muộn. Khi khôi phục từ đầu phải dùng manifest liên kết
`observation_id→sign_id` đã đóng; đổi thuật toán thì phiên bản hóa registry, báo tách/gộp
thành thay đổi có kiểm tra, không tái sinh ID từ centroid đã dịch chuyển.
Không hứa cùng ID khi xóa sạch registry và đổi thứ tự lô. Không dùng số lớp hay vị trí làm ID.

Tránh nối chuỗi: A–B=15 m, B–C=15 m, A–C=30 m; bán kính 20 m không cho suy cả ba
là một tấm qua union-find liên thông. Kiểm tra cả cụm/neo cố định và điều kiện tấm;
không để centroid trôi hấp thụ dần hai biển khác nhau. Khi di chuyển vị trí sign,
lưu before/after và không sửa snapshot cũ bằng ảnh tương lai.

| Ca biên | Ví dụ minh họa | Xử lý |
| --- | --- | --- |
| Biển đối diện | Cùng loại, 8 m, mặt 0°/180° | Không gộp vì lệch 180° |
| Chung cột khác loại | P.102 và P.131a xuất hiện cùng ảnh | Hai sign_id; không kích hoạt thay loại |
| Hai tấm cùng loại | Hai bbox tách biệt cùng ảnh, cách địa lý 4 m | Giữ hai tấm; khoảng cách nhỏ không đủ gộp |
| Đường song song | Cùng loại/cùng hướng, cách 12 m | Cần liên kết đường hoặc kiểm ảnh; không tự tuyên bố chắc một tấm |
| Sai mã/thiếu hướng | Loại null hoặc facing null | Không auto merge; metadata_only hoặc candidate theo đủ dữ liệu |
| Lô gửi lại | Cùng observation_id/payload | Duplicate, không tạo sign/cặp mới |

Độ phức tạp m quan sát, s sign: tìm chỉ mục xấp xỉ O(m log s + m c) với c ứng viên
mỗi vùng; trường hợp dày/không dùng index O(ms). Giữ quan hệ đồng xuất hiện có thể
O(b²) trên ảnh b bbox. Toàn khu xây lại bằng so từng cặp O(s²), cần báo cấu hình thật.

## Kiểm tra nhìn được và tạo not_seen

Đầu vào: camera/ảnh, target_sign_id/loại/vị trí/hướng mặt cũ, kết quả detector,
kiểm tra độ nét/che khuất độc lập. Đầu ra: bốn cờ nullable và outcome, lý do.
Tham số: `geometry.visibility_max_distance_m=30`, `perspective_frame_keep`,
`default_focal`; `visibility.max_facing_diff_deg`, `sharp_laplacian_min`, `min_visible_fraction`.

Phối cảnh: nửa góc ngang `hFOV=atan(w/(2×focal×max(w,h)))` đổi sang độ;
`in_fov = |off_deg| ≤ perspective_frame_keep×hFOV`.
Pano đúng equirectangular có 360° ngang; seam cần wrap, không dùng góc phối cảnh.
`facing_camera = angle_diff(sign_facing_deg,bearing(sign→camera)) ≤ max_facing_diff_deg`.
Distance địa lý camera–biển dùng mét; 30 m chấp nhận, 30,01 m không đủ âm.

```text
NẾU có detection đúng tấm/loại và metadata hợp lệ: TRẢ seen
TÍNH distance và các cờ nhìn được, giữ null khi không kiểm tra được
NẾU target/loại/camera/focal/ảnh bằng chứng thiếu: TRẢ unknown
NẾU distance > 30 hoặc có cờ false/null: TRẢ unknown
NẾU kiểm tra âm chưa được duyệt hoặc negative_evidence_enabled khác true:
  BỘ SINH TRẢ unknown; API từ chối nếu client vẫn gửi not_seen (422)
CHỈ KHI không phát hiện loại cũ và mọi điều kiện đủ: TRẢ not_seen
```

Ví dụ: w=2048,h=1152,focal=0,8 → f_px=1638,4; hFOV≈32,01°;
vùng giữa 85% → ±27,21°. Biển off=20°, distance=25 m, hướng mặt 180°,
bearing biển→camera=185° → lệch 5°, hai cờ hình học true. Nếu sharp=true nhưng
unoccluded=null thì **unknown**. Đổi unoccluded=true và đã duyệt bằng chứng âm,
không có detection đúng → not_seen; có detection đúng → seen.

Độ nét đề xuất: phương sai Laplacian trên ROI biển kỳ vọng, cùng thang ảnh/kích thước;
ngưỡng **cần đo**, ảnh không có detection vẫn phải xác định ROI từ phép chiếu target.
Không lấy confidence thấp làm chứng cứ mờ. Che khuất: model độc lập/segmentation
trên ROI có thể gợi ý phần nhìn thấy nhưng chưa chứng minh biển vắng mặt; với vị trí
ROI không chắc hoặc chưa có bộ kiểm tra được duyệt thì `unoccluded=null`.
Một detector biển bỏ sót không được đồng thời tự chứng nhận rằng không có vật che.

**Cần nhóm quyết:** đề xuất chỉ cho tạo âm từ kiểm tra human hoặc model độc lập đã
audit khi đánh giá; hệ tự động thiếu kiểm tra nét/che trả unknown. Khóa ngưỡng nét/
che trên tập phát triển sau khi đo; không điền ngưỡng tùy ý để làm B3 chạy được.
Độ phức tạp hình học O(1)/target; Laplacian O(P) pixel ROI; model che O(C_model),
tổng O(v(P+C_model)) cho v target. Ảnh <2048px không tự thành âm hay tự thành sai;
cần báo phân tầng kích thước, pano, ngày/đêm trong đánh giá.

## Điểm tin cậy, độ mới, mâu thuẫn và trạng thái

Đầu vào: observation accepted trước cutoff của một sign, lịch sử trạng thái/quyết
định admin đủ điều kiện, params snapshot. Đầu ra: α, β, agreement_score, số cặp,
last_seen_at, last_evidence_at, tuổi riêng, conflict_reasons và trạng thái.
Tham số: `reliability.N/theta/theta_low/H/K/alpha_prior/beta_prior/decay_base`;
`evaluation.years_T/cutoff_exclusive_utc`, `workflow.negative_evidence_enabled`.

### Gom cặp và thứ tự

Khóa `(target_sign_id,creator_id,captured_year)`. Creator phải là người chụp gốc.
Có bất kỳ seen hợp lệ → cặp dương; nếu không seen nhưng có not_seen hợp lệ → âm;
unknown không tạo cặp, không đẩy thời điểm cuối lên mới hơn. Nhiều sequence cùng
người/năm vẫn một cặp. Thời điểm cuối của cặp là max thời điểm của seen/not_seen hợp lệ,
không phải thời điểm upload. Trọng số mỗi cặp chỉ phụ thuộc năm t: `w=0,5^(T−t)`.

Nếu precision=day, thời điểm thật là một **khoảng cả ngày UTC**, không phải 00:00 thật.
Các cặp có khoảng thời gian giao nhau không có thứ tự chắc chắn. Với dấu hiệu (a),
chỉ kết luận K cặp mới nhất âm khi mọi cách xếp phù hợp thời gian đều cho K âm.
Ví dụ K=2, ba cặp cùng ngày gồm `+,-,-`: có thể + nằm cuối → chưa đủ kết luận (a).
Nếu hai cặp chắc chắn mới nhất đều âm dù hòa thứ tự nội bộ thì (a) vẫn đúng.
Không bẻ hòa bằng creator_id/observation_id để tăng số ca nghi thay đổi.

**Cần nhóm quyết:** áp dụng cách xử lý thời gian theo khoảng như trên; khi thứ tự
không chắc, trả lý do chưa đủ cơ sở và vẫn xét độc lập (b)/(c). Lý do: ngày thiếu giờ
không cho phép suy ra cặp cuối cùng từ ID hoặc thứ tự file.

### Ba dấu hiệu mâu thuẫn

| Dấu hiệu | Điều kiện | conflict_reasons đề xuất |
| --- | --- | --- |
| (a) | K cặp mới nhất chắc chắn đều âm | `latest_k_pairs_negative` (đã có trong API) |
| (b) | Từng xác nhận và score < θ_thấp | `agreement_below_low` |
| (c) | Loại khác cùng vị trí/hướng, có seen mới; loại cũ not_seen hợp lệ; loại trừ chung cột | `type_change_evidence` |

(c) cần **hai sign_id**, cặp trước/sau và liên kết chứng cứ; không đổi nhãn một
quan sát cũ. Cùng ảnh có cả loại cũ/mới → hai tấm, chặn (c). Chỉ không có detection
loại cũ nhưng visibility chưa đủ thì chưa có chứng cứ thay loại.
Khi chưa duyệt âm, (a)/(b) không được dựng bằng not_seen giả; (c) cũng cần phần vắng
loại cũ hợp lệ. B3 chưa được bật chỉ vì đã cài công thức.

Admin confirm/keep có thể bác một ca mà các cặp âm gốc vẫn còn. Theo đề xuất
watermark ở API 1.1, không mở lại **cùng tập chứng cứ đã được duyệt** chỉ do đọc
lại/sang năm; giữ nguồn confirmed_manual và hiển thị điểm số thực chưa bị nâng.
Chỉ khi có seen/not_seen hợp lệ mới sau watermark mới đánh giá việc mở lại ca;
unknown không kích hoạt mở lại. Áp dụng CHƯA RÕ theo H vẫn bình thường khi không
có bằng chứng mới. Quy tắc này còn cần nhóm duyệt, không xóa (a)/(b) khỏi số liệu
tự động trước admin hoặc khỏi kết quả pre_review.

```text
LẤY observation trước cutoff, accepted, có creator, đúng liên kết tấm và phiên bản
GOM cặp; tính α=1+Σw_dương; β=1+Σw_âm; score=α/(α+β)
ĐẾM cặp dương/âm thật, không trọng số, không tính prior
TÍNH tuổi dương từ seen gần nhất; tuổi bằng chứng từ seen/not_seen gần nhất
XÉT từng dấu hiệu mâu thuẫn có đủ tham số/chứng cứ; giữ lý do chưa tính được
NẾU removed/replaced do admin: giữ trạng thái; không tự hồi sinh
NẾU có mâu thuẫn đủ cơ sở: suspected_change
NGƯỢC LẠI nếu từng xác nhận và tuổi mọi bằng chứng > H: unclear
NGƯỢC LẠI nếu đang unclear/suspected_change:
  chỉ phục hồi khi có cặp dương mới sau lần chuyển và hết mâu thuẫn;
  phục hồi nguồn xác nhận auto/manual trước đó, không lấy score>=θ làm điều kiện hồi mới
NGƯỢC LẠI nếu đã confirmed_auto/confirmed_manual: giữ nguồn xác nhận
NGƯỢC LẠI nếu có cặp dương và N, θ đã chốt và count>=N và score>=θ: confirmed_auto
NGƯỢC LẠI nếu có cặp dương: candidate
NẾU không có cặp dương và chưa có sign: không tạo sign từ âm/unknown
GHI history khi trạng thái/điểm đổi; một review pending/sign; quyết định admin bằng transaction
```

**Cần nhóm quyết:** ưu tiên mâu thuẫn trước cũ, giữ nguồn xác nhận khi phục hồi,
candidate cũng được nghi khi có mâu thuẫn có cơ sở; hạ score dưới θ nhưng chưa
dưới θ_thấp không tự hủy xác nhận. Lý do: θ là ngưỡng đi vào xác nhận; θ_thấp
là ngưỡng mâu thuẫn. N/θ null **không tự xác nhận**; θ_thấp null nghĩa là chưa đánh
giá được (b), không thay bằng 0. Khi đánh giá cấu hình chưa đóng phải báo `not_run`.

### Ví dụ đầy đủ bốn năm

Fixture minh họa dùng N=3, θ=0,75, θ_thấp=0,40, H=2, K=2, base=0,5;
**không ghi đè N/θ null trong config**. Một tấm P.131a, không có (c).

| Năm | Cặp mới | α | β | Điểm | Tuổi dương / mọi bằng chứng | Trạng thái cuối năm |
| --- | --- | --- | --- | --- | --- | --- |
| 2022 | Bốn người khác nhau, mỗi người seen | 1+4=5 | 1 | 5/6=0,833333 | 0 / 0 | confirmed_auto |
| 2023 | Không có bằng chứng | 1+4×0,5=3 | 1 | 3/4=0,75 | 1 / 1 | confirmed_auto |
| 2024 | Một người mới not_seen ngày 01/06 | 1+4×0,25=2 | 1+1=2 | 1/2=0,5 | 2 / 0 | confirmed_auto; chưa (a), score chưa <0,40 |
| 2025 | Một người mới khác not_seen ngày 01/07 | 1+4×0,125=1,5 | 1+0,5+1=2,5 | 1,5/4=0,375 | 3 / 0 | suspected_change: (a) và (b) |

2022 là năm tính tay cho unit test, không phải evaluation_year HTTP được thêm mới.
Năm 2025 có bốn cặp dương thật, không phải 0,5 cặp. Không thành unclear vì có âm mới.
Nếu thêm một cặp dương mới 01/08/2025: α=2,5, β=2,5, score=0,5; hai cặp cuối
là âm rồi dương, hết (a)/(b)/(c) → trở lại confirmed_auto theo nguồn trước.
Nếu hoàn toàn không có bằng chứng từ sau 2022: ở 2025 α=1,5, β=1, score=0,6,
tuổi mọi bằng chứng=3>2 → unclear. Không thành removed dù không có ảnh mới.

Ca biên: không có cặp → α=β=1, score=0,5 nhưng không xác nhận; cùng người/năm
có âm rồi dương → một cặp dương; đúng bằng θ thì được vào, đúng bằng θ_thấp
chưa kích hoạt (b); tuổi đúng H chưa unclear; K chưa đủ cặp chưa kích hoạt (a).
Âm đến muộn ở năm cũ phải tính lại snapshot liên quan, không chỉ cộng β hiện tại.
Backfill có thể làm đổi lịch sử suy luận, phải lưu phiên bản bản chạy; không xóa lịch sử admin.

### Khi tính lại và độ phức tạp

Sau lô đồng bộ: tập bị ảnh hưởng gồm sign nhận quan sát, sign láng giềng có thể
liên quan (c), và sign cũ/mới khi admin thay. Tính lại từ bằng chứng gốc thay vì
cộng score đã làm tròn. Sang năm T mới phải giảm trọng số và xét H **cả biển không
có lô mới**; đọc snapshot cũng phải kiểm phiên bản/năm. Không chỉ cập nhật khi có ảnh mới.

n dòng/sign, p cặp: hash gom O(n), sắp theo thời gian O(p log p), bộ nhớ O(n+p)
nếu giữ provenance, O(p) nếu stream; kiểm (c) thêm ứng viên không gian c.
Snapshot key gồm evaluation_year, cutoff, params_version **và hash config**, model_version,
classes_version, dataset/registry version, chế độ trước/sau admin. Khóa registry và
tọa độ phải được dựng từ dữ liệu trước cutoff, không chỉ lọc score sau khi đã gộp tương lai.

## Biển phía trước và chống nhắc lặp

Đầu vào: điểm replay hợp lệ, heading, speed_mps, road_ref sau map matching,
vehicle_type, evaluation_year và snapshot, bộ nhớ nhắc của phiên. Đầu ra: items và
banner/TTS, hoặc trạng thái không đủ điều kiện. Tham số: `warning_min_lead_s`,
`ahead_max_bearing_diff_deg`, `ahead_max_facing_diff_deg`, `replay.alert_cooldown_s`,
`alert_rearm_distance_m`.

```text
GIỮ timestamp/GPS gốc; map match để xác định đoạn đường, không thay người chụp
GỌI /signs/ahead với lon, lat, heading_deg, speed_mps, loại xe, năm, road_ref
SERVER tính D=speed_mps×warning_min_lead_s; speed=0 thì items rỗng
TÌM biển trong D mét, cùng đường phù hợp, hướng tới biển và mặt biển đạt ngưỡng
LỌC lớp áp dụng xe; bỏ candidate/removed/replaced; gắn nhãn unclear/suspected_change
APP sắp gần trước; với mỗi sign chưa nhắc trong lần tiếp cận: hiện banner và TTS
CHỈ tái nhắc sau khi rời ≥rearm_distance và trở lại, đồng thời đã qua cooldown
ĐỔI chuyến/năm/seek về trước: mở phiên mới có log reset, không trộn số đo hai phiên
```

Khóa chống lặp `(replay_session, evaluation_year, sign_id)`; không dùng mỗi class
vì hai biển cùng loại vẫn là hai tấm. Chuyển trạng thái cùng sign cập nhật banner,
không tự nói lại liên tục. Biển thay mới có sign_id khác. Mất mạng không nói dữ liệu
cũ như đã xác minh mới; chỉ dùng snapshot cache cùng năm/version và báo rõ mất mạng.

Ví dụ speed=10 m/s, min_lead=5 s → D=50 m. Biển 45 m, hướng tới 5° so với xe 0°,
facing=180°, bearing ngược=185°, xe car thuộc lớp → có thể nhắc; lead=4,5 s,
**chưa đạt ≥5 giây**. Biển 55 m chưa vào kết quả dù cho lead=5,5 s. Do D hiện bằng
đúng 5 giây, lấy mẫu/API/TTS có thể làm nhắc trễ. Không tự thêm hệ số đệm làm đổi hợp
đồng; đo tỷ lệ nhắc kịp và ghi rõ hạn chế, đề xuất đệm chỉ qua quyết định bổ sung.

**Cần nhóm quyết:** dùng chống lặp theo lần tiếp cận như trên (15 s/80 m là dự kiến);
đo đủ trễ trước khi đề nghị đệm khoảng cách. Cần thống nhất road_ref của Vietmap với
sign.road_ref; nếu không có ánh xạ thì `unverified`, không nhận đã giải quyết đường song song.
Độ phức tạp server xấp xỉ O(log s+c), app sắp c biển O(c log c), cache O(a) biển đã nhắc.
GPS nhảy/heading null/biển thiếu hướng mặt → không suy cảnh báo chắc chắn; pano
không đổi heading xe; không nhân speed_mps theo tốc độ phát trình diễn.
