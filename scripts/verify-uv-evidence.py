"""Verify Lenovo TB520FU captures for the UV-preserving patch (Pillow)."""
from pathlib import Path
from PIL import Image, ImageChops
root = Path(__file__).resolve().parents[1] / 'evidence' / 'uv-preserving'
im = Image.open(root / 'masked.png').convert('RGB')
assert im.size == (2944, 1840)
rows = [y for y in range(736, 1380) if any(max(im.getpixel((x, y))) < 100 for x in range(1236, 1736))]
groups = []
for y in rows:
    if not groups or y > groups[-1][-1] + 10:
        groups.append([])
    groups[-1].append(y)
heights = [g[-1] - g[0] + 1 for g in groups]
assert heights == [83, 83], heights
region = (0, 215, 2944, 1770)
a = Image.open(root / 'unmasked-stock.png').convert('RGB').crop(region)
b = Image.open(root / 'unmasked-patched.png').convert('RGB').crop(region)
assert ImageChops.difference(a, b).getbbox() is None
print('Masked label heights: 83px / 83px; unmasked map pixel-identical')
