"""Generate OS launcher icons from the approved Shadow RP artwork.

This script intentionally uses the supplied cropped photographic artwork,
not a generic Material glyph. Source artwork stays in the repository.
"""
from __future__ import annotations
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets" / "launcher.webp"
RES = ROOT / "app" / "src" / "main" / "res"
ICO = ROOT / "assets" / "ShadowRP.ico"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def outputs() -> list[tuple[Path, tuple[int, int]]]:
    return [(RES / ("mipmap-" + density) / "ic_launcher.png", (size, size))
            for density, size in SIZES.items()] + [
                (RES / "drawable-nodpi" / "ic_launcher_foreground.png", (432, 432))]


def build() -> None:
    with Image.open(SOURCE) as original:
        icon = original.convert("RGBA")
    if icon.size != (192, 192):
        raise ValueError(f"Expected a square 192px source icon: {icon.size}")
    for density, size in SIZES.items():
        target = RES / ("mipmap-" + density) / "ic_launcher.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        icon.resize((size, size), Image.Resampling.LANCZOS).save(target)
    # Android adaptive icons clip the outer 18dp: keep the artwork in safe zone.
    foreground = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    foreground.alpha_composite(icon.resize((288, 288), Image.Resampling.LANCZOS),
                               (72, 72))
    target = RES / "drawable-nodpi" / "ic_launcher_foreground.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    foreground.save(target)
    adaptive = RES / "mipmap-anydpi-v26" / "ic_launcher.xml"
    adaptive.parent.mkdir(parents=True, exist_ok=True)
    adaptive.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">'
        '<background android:drawable="@color/veyrion_icon_background"/>'
        '<foreground android:drawable="@drawable/ic_launcher_foreground"/>'
        '</adaptive-icon>\n', encoding="utf-8")
    values = RES / "values" / "veyrion_icon_colors.xml"
    values.parent.mkdir(parents=True, exist_ok=True)
    values.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources><color name="veyrion_icon_background">#050A1A</color></resources>\n',
        encoding="utf-8")
    ICO.parent.mkdir(parents=True, exist_ok=True)
    icon.convert("RGB").resize((256, 256), Image.Resampling.LANCZOS).save(
        ICO, format="ICO", sizes=[(16, 16), (24, 24), (32, 32),
                                 (48, 48), (64, 64), (128, 128), (256, 256)])


def verify() -> None:
    with Image.open(SOURCE) as im:
        assert im.size == (192, 192), "Source artwork must be 192x192"
    for path, size in outputs():
        with Image.open(path) as im:
            assert im.size == size, f"{path} wrong size: {im.size}"
    with Image.open(ICO) as im:
        assert (256, 256) in im.ico.sizes(), "Missing 256px Windows icon"
        assert (16, 16) in im.ico.sizes(), "Missing 16px Windows icon"
    assert (RES / "mipmap-anydpi-v26" / "ic_launcher.xml").is_file()


if __name__ == "__main__":
    if "--verify" in sys.argv:
        verify()
    else:
        build()
        verify()
