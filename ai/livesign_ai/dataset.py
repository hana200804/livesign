"""Đọc dataset nguồn về một dạng chung và áp ánh xạ nhãn sang mã QCVN.

Hỗ trợ hai bố cục YOLO hay gặp: `images/<split>/x.jpg` + `labels/<split>/x.txt`
(Kaggle) và `<split>/images/x.jpg` + `<split>/labels/x.txt` (Roboflow export).
Tên lớp gốc lấy từ file yaml có khóa `names` trong thư mục nguồn.
"""

from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
SPLIT_ALIASES = {"train": "train", "val": "val", "valid": "val", "validation": "val", "test": "test"}


@dataclass
class Obj:
    orig_label: str
    yolo: tuple  # (cx, cy, w, h) chuẩn hóa, giữ nguyên như nguồn


@dataclass
class ImageRec:
    source: str
    image_path: Path
    label_path: Path | None
    orig_split: str | None
    objects: list = field(default_factory=list)
    rel: str = ""  # đường dẫn trong nguồn, bỏ thư mục `images` và đuôi file

    @property
    def image_key(self):
        """Khóa duy nhất trong nguồn. Không dùng tên file trần: nguồn có thể có cùng tên
        ở nhiều split (bộ Kaggle có 270 ảnh test/val chép y hệt ảnh train cùng tên)."""
        return f"{self.source}:{self.rel or self.image_path.stem}"

    @property
    def export_name(self):
        return self.image_key.replace(":", "__").replace("/", "__")


def parse_names(names):
    """`names` của yaml Ultralytics có thể là list hoặc dict {id: tên}."""
    if isinstance(names, dict):
        return {int(k): str(v) for k, v in names.items()}
    return {i: str(n) for i, n in enumerate(names)}


def parse_yolo_label(text, names):
    """Một dòng `class cx cy w h`. Dòng sai định dạng báo lỗi, không âm thầm bỏ."""
    objs = []
    for n, line in enumerate(text.splitlines(), 1):
        parts = line.split()
        if not parts:
            continue
        if len(parts) != 5:
            raise ValueError(f"dòng {n}: cần 5 cột, có {len(parts)}")
        cid = int(parts[0])
        if cid not in names:
            raise ValueError(f"dòng {n}: class {cid} không có trong names")
        objs.append(Obj(names[cid], tuple(float(v) for v in parts[1:])))
    return objs


def label_path_for(image_path):
    """Thay thư mục `images` gần nhất bằng `labels`, đuôi thành `.txt`."""
    parts = list(image_path.parts)
    for i in range(len(parts) - 2, -1, -1):
        if parts[i] == "images":
            parts[i] = "labels"
            return Path(*parts).with_suffix(".txt")
    return None


def split_from_path(path, root):
    for part in path.relative_to(root).parts:
        if part.lower() in SPLIT_ALIASES:
            return SPLIT_ALIASES[part.lower()]
    return None


def find_names(root, load_yaml):
    for p in sorted(Path(root).rglob("*.yaml")):
        data = load_yaml(p)
        if isinstance(data, dict) and "names" in data:
            return parse_names(data["names"])
    raise FileNotFoundError(f"không thấy yaml có `names` trong {root}")


def read_yolo_source(source, root, load_yaml):
    root = Path(root)
    names = find_names(root, load_yaml)
    recs = []
    for img in sorted(p for p in root.rglob("*") if p.suffix.lower() in IMAGE_EXTS and "images" in p.parts):
        lab = label_path_for(img)
        objs = parse_yolo_label(lab.read_text(encoding="utf-8"), names) if lab and lab.exists() else []
        rel = "/".join(p for p in img.relative_to(root).with_suffix("").parts if p != "images")
        recs.append(ImageRec(source, img, lab if lab and lab.exists() else None, split_from_path(img, root), objs, rel))
    return recs


def map_record(rec, mapping, ignore, code_to_id):
    """Đổi nhãn gốc của một ảnh sang class_id.

    - Nhãn trong `mapping` → giữ với class_id tương ứng.
    - Nhãn trong `ignore` (chắc chắn ngoài 12 lớp) → bỏ object, ảnh vẫn dùng được.
    - Nhãn khác (chưa ánh xạ, có thể lẫn lớp đích như "no car entry/turning") → bỏ
      **cả ảnh**, vì giữ lại sẽ dạy model coi một biển đích là nền.

    Trả về (list (class_id, yolo) hoặc None nếu loại ảnh, Counter nhãn bị bỏ).
    """
    kept, dropped, unsafe = [], Counter(), False
    for o in rec.objects:
        code = mapping.get(o.orig_label)
        if code is not None:
            kept.append((code_to_id[code], o.yolo))
            continue
        dropped[o.orig_label] += 1
        if o.orig_label not in ignore:
            unsafe = True
    return (None if unsafe else kept), dropped


def count_labels(recs):
    """Counter theo (nhãn gốc, split gốc) và số ảnh có nhãn đó."""
    objects, images = Counter(), Counter()
    for rec in recs:
        for label in {o.orig_label for o in rec.objects}:
            images[(label, rec.orig_split)] += 1
        for o in rec.objects:
            objects[(o.orig_label, rec.orig_split)] += 1
    return objects, images
