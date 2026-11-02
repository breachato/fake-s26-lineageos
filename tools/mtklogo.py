"""Unpack / repack a MediaTek logo partition (logo.bin).

Layout: 512-byte MTK header (magic 0x58881688, payload size, name "LOGO"),
then: u32 count, u32 payload_size, u32 offsets[count] (relative to payload),
each entry a zlib stream of raw pixels.
"""
import os
import struct
import sys
import zlib

MAGIC = 0x58881688


def read_entries(path):
    data = open(path, "rb").read()
    magic, size = struct.unpack_from("<II", data, 0)
    assert magic == MAGIC, hex(magic)
    name = data[8:40].split(b"\0")[0]
    payload = data[512:512 + size]
    count, total = struct.unpack_from("<II", payload, 0)
    offs = list(struct.unpack_from(f"<{count}I", payload, 8))
    ends = offs[1:] + [total]
    entries = [zlib.decompress(payload[o:e]) for o, e in zip(offs, ends)]
    return data[:512], name, entries


def write_logo(header, entries, out_path, part_size=None):
    comp = [zlib.compress(e, 9) for e in entries]
    table = 8 + 4 * len(comp)
    offs, pos = [], table
    for c in comp:
        offs.append(pos)
        pos += len(c)
    payload = struct.pack("<II", len(comp), pos) + struct.pack(f"<{len(comp)}I", *offs) + b"".join(comp)
    hdr = bytearray(header)
    struct.pack_into("<I", hdr, 4, len(payload))
    blob = bytes(hdr) + payload
    if part_size:
        assert len(blob) <= part_size, f"logo too big: {len(blob)} > {part_size}"
    open(out_path, "wb").write(blob)
    return len(blob)


if __name__ == "__main__":
    header, name, entries = read_entries(sys.argv[1])
    print("name", name, "entries", len(entries))
    for i, e in enumerate(entries):
        print(i, len(e))
