"""Lọc trùng giữa các nguồn: sha256 cho trùng tuyệt đối, pHash 64 bit cho gần trùng.

Cặp pHash gần chỉ là ứng viên; người xem xác nhận rồi mới gom nhóm (ai-pipeline.md).
Hash lưu dạng int 64 bit để so bằng XOR + đếm bit, không cần numpy.
"""


def hamming(a, b):
    return (a ^ b).bit_count()


def candidate_pairs(hashes, max_distance):
    """`hashes`: list (key, phash_int). Trả về list (key_a, key_b, d) với d ≤ max_distance.

    So mọi cặp O(n²): vài nghìn ảnh chạy trong vài giây, đủ cho dataset của đồ án.
    """
    out = []
    for i in range(len(hashes)):
        ka, ha = hashes[i]
        for j in range(i + 1, len(hashes)):
            kb, hb = hashes[j]
            d = (ha ^ hb).bit_count()
            if d <= max_distance:
                out.append((ka, kb, d))
    return out


class UnionFind:
    def __init__(self):
        self.parent = {}

    def find(self, x):
        self.parent.setdefault(x, x)
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a, b):
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            # Gốc là key nhỏ nhất để ID nhóm không phụ thuộc thứ tự cặp.
            lo, hi = sorted((ra, rb))
            self.parent[hi] = lo


def duplicate_groups(keys, same_pairs):
    """Gán `duplicate_group_id` cho mọi key. Ảnh không trùng ai có nhóm riêng.

    `same_pairs`: các cặp đã xác nhận cùng ảnh (sha256 trùng hoặc người duyệt "yes").
    ID nhóm = `dup:<key nhỏ nhất trong nhóm>`, ổn định khi chạy lại.
    """
    uf = UnionFind()
    for k in keys:
        uf.find(k)
    for a, b in same_pairs:
        uf.union(a, b)
    return {k: f"dup:{uf.find(k)}" for k in keys}


def exact_duplicate_pairs(sha_by_key):
    first = {}
    pairs = []
    for key, sha in sorted(sha_by_key.items()):
        if sha in first:
            pairs.append((first[sha], key))
        else:
            first[sha] = key
    return pairs


def phash_int(image):
    """pHash 64 bit của ảnh PIL đã xoay EXIF. Import imagehash trễ để test không cần."""
    import imagehash

    return int(str(imagehash.phash(image, hash_size=8)), 16)
