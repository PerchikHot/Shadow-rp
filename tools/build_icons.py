"""Generate consistent premium Shadow RP launcher art for Android/Windows.

The vector-style hood, blue eyes and chat emblem are rendered by Pillow;
the old red-eyed purple placeholder is deliberately not used.
"""
from __future__ import annotations
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets" / "launcher.webp"
RES = ROOT / "app" / "src" / "main" / "res"
ICO = ROOT / "assets" / "ShadowRP.ico"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def render_hood_icon() -> Image.Image:
    """Render at 4x resolution for crisp adaptive and Windows icon sizes."""
    side = 768
    canvas = Image.new("RGBA", (side, side), (4, 12, 28, 255))
    draw = ImageDraw.Draw(canvas)
    for y in range(side):
        t = y / side
        draw.line((0, y, side, y),
                  fill=(int(5 + 5*t), int(17 + 13*t), int(38 + 23*t), 255))
    def box(x0, y0, x1, y1):
        return tuple(round(v * side / 108) for v in (x0, y0, x1, y1))
    def poly(points):
        return [(round(x * side / 108), round(y * side / 108))
                for x, y in points]
    # Blue atmospheric halo and a thin luminous frame.
    halo = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    h = ImageDraw.Draw(halo)
    h.ellipse(box(12, 10, 97, 101), fill=(22, 129, 231, 135))
    canvas = Image.alpha_composite(canvas, halo.filter(ImageFilter.GaussianBlur(65)))
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(box(4, 4, 104, 104), radius=round(side*0.16),
                           outline=(77, 175, 246, 210), width=9)
    draw.rounded_rectangle(box(8, 8, 100, 100), radius=round(side*0.14),
                           outline=(19, 74, 126, 240), width=6)
    # Cloak and deep hood silhouette.
    draw.polygon(poly([(7,108),(16,83),(34,70),(30,51),(32,31),
                       (42,17),(55,12),(69,19),(80,33),(79,52),
                       (74,70),(91,83),(102,108)]), fill=(19, 59, 103, 255))
    draw.polygon(poly([(13,108),(22,83),(39,72),(34,50),(38,32),
                       (54,22),(69,33),(75,51),(68,72),(86,84),
                       (95,108)]), fill=(6, 21, 44, 255))
    draw.ellipse(box(34, 29, 75, 81), fill=(2, 10, 24, 255))
    draw.arc(box(31, 20, 78, 75), start=194, end=347,
             fill=(39, 111, 172, 245), width=14)
    draw.polygon(poly([(17,108),(33,78),(45,85),(54,99),
                       (67,84),(82,78),(95,108)]), fill=(27, 81, 137, 255))
    # Glow is a separate blurred layer, keeping the eyes readable at 48 px.
    eyes = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    ed = ImageDraw.Draw(eyes)
    ed.ellipse(box(32, 47, 52, 61), fill=(45, 203, 255, 235))
    ed.ellipse(box(57, 47, 77, 61), fill=(45, 203, 255, 235))
    canvas = Image.alpha_composite(canvas, eyes.filter(ImageFilter.GaussianBlur(22)))
    draw = ImageDraw.Draw(canvas)
    draw.polygon(poly([(35,52),(44,49),(51,54),(46,57),(39,57)]),
                 fill=(116, 231, 255, 255))
    draw.polygon(poly([(58,54),(66,49),(74,52),(70,57),(62,57)]),
                 fill=(116, 231, 255, 255))
    draw.ellipse(box(43, 52, 46, 55), fill=(241, 255, 255, 255))
    draw.ellipse(box(65, 52, 68, 55), fill=(241, 255, 255, 255))
    # Speech bubble at the lower-right: distinct from the character's eyes.
    draw.rounded_rectangle(box(74, 74, 100, 94), radius=18,
                           fill=(72, 195, 255, 255))
    draw.polygon(poly([(80,91),(80,101),(91,91)]), fill=(72, 195, 255, 255))
    draw.rounded_rectangle(box(77, 77, 97, 90), radius=12,
                           fill=(8, 37, 71, 255))
    draw.line(poly([(81,83),(93,83)]), fill=(177, 243, 255, 255), width=7)
    return canvas.resize((192, 192), Image.Resampling.LANCZOS)

def outputs() -> list[tuple[Path, tuple[int, int]]]:
    return [(RES / ("mipmap-" + density) / "ic_launcher.png", (size, size))
            for density, size in SIZES.items()] + [
                (RES / "drawable-nodpi" / "ic_launcher_foreground.png", (432, 432))]


def build() -> None:
    icon = render_hood_icon()
    # Keep the generated source alongside the other build artifacts so the
    # installed APK and Windows EXE always share identical icon pixels.
    icon.save(SOURCE, format="WEBP", quality=96)
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
