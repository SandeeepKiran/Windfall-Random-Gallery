# Capture frames of index.html with headless Chromium. Each frame = render(t).
# Usage: python render.py stills 1.5 4.0 ...   |   python render.py full
import sys, os, pathlib
from playwright.sync_api import sync_playwright

HERE = pathlib.Path(__file__).parent
FPS, DUR = 30, 24.0
mode = sys.argv[1]
with sync_playwright() as p:
    browser = p.chromium.launch()
    try:
        page = browser.new_page(viewport={"width": 1920, "height": 1080})
        page.goto((HERE / "index.html").as_uri())
        page.evaluate("window.ready")
        if mode == "stills":
            out = HERE / "stills"; out.mkdir(exist_ok=True)
            for ts in sys.argv[2:]:
                page.evaluate(f"render({float(ts)})")
                page.screenshot(path=str(out / f"s_{float(ts):05.2f}.jpg"), type="jpeg", quality=85)
        else:
            out = HERE / "frames"; out.mkdir(exist_ok=True)
            for f in range(int(DUR * FPS)):
                page.evaluate(f"render({f / FPS})")
                page.screenshot(path=str(out / f"f{f:04d}.jpg"), type="jpeg", quality=95)
    finally:
        browser.close()
print("done")
