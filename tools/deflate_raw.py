#!/usr/bin/env python3
"""Create the smallest deterministic raw-DEFLATE stream available locally."""

import ctypes
import ctypes.util
import sys
import zlib
from pathlib import Path

def libdeflate_candidates(data, consider):
    """Use libdeflate's denser maximum levels when the build host provides it."""
    library = ctypes.util.find_library("deflate")
    if not library:
        return
    try:
        native = ctypes.CDLL(library)
        native.libdeflate_alloc_compressor.argtypes = [ctypes.c_int]
        native.libdeflate_alloc_compressor.restype = ctypes.c_void_p
        native.libdeflate_deflate_compress_bound.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
        native.libdeflate_deflate_compress_bound.restype = ctypes.c_size_t
        native.libdeflate_deflate_compress.argtypes = [
            ctypes.c_void_p,
            ctypes.c_void_p,
            ctypes.c_size_t,
            ctypes.c_void_p,
            ctypes.c_size_t,
        ]
        native.libdeflate_deflate_compress.restype = ctypes.c_size_t
        native.libdeflate_free_compressor.argtypes = [ctypes.c_void_p]
        source_buffer = ctypes.create_string_buffer(data)
        for compression_level in range(10, 13):
            compressor = native.libdeflate_alloc_compressor(compression_level)
            if not compressor:
                continue
            try:
                bound = native.libdeflate_deflate_compress_bound(compressor, len(data))
                output_buffer = ctypes.create_string_buffer(bound)
                size = native.libdeflate_deflate_compress(
                    compressor, source_buffer, len(data), output_buffer, bound
                )
                if size:
                    consider(output_buffer.raw[:size])
            finally:
                native.libdeflate_free_compressor(compressor)
    except (AttributeError, OSError):
        return

def best_raw_deflate(payload):
    best = None

    def consider(candidate):
        nonlocal best
        if candidate and (best is None or len(candidate) < len(best)):
            best = candidate

    # memLevel changes zlib's match-finder shape, not just memory usage. DEX files regularly
    # compress smaller below the default, so measure a bounded deterministic search.
    for compression_level in range(6, 10):
        for memory_level in range(5, 10):
            compressor = zlib.compressobj(
                compression_level,
                zlib.DEFLATED,
                -15,
                memory_level,
                zlib.Z_DEFAULT_STRATEGY,
            )
            consider(compressor.compress(payload) + compressor.flush())

    libdeflate_candidates(payload, consider)
    return best


def main():
    source, output = map(Path, sys.argv[1:3])
    output.write_bytes(best_raw_deflate(source.read_bytes()))


if __name__ == "__main__":
    main()
