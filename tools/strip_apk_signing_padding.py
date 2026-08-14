#!/usr/bin/env python3
"""Remove apksigner's optional verity-padding pair without changing the v2 signature."""
import struct
import sys
from pathlib import Path

EOCD = b"PK\x05\x06"
MAGIC = b"APK Sig Block 42"
PADDING_ID = 0x42726577


def compact(data: bytes) -> bytes:
    eocd = data.rfind(EOCD)
    if eocd < 0 or eocd + 22 > len(data):
        raise ValueError("ZIP end record not found")
    central = struct.unpack_from("<I", data, eocd + 16)[0]
    if central < 32 or data[central - 16:central] != MAGIC:
        raise ValueError("APK signing block not found")
    size = struct.unpack_from("<Q", data, central - 24)[0]
    start = central - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        raise ValueError("APK signing block size mismatch")

    kept = bytearray()
    cursor = start + 8
    end = central - 24
    removed = False
    while cursor < end:
        pair_size = struct.unpack_from("<Q", data, cursor)[0]
        pair_end = cursor + 8 + pair_size
        if pair_size < 4 or pair_end > end:
            raise ValueError("invalid APK signing pair")
        pair_id = struct.unpack_from("<I", data, cursor + 8)[0]
        if pair_id == PADDING_ID:
            removed = True
        else:
            kept.extend(data[cursor:pair_end])
        cursor = pair_end
    if cursor != end:
        raise ValueError("truncated APK signing block")
    if not removed:
        return data

    new_size = len(kept) + 24
    block = struct.pack("<Q", new_size) + kept + struct.pack("<Q", new_size) + MAGIC
    tail = bytearray(data[central:])
    tail_eocd = tail.rfind(EOCD)
    struct.pack_into("<I", tail, tail_eocd + 16, start + len(block))
    return data[:start] + block + tail


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: strip_apk_signing_padding.py INPUT.apk OUTPUT.apk")
    source, target = map(Path, sys.argv[1:])
    target.write_bytes(compact(source.read_bytes()))


if __name__ == "__main__":
    main()
