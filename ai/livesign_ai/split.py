"""Chia train/val/test theo nhóm trùng, không chia ngẫu nhiên từng frame.

Quy tắc mỗi nguồn (`rules[source]`):
- `"keep"`: giữ split gốc của nguồn;
- `"train"` / `"val"` / `"test"`: cả nguồn vào một split (vd. giữ trọn nguồn ngoài train);
- `{"by_group": {"train": 0.8, "val": 0.1, "test": 0.1}}`: chia theo hash của group id.

Một nhóm trùng chỉ ở một split. Nhóm có ảnh rơi vào nhiều split thì chuyển cả nhóm
vào train (loại khỏi val/test để không rò), ghi vào danh sách ngoại lệ.
"""

import hashlib

SPLITS = ("train", "val", "test")


def _by_group(group_id, ratios):
    total = sum(ratios.values())
    u = int(hashlib.sha256(group_id.encode()).hexdigest()[:12], 16) / 16**12
    acc = 0.0
    for s in SPLITS:
        acc += ratios.get(s, 0) / total
        if u < acc:
            return s
    return SPLITS[-1]


def proposed_split(item, rules):
    rule = rules[item["source"]]
    if rule == "keep":
        if item["orig_split"] not in SPLITS:
            raise ValueError(f"{item['key']}: nguồn dùng 'keep' nhưng không có split gốc")
        return item["orig_split"]
    if rule in SPLITS:
        return rule
    if isinstance(rule, dict) and "by_group" in rule:
        return _by_group(item["group_id"], rule["by_group"])
    raise ValueError(f"quy tắc chia không hợp lệ cho {item['source']}: {rule!r}")


def assign_splits(items, rules):
    """`items`: list dict có key, source, orig_split, group_id.

    Trả về (dict key → split, list ngoại lệ {group_id, proposed, assigned, keys}).
    """
    proposed = {it["key"]: proposed_split(it, rules) for it in items}
    by_group = {}
    for it in items:
        by_group.setdefault(it["group_id"], []).append(it["key"])
    final, exceptions = {}, []
    for gid, keys in sorted(by_group.items()):
        splits = sorted({proposed[k] for k in keys})
        target = splits[0] if len(splits) == 1 else "train"
        if len(splits) > 1:
            exceptions.append({"group_id": gid, "proposed": splits, "assigned": target, "keys": sorted(keys)})
        for k in keys:
            final[k] = target
    return final, exceptions


def groups_cross_splits(split_by_key, group_by_key):
    """Các group id nằm ở hơn một split; rỗng nghĩa là chia đúng quy tắc."""
    seen = {}
    for k, s in split_by_key.items():
        seen.setdefault(group_by_key[k], set()).add(s)
    return sorted(g for g, s in seen.items() if len(s) > 1)
