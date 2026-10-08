"""Verify the checked-in Pixel 5a screenshots and input PNGs (requires Pillow)."""
from pathlib import Path
import json
from PIL import Image

root = Path(__file__).resolve().parents[1] / "evidence"
source = Image.open(root / "input/source.png").convert("RGBA")
stitch = Image.new("RGBA", source.size)
for x in range(2):
    for y in range(2):
        tile = Image.open(root / f"input/4-{7+x}-{7+y}.png").convert("RGBA")
        assert tile.size == (1024, 1024)
        stitch.paste(tile, (1024*x, 1024*y))
assert source.tobytes() == stitch.tobytes(), "Input PNGs have a discontinuity"


def heights(image):
    # Pixel 5a landscape capture: label-only region, excluding the ladder.
    pixels = image.load()
    rows = [y for y in range(600, 1000)
            if any(max(pixels[x, y]) < 100 for x in range(880, 1500))]
    groups = []
    for y in rows:
        if not groups or y > groups[-1][-1] + 10:
            groups.append([])
        groups[-1].append(y)
    assert len(groups) == 2
    return [group[-1] - group[0] + 1 for group in groups]


before = Image.open(root / "before.png").convert("RGB")
after = Image.open(root / "after.png").convert("RGB")
assert before.size == after.size == (2400, 1080)
before_heights, after_heights = heights(before), heights(after)
assert before_heights[0] < before_heights[1]
assert after_heights[0] == after_heights[1]
unmasked = [Image.open(root / f"unmasked-{name}.png").convert("RGB")
            .crop((127, 335, 2273, 1080)) for name in ("before", "after")]
assert unmasked[0].tobytes() == unmasked[1].tobytes(), "Unmasked map changed"
result = {"device": "Pixel 5a / Android 14 / arm64-v8a", "threshold": "all RGB < 100",
          "before_label_heights_px": before_heights, "after_label_heights_px": after_heights,
          "input_pngs_reconstruct_source_exactly": True,
          "unmasked_map_pixel_identical": True}
print(json.dumps(result, indent=2))
