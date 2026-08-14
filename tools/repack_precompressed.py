#!/usr/bin/env python3
"""Replace one ZIP member with a verified raw-DEFLATE stream."""

import argparse
import binascii
import struct
import zipfile
import zlib
from pathlib import Path

from deflate_raw import best_raw_deflate


def dos_time(info: zipfile.ZipInfo) -> tuple[int, int]:
    year, month, day, hour, minute, second = info.date_time
    return (hour << 11) | (minute << 5) | (second // 2), ((year - 1980) << 9) | (month << 5) | day


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("entry")
    parser.add_argument("deflate", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    replacement = args.deflate.read_bytes()
    with zipfile.ZipFile(args.apk) as source:
        records = []
        for info in source.infolist():
            data = source.read(info)
            if info.filename == args.entry:
                if zlib.decompress(replacement, -15) != data:
                    raise SystemExit("precompressed stream does not match ZIP entry")
                compressed = replacement
                method = zipfile.ZIP_DEFLATED
            elif info.compress_type == zipfile.ZIP_STORED:
                compressed = data
                method = zipfile.ZIP_STORED
            else:
                compressed = best_raw_deflate(data)
                method = zipfile.ZIP_DEFLATED
            records.append((info, data, compressed, method))

    central = []
    with args.output.open("wb") as output:
        for info, data, compressed, method in records:
            name = info.filename.encode("utf-8")
            flags = 0x800 if any(byte >= 128 for byte in name) else 0
            timestamp, datestamp = dos_time(info)
            crc = binascii.crc32(data) & 0xFFFFFFFF
            offset = output.tell()
            output.write(struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, flags, method,
                                     timestamp, datestamp, crc, len(compressed), len(data), len(name), 0))
            output.write(name)
            output.write(compressed)
            central.append((name, flags, method, timestamp, datestamp, crc,
                            len(compressed), len(data), info.external_attr, offset))

        central_offset = output.tell()
        for name, flags, method, timestamp, datestamp, crc, compressed_size, size, attrs, offset in central:
            output.write(struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 0x0314, 20, flags,
                                     method, timestamp, datestamp, crc, compressed_size, size,
                                     len(name), 0, 0, 0, 0, attrs, offset))
            output.write(name)
        central_size = output.tell() - central_offset
        count = len(central)
        output.write(struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, count, count,
                                 central_size, central_offset, 0))


if __name__ == "__main__":
    main()
