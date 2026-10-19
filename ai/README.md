# ai — Nhận diện biển báo

Train YOLOv8n nhận diện biển báo Việt Nam, xuất TFLite cho app, và chạy nhận diện hàng loạt trên ảnh Mapillary để tạo "lần thấy" cho backend.

## Cấu trúc

```
ai/
  livesign_ai/          # code Python dùng chung, import được từ script và notebook
    geometry.py         #   góc nhìn của biển trong ảnh (tách từ PoC)
  configs/
    classes.yaml        # danh sách lớp đích + bảng gộp nhãn từ từng nguồn (bản nháp)
    dataset.yaml        # file dataset cho Ultralytics
  scripts/              # script chạy bằng dòng lệnh: chuẩn bị dataset, train, export, nhận diện hàng loạt
  notebooks/            # notebook Colab, đánh số theo thứ tự (00_, 01_, ...)
  results/              # số liệu nhỏ (CSV) và ảnh minh họa đưa vào quyển; KHÔNG để checkpoint ở đây
  requirements.txt
```

Dataset, ảnh Mapillary và checkpoint nằm trong `data/` ở gốc repo (không commit, xem [data/README.md](../data/README.md)).

## Môi trường

Hai chỗ chạy:

- **Máy cá nhân** (RTX 3050 4GB): chuẩn bị dataset, lọc trùng, thử nhanh, nhận diện hàng loạt. Train YOLOv8n được với `batch` nhỏ (8–16, ảnh 640).
- **Google Colab** (T4 16GB): train chính, thử ảnh lớn (1280–2048px).

Cài trên máy (Python 3.11 hoặc 3.12), chạy từ gốc repo:

```powershell
python -m venv .venv
.venv\Scripts\activate
# lệnh cài torch bản CUDA: chọn ở https://pytorch.org/get-started/locally/ (Windows, Pip, CUDA)
pip install -r ai/requirements.txt
```

Mọi lệnh chạy từ **gốc repo** để đường dẫn trong `configs/` và `data/` khớp nhau, ví dụ:

```powershell
yolo detect train data=ai/configs/dataset.yaml model=yolov8n.pt imgsz=640 batch=16 epochs=100 project=data/runs
```

Trên Colab: repo là private nên cần tạo một fine-grained token chỉ đọc repo này, lưu trong **Colab Secrets** với tên `GITHUB_TOKEN`, rồi clone:

```python
from google.colab import userdata
!git clone https://{userdata.get('GITHUB_TOKEN')}@github.com/hana200804/livesign.git
%cd livesign
!pip -q install -r ai/requirements.txt
```

## Dữ liệu train

- Zalo AI Challenge 2020 – Traffic Sign Detection (7 nhóm biển)
- Các bộ biển báo Việt Nam trên Roboflow / Kaggle

Gộp nhãn theo `configs/classes.yaml`. Lọc ảnh gần trùng (perceptual hash) **giữa các nguồn**, rồi chia train/val/test **theo nguồn** để ảnh gần giống nhau không lọt sang tập test.

## Định dạng file lần thấy (đầu vào của backend)

Thống nhất với Backend trước khi viết code. Gợi ý một dòng CSV:

```
image_id, sequence_id, creator_id, captured_at, lat, lon, compass_angle, sign_class, conf, bbox, outcome
```

`outcome`: `seen` | `not_seen` | `unknown`.

## Notebook

| Notebook | Nội dung |
| --- | --- |
| `00_visibility_poc.ipynb` | PoC: YOLO-World có sẵn thấy biển trên ảnh Mapillary được bao nhiêu. Dữ liệu từ `tools/poc/fetch-signs.mjs`, kết quả ở `tools/poc/results/` |
