# ai/ — Python: dataset, train, batch nhận diện

Quy trình nhánh/PR ở [CLAUDE.md gốc](../CLAUDE.md). Thiết kế: [docs/ai-pipeline.md](../docs/ai-pipeline.md).

- **Môi trường:** `.venv` ở gốc repo (Python 3.12). Lệnh train/batch chạy từ **gốc repo** để đường dẫn trong `configs/` và `data/` khớp.
- **Test trước khi mở PR:** `cd ai && ../.venv/Scripts/python -m pytest` (trên Linux/Colab: `cd ai && pytest`). CI chạy lại bộ test này. Test không được cần torch hay GPU. Phần nặng thì tách hàm thuần để test được.
- **Danh mục lớp:**
  - `configs/classes.yaml` là nguồn duy nhất. `dataset.yaml` chỉ là bản chiếu, và test bắt hai file phải khớp.
  - Đổi lớp nghĩa là đổi hợp đồng ([classes.md](../docs/contracts/classes.md)) và `classes_version`.
- **`livesign_ai/geometry.py`:** quy ước góc mà hợp đồng lần thấy đang dùng. Không đổi công thức nếu không sửa [observation.md](../docs/contracts/observation.md) trong cùng PR.
- **Đầu ra phải theo hợp đồng:**
  - Lần thấy: [observation.md](../docs/contracts/observation.md).
  - Gói model: [model.md](../docs/contracts/model.md), gồm tensor, letterbox và manifest.
- **Dữ liệu và model:**
  - Nằm trong `data/` theo [data/README.md](../data/README.md), không commit.
  - Kết quả đưa vào quyển để ở `results/` (CSV nhỏ), ghi kèm ngày, nguồn hoặc hash dataset và cấu hình.
- **Notebook:** xóa output lớn (ảnh, log dài) trước khi commit. Code dùng lại được thì chuyển vào `livesign_ai/` hoặc `scripts/`.
- **Token:** Mapillary đọc từ `MAPILLARY_TOKEN`. Trên Colab dùng Secrets, không ghi token vào notebook.
