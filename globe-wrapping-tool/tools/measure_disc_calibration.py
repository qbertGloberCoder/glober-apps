#!/usr/bin/env python3
"""
measure_disc_calibration.py — estimate DiscCalibration fractions from a source image.

Detects a full-disk satellite image's disc boundary directly from pixel content
and prints nadir_x/nadir_y/radius_x/radius_y (see globe-unwrapper-requirements.md
section 4) ready to paste into globe-unwrap.yaml, instead of guessing values or
hand-measuring pixel offsets in an image editor.

Two detection strategies, tried in order:

1. Connected-component (primary): find the largest bright region touching the
   image's own geometric center pixel, take its bounding box. Works for ordinary
   full-disk composites where the disc — day or night side — is actually drawn.
   Requires scipy.

2. Limb circle-fit (fallback, used when the center pixel isn't part of any
   bright region — e.g. a crescent-only render where the unlit hemisphere is
   left undrawn rather than filled in, as with the himawari9 sample encountered
   in tasks.md): trace the topmost bright pixel per column and the leftmost
   bright pixel per row, fit a circle to each trace independently via an
   algebraic (Kasa) least-squares fit, then average the two fits. Two
   independent traces agreeing closely is itself a good sanity check that the
   fit is trustworthy.

KNOWN LIMITATION — full-width UI chrome (caption bars, logos): both strategies
can be thrown off by non-disc bright content that spans a large fraction of the
frame width/height, e.g. a caption bar at the bottom of NOAA GOES imagery. For
the goes19/goes18/meteosat0 samples this didn't matter in practice (their true
disc crop is already so close to the frame edges — see globe-unwrap.yaml's
comments — that the bar's bounding-box contribution was negligible), but don't
assume that holds for every source. Sanity-check the printed numbers against the
image by eye, or against a rendered `unwrap` output, before trusting them blindly
— this script accelerates the "empirically tedious, iterative" calibration
process globe-unwrapper-requirements.md section 4 describes, it doesn't replace
the sanity check.

Usage:
    python3 tools/measure_disc_calibration.py <image> [<image> ...] [--threshold N]

Requires: pillow, numpy, and (for the primary strategy) scipy.
    pip install pillow numpy scipy
"""

import argparse
import sys

import numpy as np
from PIL import Image

try:
    from scipy import ndimage

    HAVE_SCIPY = True
except ImportError:
    HAVE_SCIPY = False


def load_brightness(path):
    image = Image.open(path).convert("RGB")
    width, height = image.size
    array = np.asarray(image).astype(int)
    return width, height, array.sum(axis=2)  # sum of R+G+B, range 0-765


def connected_component_bbox(brightness, width, height, thresholds=(10, 6, 3, 1)):
    """Bounding box of the largest bright region connected to the center pixel, or None."""
    if not HAVE_SCIPY:
        return None
    for threshold in thresholds:
        mask = brightness > threshold
        labels, _ = ndimage.label(mask)
        center_label = labels[height // 2, width // 2]
        if center_label == 0:
            continue
        component = labels == center_label
        if component.sum() > (width * height) * 0.05:
            rows = np.any(component, axis=1)
            cols = np.any(component, axis=0)
            y0, y1 = np.where(rows)[0][[0, -1]]
            x0, x1 = np.where(cols)[0][[0, -1]]
            return x0, y0, x1, y1, threshold
    return None


def kasa_circle_fit(points):
    """Algebraic (Kasa) least-squares circle fit through a set of (x, y) points."""
    pts = np.asarray(points, dtype=float)
    x, y = pts[:, 0], pts[:, 1]
    coefficients = np.column_stack([2 * x, 2 * y, np.ones_like(x)])
    target = x**2 + y**2
    solution, *_ = np.linalg.lstsq(coefficients, target, rcond=None)
    a, b, c = solution
    r = np.sqrt(c + a**2 + b**2)
    return a, b, r


def limb_circle_fit(brightness, width, height, threshold):
    """Fits a circle to the disc's outer limb via independent top-edge and left-edge traces."""
    mask = brightness > threshold
    cols_with_bright = np.where(mask.any(axis=0))[0]
    rows_with_bright = np.where(mask.any(axis=1))[0]
    if len(cols_with_bright) == 0 or len(rows_with_bright) == 0:
        return None

    top_points = [(x, np.where(mask[:, x])[0].min()) for x in cols_with_bright]
    left_points = [(np.where(mask[y, :])[0].min(), y) for y in rows_with_bright]

    top_fit = kasa_circle_fit(top_points)
    left_fit = kasa_circle_fit(left_points)

    cx = (top_fit[0] + left_fit[0]) / 2.0
    cy = (top_fit[1] + left_fit[1]) / 2.0
    r = (top_fit[2] + left_fit[2]) / 2.0
    return cx, cy, r, top_fit, left_fit


def measure(path, threshold):
    width, height, brightness = load_brightness(path)

    bbox = connected_component_bbox(brightness, width, height)
    if bbox is not None:
        x0, y0, x1, y1, used_threshold = bbox
        cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        rx, ry = (x1 - x0) / 2.0, (y1 - y0) / 2.0
        print(f"# {path} ({width}x{height}) -- connected-component detection (threshold={used_threshold})")
        print(f"nadir_x: {cx / width:.4f}")
        print(f"nadir_y: {cy / height:.4f}")
        print(f"radius_x: {rx / width:.4f}")
        print(f"radius_y: {ry / height:.4f}")
        return

    print(
        f"# {path} ({width}x{height}) -- center pixel isn't part of a solid bright region "
        "(e.g. a crescent/partial render); falling back to limb circle-fit",
        file=sys.stderr,
    )
    result = limb_circle_fit(brightness, width, height, threshold)
    if result is None:
        print(f"# {path}: could not detect any content above brightness threshold {threshold}", file=sys.stderr)
        return
    cx, cy, r, top_fit, left_fit = result
    print(f"# {path} ({width}x{height}) -- limb circle-fit (top-edge + left-edge trace, averaged)")
    print(f"#   top-edge fit:  center=({top_fit[0]:.1f},{top_fit[1]:.1f}) r={top_fit[2]:.1f}")
    print(f"#   left-edge fit: center=({left_fit[0]:.1f},{left_fit[1]:.1f}) r={left_fit[2]:.1f}")
    print(f"nadir_x: {cx / width:.4f}")
    print(f"nadir_y: {cy / height:.4f}")
    print(f"radius_x: {r / width:.4f}")
    print(f"radius_y: {r / height:.4f}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("images", nargs="+", help="one or more full-disk satellite image paths")
    parser.add_argument(
        "--threshold",
        type=int,
        default=15,
        help="brightness threshold (sum of R+G+B, 0-765) for the limb-fit fallback (default: 15)",
    )
    args = parser.parse_args()

    for path in args.images:
        measure(path, args.threshold)
        print()


if __name__ == "__main__":
    main()
