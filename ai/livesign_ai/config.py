"""Đọc classes.yaml và params.yaml. Đường dẫn tính từ gốc repo, không phụ thuộc thư mục đang chạy."""

import hashlib
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]
CLASSES_YAML = REPO / "ai" / "configs" / "classes.yaml"
DATASET_YAML = REPO / "ai" / "configs" / "dataset.yaml"
PARAMS_YAML = REPO / "config" / "params.yaml"


def load_yaml(path):
    return yaml.safe_load(Path(path).read_text(encoding="utf-8"))


def load_classes(path=CLASSES_YAML):
    return load_yaml(path)


def load_params(path=PARAMS_YAML):
    return load_yaml(path)


def param(params, dotted):
    """Giá trị `value` của tham số dạng "nhóm.tên"; None nếu chưa chốt."""
    node = params
    for key in dotted.split("."):
        node = node[key]
    return node["value"] if isinstance(node, dict) and "value" in node else node


def code_to_id(classes_cfg):
    return {code: cid for cid, code in classes_cfg["classes"].items()}


def file_sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()
