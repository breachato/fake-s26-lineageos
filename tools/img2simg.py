"""Convert a raw ext4 image to Android sparse format (minimal img2simg)."""
import os
import struct
import sys

BLK = 4096
MAX_RAW_BLOCKS = 4096  # 16 MiB per RAW chunk
CHUNK_RAW, CHUNK_DONT_CARE = 0xCAC1, 0xCAC3


def main(src, dst):
    size = os.path.getsize(src)
    assert size % BLK == 0, "image size not a multiple of 4096"
    total = size // BLK
    chunks = []  # (type, start_block, nblocks)
    zero = bytes(BLK)
    with open(src, "rb") as f:
        cur_type, start, n = None, 0, 0
        for i in range(total):
            t = CHUNK_DONT_CARE if f.read(BLK) == zero else CHUNK_RAW
            if t == cur_type and not (t == CHUNK_RAW and n >= MAX_RAW_BLOCKS):
                n += 1
                continue
            if cur_type is not None:
                chunks.append((cur_type, start, n))
            cur_type, start, n = t, i, 1
        chunks.append((cur_type, start, n))

    with open(src, "rb") as f, open(dst, "wb") as out:
        out.write(struct.pack("<IHHHHIIII", 0xED26FF3A, 1, 0, 28, 12, BLK, total, len(chunks), 0))
        for t, s, n in chunks:
            if t == CHUNK_RAW:
                out.write(struct.pack("<HHII", t, 0, n, 12 + n * BLK))
                f.seek(s * BLK)
                out.write(f.read(n * BLK))
            else:
                out.write(struct.pack("<HHII", t, 0, n, 12))
    raw = sum(n for t, _, n in chunks if t == CHUNK_RAW)
    print(f"blocks={total} chunks={len(chunks)} raw_blocks={raw} out={os.path.getsize(dst)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
