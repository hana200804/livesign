# Chuẩn bị môi trường LiveSign

Phần chung ai cũng làm; các phần AI / Backend + Web / Android là checklist cho người nhận việc phần đó.
Lần chuẩn bị này chỉ tạo tài liệu, hợp đồng và cấu hình. Các bước tạo app, train,
chạy thử nghiệp vụ là việc tiếp theo, ai nhận phần nào thì làm phần đó.

## Mọi người

Chạy từ gốc repo trong PowerShell; mỗi người tự đánh dấu sau khi kiểm tra trên máy mình.

| Sẵn sàng | Mục | Kiểm tra và kết quả cần thấy |
| --- | --- | --- |
| [ ] | Git | `git --version`; `git status --short --branch` không có bí mật/dữ liệu lớn |
| [ ] | JDK **21** | `java -version`; `javac -version` đều báo 21; Android Studio chọn Gradle JDK 21 khi cấu hình project |
| [ ] | Node **≥18** | `node --version`; `node -e "if(+process.versions.node.split('.')[0]<18)process.exit(1)"` trả mã 0 |
| [ ] | Python **3.12** | `py -3.12 --version`; `py -3.12 -m venv .venv`; `.\.venv\Scripts\python.exe --version` báo 3.12.x |
| [ ] | Android Studio | Mở Help → About, ghi phiên bản vào biên bản nhóm; `Get-Item 'C:\Program Files\Android\Android Studio\bin\studio64.exe'` xác nhận đường dẫn mặc định (đổi sang nơi đã cài nếu khác) |
| [ ] | Docker Desktop + Compose | `docker version` có cả Client và Server; `docker compose version` chạy được |
| [ ] | Token Mapillary **riêng mỗi người** | Tạo ở [Mapillary Developers](https://www.mapillary.com/dashboard/developers); thử truy vấn bên dưới trả về `data` |
| [ ] | Vietmap API key | Thử key bằng ví dụ chính thức của Maps SDK trên thiết bị; cả nhóm chỉ ghi tình trạng quyền truy cập, không ghi giá trị key |
| [ ] | GitHub token chỉ đọc cho Colab | Fine-grained PAT giới hạn repo này, Contents: Read-only; lưu Colab Secret tên `GITHUB_TOKEN`; phép kiểm tra bên dưới trả 200 |
| [ ] | Drive chung | Nhóm cung cấp URL/thư mục; từng người tải lên rồi tải lại một file văn bản thử; ghi vị trí dữ liệu/model và người có quyền vào biên bản, không để ảnh/model trong git |
| [ ] | MySQL **8** qua Docker | Sau khi điền `.env`, chạy các lệnh ở mục MySQL; `SELECT VERSION()` trả 8.x |

Không cần chung token Mapillary hoặc PAT. Chỉ chia sẻ thư mục dữ liệu đã thống nhất.
Không đưa credential vào URL clone, notebook output hoặc ảnh chụp màn hình.

### File môi trường

```powershell
Copy-Item .env.example .env
git check-ignore .env
```

Điền `.env` bằng editor riêng trên máy: `MAPILLARY_TOKEN`, `VIETMAP_API_KEY`,
`MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`,
`MYSQL_PORT`, `MYSQL_URL`, `GITHUB_TOKEN`, `SHARED_DRIVE_URL`.
`.env` được Compose đọc để thay biến; PowerShell và Python **không tự nạp** `.env`.
Khi chạy tool hiện có, đặt token vào môi trường của terminal qua công cụ quản lý bí mật
hoặc nhập kín như sau (không hiển thị token):

```powershell
$mapillarySecret = Read-Host 'Mapillary token cá nhân' -AsSecureString
$env:MAPILLARY_TOKEN = [System.Net.NetworkCredential]::new('', $mapillarySecret).Password
$headers = @{ Authorization = "OAuth $env:MAPILLARY_TOKEN" }
$probe = Invoke-RestMethod -Uri 'https://graph.mapillary.com/images?fields=id&bbox=105.847,21.025,105.848,21.026&limit=1' -Headers $headers
$probe.PSObject.Properties.Name -contains 'data'
```

Kết quả `True` xác nhận truy vấn được; tập ảnh rỗng không chứng minh token sai.
Nếu HTTP lỗi, ghi mã lỗi, không in header/token.

Kiểm tra PAT trong Colab, dùng Secret được cấp cho notebook:

```python
from google.colab import userdata
import requests
r = requests.get(
    'https://api.github.com/repos/hana200804/livesign/contents/README.md',
    headers={'Authorization': 'Bearer ' + userdata.get('GITHUB_TOKEN')},
    timeout=30,
)
print(r.status_code)  # 200; không in token hoặc headers
```

Kiểm tra Vietmap có hai mức: `Test-Path android/local.properties` chỉ xác nhận
file cấu hình tồn tại; phải tải được bản đồ trong ví dụ SDK trên máy thật mới coi
key hoạt động. Không tạo project trong lần chuẩn bị này. Không mặc định quota miễn phí;
Kiểm tra quota/quyền SDK trên tài khoản thực tế.

### MySQL

Điền mật khẩu riêng trong `.env`, không để rỗng. Cổng mặc định 3306; nếu đổi
`MYSQL_PORT`, sửa cổng trong `MYSQL_URL` tương ứng.

```powershell
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d mysql
docker compose ps
docker compose exec mysql mysql -u livesign -p -D livesign -e 'SELECT VERSION(), DATABASE(), @@session.time_zone;'
```

Lệnh cuối hỏi mật khẩu tương tác; thay `livesign` bằng user/database đã chọn.
Compose đặt múi giờ SQL `+00:00`. DDL mới chỉ là bản nháp trong
[db-schema.md](contracts/db-schema.md), chưa tự nạp. `docker compose stop mysql`
dừng dịch vụ và giữ volume. Việc đổi mật khẩu `.env` không đổi credential trong volume
đã khởi tạo; xử lý dữ liệu hiện có trước khi tạo lại volume.

## Phần AI

- [ ] ` .\.venv\Scripts\python.exe -m pip install -r ai/requirements.txt` hoàn tất;
  PyTorch/CUDA chọn theo máy tại [PyTorch](https://pytorch.org/get-started/locally/).
- [ ] `.\.venv\Scripts\python.exe -c "import torch, ultralytics, yaml; print(torch.__version__, ultralytics.__version__); print(torch.cuda.is_available())"` import được; ghi rõ CPU/GPU.
- [ ] Trên Colab chọn GPU; `!nvidia-smi` thấy GPU, đọc được PAT, mount được Drive chung.
- [ ] Đọc [classes.md](contracts/classes.md), đối chiếu annotation/giấy phép từng phiên bản dataset;
  không dùng ánh xạ 7 nhóm Zalo cũ để tự sinh nhãn chi tiết.
- [ ] `Test-Path data/raw`; `Test-Path data/processed/yolo`; các thư mục dữ liệu lớn ở `data/`/Drive.
- [ ] Đọc [observation.md](contracts/observation.md); phân biệt confidence model và điểm đồng thuận.

## Phần Backend + Web

- [ ] JDK 21, Docker, Node đạt checklist chung; `docker compose ps` báo MySQL healthy.
- [ ] Đọc [api.md](contracts/api.md), [db-schema.md](contracts/db-schema.md),
  [params.yaml](../config/params.yaml); N/θ chưa chốt, không tự thay null bằng mặc định.
- [ ] Có thể đăng nhập SQL bằng tài khoản ứng dụng; không dùng root cho backend.
- [ ] Ghi nhận hợp đồng JWT, bcrypt và vai trò admin; chưa sinh project/controller/web UI.
- [ ] Xác nhận đường dẫn bằng chứng ảnh ngoài git và hợp đồng URL admin đọc được.

## Phần Android

- [ ] Android Studio, SDK Platform/Build Tools và Platform Tools cài được;
  `adb version`; `adb devices -l` thấy thiết bị thật ở trạng thái `device`.
- [ ] `adb shell getprop ro.build.version.sdk` trả **≥26**; chấp nhận hộp thoại USB debug trên điện thoại.
- [ ] Ghi model máy/Android/chip để đo FPS, độ trễ, pin; model TFLite để ở Drive/`data/models/`.
- [ ] Kiểm tra key Vietmap trên ví dụ SDK khi bắt đầu làm app;
  lưu key ở `android/local.properties`, không commit.
- [ ] Đọc [classes.md](contracts/classes.md), [api.md](contracts/api.md),
  [observation.md](contracts/observation.md): `car | truck | motorcycle`, góc độ, khoảng cách mét.

## Kết quả kiểm tra tại lần chuẩn bị 19/10/2026

Chỉ phản ánh máy đang thực hiện tài liệu: virtualenv có Python 3.12.10/PyYAML 6.0.3;
có Docker CLI nhưng chưa kết nối được Docker daemon. Chưa xác nhận JDK, Android Studio,
key, thiết bị thật hoặc quyền Drive của ba thành viên. Mỗi người tự xác nhận checklist.
