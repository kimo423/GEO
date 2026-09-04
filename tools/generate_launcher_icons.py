from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "GEO-icon-original.png"
RES = ROOT / "app" / "src" / "main" / "res"
# 108dp at xxxhdpi. Safe zone is the inner 72dp so OEM masks do not crop the mark.
CANVAS = 432
SAFE = 288


def _resampling():
    return getattr(Image, "Resampling", Image).LANCZOS


def inset_on_canvas(source: Image.Image) -> Image.Image:
    logo = source.convert("RGBA").resize((SAFE, SAFE), _resampling())
    layer = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    offset = (CANVAS - SAFE) // 2
    layer.paste(logo, (offset, offset), logo)
    return layer


def write_adaptive_layers(original: Image.Image) -> None:
    drawable = RES / "drawable-xxxhdpi"
    drawable.mkdir(parents=True, exist_ok=True)
    foreground = inset_on_canvas(original)
    foreground.save(drawable / "ic_launcher_foreground.png", optimize=True)

    # White silhouette of the supplied mark for themed / monochrome launchers.
    gray = ImageOps.grayscale(original.convert("RGB"))
    alpha = gray.point(lambda p: 0 if p < 40 else min(255, (p - 40) * 2))
    mono = Image.new("RGBA", original.size, (255, 255, 255, 0))
    mono.putalpha(alpha)
    inset_on_canvas(mono).save(drawable / "ic_launcher_monochrome.png", optimize=True)


def main() -> None:
    with Image.open(SOURCE) as original:
        # Density mipmaps are left untouched so legacy launcher PNGs stay as shipped.
        write_adaptive_layers(original)


if __name__ == "__main__":
    main()
