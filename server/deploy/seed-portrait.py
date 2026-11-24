#!/usr/bin/python3 -I
"""A made-up portrait for the TEST accounts' profile photos (seed-test.sh): a head and shoulders on a gradient,
as a PNG, without any library. Usage: seed-portrait.py BACKGROUND SKIN SHIRT HAIR OUT.png  (colors as RRGGBB)."""
import struct
import sys
import zlib

SIZE = 512


def rgb(text):
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4))


def main():
    background, skin, shirt, hair = (rgb(c) for c in sys.argv[1:5])
    out = sys.argv[5]
    rows = []
    c = SIZE / 2
    for y in range(SIZE):
        row = bytearray([0])
        for x in range(SIZE):
            t = y / SIZE
            color = tuple(int(v * (1.15 - 0.45 * t)) for v in background)
            # shoulders
            if ((x - c) / 210) ** 2 + ((y - 560) / 200) ** 2 <= 1:
                color = shirt
            # neck
            if abs(x - c) < 38 and 290 < y < 380:
                color = tuple(int(v * 0.9) for v in skin)
            # head
            if ((x - c) / 92) ** 2 + ((y - 220) / 112) ** 2 <= 1:
                color = skin
            # hair
            if ((x - c) / 100) ** 2 + ((y - 185) / 95) ** 2 <= 1 and y < 190:
                color = hair
            # eyes
            for ex in (c - 34, c + 34):
                if (x - ex) ** 2 + (y - 225) ** 2 <= 64:
                    color = (40, 32, 28)
            # smile
            if 255 < y < 272 and ((x - c) / 34) ** 2 + ((y - 245) / 26) ** 2 <= 1 and ((x - c) / 30) ** 2 + ((y - 240) / 22) ** 2 > 1:
                color = (150, 70, 60)
            row += bytes(max(0, min(255, v)) for v in color)
        rows.append(bytes(row))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b"")
    with open(out, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    main()
