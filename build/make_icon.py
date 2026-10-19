"""Generates res/icon.png - a small iPod silhouette. No image libraries needed."""
import zlib, struct, math

S = 32
BG = (0, 0, 0, 0)

def rounded(x, y, x0, y0, x1, y1, r):
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    for cx, cy in ((x0 + r, y0 + r), (x1 - r, y0 + r), (x0 + r, y1 - r), (x1 - r, y1 - r)):
        if ((x < x0 + r) == (cx == x0 + r)) and ((y < y0 + r) == (cy == y0 + r)):
            if (x < x0 + r or x > x1 - r) and (y < y0 + r or y > y1 - r):
                if (x - cx) ** 2 + (y - cy) ** 2 > r * r:
                    return False
    return True

px = [[BG for _ in range(S)] for _ in range(S)]
for y in range(S):
    for x in range(S):
        if rounded(x, y, 4, 1, 27, 30, 4):
            # body: light silver, slightly shaded down the page
            v = 236 - int(26 * y / S)
            px[y][x] = (v, v, v, 255)
        # outline
for y in range(S):
    for x in range(S):
        if px[y][x][3] and not (rounded(x, y, 5, 2, 26, 29, 3)):
            px[y][x] = (140, 140, 146, 255)

# screen
for y in range(5, 16):
    for x in range(8, 24):
        if 8 <= x <= 23 and 5 <= y <= 15:
            t = (y - 5) / 10.0
            r = int(70 + 90 * t); g = int(120 + 100 * t); b = int(200 + 45 * t)
            px[y][x] = (r, g, b, 255)
for x in range(8, 24):
    px[4][x] = (90, 92, 98, 255)

# click wheel
cx, cy, R, r = 15.5, 23.0, 7.2, 2.4
for y in range(S):
    for x in range(S):
        d = math.hypot(x - cx, y - cy)
        if d <= R and px[y][x][3]:
            px[y][x] = (248, 248, 250, 255)
        if abs(d - R) < 0.8 and px[y][x][3]:
            px[y][x] = (168, 168, 174, 255)
        if d <= r:
            px[y][x] = (222, 222, 228, 255)
        if abs(d - r) < 0.7:
            px[y][x] = (160, 160, 166, 255)

raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y][x]) for x in range(S)) for y in range(S))

def chunk(tag, data):
    c = struct.pack(">I", len(data)) + tag + data
    return c + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw, 9))
       + chunk(b"IEND", b""))
open("res/icon.png", "wb").write(png)
print("res/icon.png", len(png), "bytes")
