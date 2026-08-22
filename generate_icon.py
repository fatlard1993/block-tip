#!/usr/bin/env python3
"""Generate Block Tip's mod menu icon: the card it draws.

The mod is one small card that appears when you look at something, so the icon is
that card - the same dark panel, the same block picture at the head of it, the
same two lines of text, and the green tick that says the tool in your hand will
do. Drawn in the card's own colours, read out of TipHud rather than picked again
here.

The panel is painted more solidly than the real one. On screen it sits over the
world at half black and is meant to be barely there; in a mod list it sits over
whatever the launcher's background happens to be, and barely there would be
unreadable.

Pure stdlib PNG reader and writer (zlib + struct), no Pillow, deterministic:
re-running produces identical bytes. Nearest neighbour throughout, never smoothed.

Usage: python3 generate_icon.py
"""

import glob
import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/block-tip/icon.png")

CANVAS = 32
SCALE = 4                        # 32 -> 128, the size the rest of the suite uses

PANEL = (20, 20, 20, 235)        # TipHud's panel, opaque enough for a list
NAME = (255, 255, 255, 255)
DETAIL = (160, 160, 160, 255)    # TipHud.DETAIL_COLOR
SOURCE = (122, 140, 168, 255)    # TipHud.SOURCE_COLOR
TICK = (85, 255, 85, 255)        # TipHud.MARK_OK_COLOR


def client_jar():
    jars = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/*/minecraft-client*.jar")), key=os.path.getmtime)
    if not jars:
        sys.exit("no Loom client jar cached - run a build first")
    return jars[-1]


def read_png(data):
    """Decode a non-interlaced 8-bit PNG to rows of RGBA tuples."""
    pos, idat, palette, trns = 8, b"", None, None
    width = height = depth = colour = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        pos += 12 + length

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    raw = zlib.decompress(idat)
    stride = width * channels
    out, prev = [], bytearray(stride)

    for y in range(height):
        head = y * (stride + 1)
        filt = raw[head]
        line = bytearray(raw[head + 1:head + 1 + stride])
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if filt == 1: line[i] = (line[i] + a) & 0xFF
            elif filt == 2: line[i] = (line[i] + b) & 0xFF
            elif filt == 3: line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        prev = line

        row = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if colour == 6: row.append(tuple(px))
            elif colour == 2: row.append((px[0], px[1], px[2], 255))
            elif colour == 3:
                i = px[0]
                alpha = trns[i] if trns and i < len(trns) else 255
                row.append((palette[i * 3], palette[i * 3 + 1], palette[i * 3 + 2], alpha))
            elif colour == 0: row.append((px[0], px[0], px[0], 255))
            else: row.append((px[0], px[0], px[0], px[1]))
        out.append(row)
    return out


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


def paste(canvas, source, region, at):
    x0, y0, w, h = region
    ax, ay = at
    for y in range(h):
        for x in range(w):
            px = source[y0 + y][x0 + x]
            if px[3] == 0:
                continue
            canvas[ay + y][ax + x] = px


def bar(canvas, x, y, width, height, colour):
    for row in range(height):
        for col in range(width):
            canvas[y + row][x + col] = colour


def build():
    with zipfile.ZipFile(client_jar()) as jar:
        block = read_png(jar.read("assets/minecraft/textures/block/grass_block_side.png"))

    # Halved by taking every other pixel: a clean 2:1 with no blending, and small
    # enough that the card is the subject rather than the block sitting on it.
    small = [[block[y * 2][x * 2] for x in range(8)] for y in range(8)]

    canvas = [[(0, 0, 0, 0)] * CANVAS for _ in range(CANVAS)]

    top, height = 9, 14
    bar(canvas, 0, top, CANVAS, height, PANEL)

    paste(canvas, small, (0, 0, 8, 8), (3, top + 3))

    # The tick rides the block's top-right corner, as it does on the card.
    # Two pixels thick, or at this size it reads as a stray diagonal rather than a mark.
    for x, y in ((9, top + 3), (10, top + 4), (11, top + 3), (12, top + 2), (13, top + 1),
                 (9, top + 2), (10, top + 3), (11, top + 2), (12, top + 1), (13, top + 0)):
        canvas[y][x] = TICK

    bar(canvas, 14, top + 3, 15, 2, NAME)     # the block's name
    bar(canvas, 14, top + 6, 7, 1, SOURCE)    # the mod it came from
    bar(canvas, 14, top + 8, 11, 2, DETAIL)   # what is odd about it

    return [[canvas[y // SCALE][x // SCALE] for x in range(CANVAS * SCALE)]
            for y in range(CANVAS * SCALE)]


if __name__ == "__main__":
    write_png(OUT, build())
