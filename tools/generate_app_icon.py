"""Generate the Assistant app icon — flat vector style.

Produces a 1024x1024 icon with a deep charcoal background, an off-white
rounded speech bubble, and a single warm amber dot. Outputs both PNGs
for every Android density bucket so the launcher can pick the right one.

Run from the project root:
    python tools/generate_app_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"

SIZE = 1024  # source canvas size (Android adaptive icon foreground)
BG = (26, 26, 31, 255)          # deep charcoal
BUBBLE = (245, 245, 247, 255)    # soft off-white
AMBER = (245, 158, 11, 255)      # warm amber accent
AMBER_GLOW = (245, 158, 11, 70)   # softer amber for halo


def draw_icon(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), BG)
    draw = ImageDraw.Draw(img)

    # Speech bubble: a horizontal pill (wider than tall) with rounded ends.
    # Width-to-height ratio ~1.4:1, centered horizontally, shifted slightly up
    # to leave room for the tail.
    pill_w = int(size * 0.62)
    pill_h = int(size * 0.50)
    cx = size // 2
    cy = size // 2
    pill_x0 = cx - pill_w // 2
    pill_y0 = cy - pill_h // 2 - int(size * 0.02)
    pill_x1 = cx + pill_w // 2
    pill_y1 = pill_y0 + pill_h
    pill_radius = pill_h // 2  # full pill (semicircle ends)
    draw.rounded_rectangle(
        [pill_x0, pill_y0, pill_x1, pill_y1],
        radius=pill_radius,
        fill=BUBBLE,
    )

    # Tail — a small triangle fused into the bottom-right of the pill.
    tail_w = int(size * 0.10)
    tail_h = int(size * 0.10)
    tail = [
        (pill_x1 - tail_w * 1.6, pill_y1 - tail_h * 0.3),
        (pill_x1 + tail_w * 0.2, pill_y1 - tail_h * 0.1),
        (pill_x1 - tail_w * 1.2, pill_y1 + tail_h * 0.8),
    ]
    draw.polygon(tail, fill=BUBBLE)

    # Amber dot — perfectly centered in the bubble.
    dot_r = int(size * 0.105)
    draw.ellipse(
        [cx - dot_r, cy - dot_r - int(size * 0.02), cx + dot_r, cy + dot_r - int(size * 0.02)],
        fill=AMBER,
    )

    # Soft amber halo behind the dot for a "listening" feel.
    halo_r = int(dot_r * 1.9)
    halo = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse(
        [cx - halo_r, cy - halo_r - int(size * 0.02),
         cx + halo_r, cy + halo_r - int(size * 0.02)],
        fill=AMBER_GLOW,
    )
    img = Image.alpha_composite(img, halo)

    # Redraw the dot on top of the halo so it stays sharp.
    ImageDraw.Draw(img).ellipse(
        [cx - dot_r, cy - dot_r - int(size * 0.02), cx + dot_r, cy + dot_r - int(size * 0.02)],
        fill=AMBER,
    )

    return img


def make_foreground(size: int) -> Image.Image:
    """Adaptive-icon foreground — transparent BG, glyph centered."""
    src = draw_icon(size)
    mask = Image.new("L", src.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size, size], radius=int(size * 0.22), fill=255
    )
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(src, (0, 0), mask)
    return out


def main() -> None:
    fg = make_foreground(SIZE)
    fg.save(RES / "drawable" / "ic_launcher_foreground.png")

    bg_img = Image.new("RGBA", (SIZE, SIZE), BG)
    bg_img.save(RES / "drawable" / "ic_launcher_background.png")

    full = draw_icon(SIZE)
    (RES / "mipmap-xxxhdpi").mkdir(parents=True, exist_ok=True)
    full.save(RES / "mipmap-xxxhdpi" / "ic_launcher.png")

    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""
    )
    (anydpi / "ic_launcher_round.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""
    )

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for dirname, dim in densities.items():
        d = RES / dirname
        d.mkdir(parents=True, exist_ok=True)
        resized = fg.resize((dim, dim), Image.LANCZOS)
        resized.save(d / "ic_launcher.png")
        resized.save(d / "ic_launcher_round.png")

    print("Generated app icon at all density buckets.")


if __name__ == "__main__":
    main()
