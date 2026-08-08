#!/bin/sh
# make_timelapse_frames.sh <year> <month> <day> <start_hhmm> <end_hhmm> <step_minutes> <lat,lon> [size]
#
# Batch-generates a numbered sequence of `wrap` frames -- one per timestamp in
# [start_hhmm, end_hhmm] stepped by <step_minutes> -- by running the full
# unwrap -> combine -> wrap pipeline for each. A starting point for building a
# time-lapse video externally (e.g. with ffmpeg -- this project deliberately
# doesn't do video assembly itself, see globe-unwrapper-requirements.md
# section 2).
#
# This is a convenience/example script, not a core deliverable: most uses of
# the tool are a single `unwrap`/`combine`/`wrap` invocation. This exists to
# show what stringing them together into a batch looks like.
#
# Resumable: skips any frame whose output file already exists, so an
# interrupted or partially-completed run can just be re-invoked as-is.
#
# Example (every 10 minutes from 0000 to 0040 on 2026-08-01, centered over
# the eastern Pacific):
#   ./make_timelapse_frames.sh 2026 08 01 0000 0040 10 30,-140

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# ---------------------------------------------------------------------------
# Tunable paths -- override by editing the defaults below, or via environment
# variable without editing the script (same pattern as unwrap_snapshot.sh).
# ---------------------------------------------------------------------------
JAR="${JAR:-$SCRIPT_DIR/target/globe-wrapping-tool-2.0-SNAPSHOT-jar-with-dependencies.jar}"
UNWRAP_SNAPSHOT_SCRIPT="${UNWRAP_SNAPSHOT_SCRIPT:-$SCRIPT_DIR/unwrap_snapshot.sh}"
BASEMAP="${BASEMAP:-$SCRIPT_DIR/earth_equirectilinear_projection.jpg}"
OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR/output}"
WORK_DIR="${WORK_DIR:-$SCRIPT_DIR}"
# ---------------------------------------------------------------------------

if [ "$#" -lt 7 ]; then
    echo "Usage: $0 <year> <month> <day> <start_hhmm> <end_hhmm> <step_minutes> <lat,lon> [size]" >&2
    echo "  e.g. $0 2026 08 01 0000 0040 10 30,-140" >&2
    exit 1
fi

YEAR="$1"
MONTH="$2"
DAY="$3"
START_HHMM="$4"
END_HHMM="$5"
STEP_MINUTES="$6"
CENTER="$7"
SIZE="${8:-1024x1024}"

case "$START_HHMM" in
    [0-9][0-9][0-9][0-9]) ;;
    *) echo "start_hhmm must be exactly 4 digits (HHMM): $START_HHMM" >&2; exit 1 ;;
esac
case "$END_HHMM" in
    [0-9][0-9][0-9][0-9]) ;;
    *) echo "end_hhmm must be exactly 4 digits (HHMM): $END_HHMM" >&2; exit 1 ;;
esac
case "$STEP_MINUTES" in
    ''|*[!0-9]*) echo "step_minutes must be a positive integer: $STEP_MINUTES" >&2; exit 1 ;;
    0|00|000) echo "step_minutes must be greater than zero" >&2; exit 1 ;;
esac

if [ ! -x "$UNWRAP_SNAPSHOT_SCRIPT" ]; then
    echo "unwrap_snapshot.sh not found or not executable at $UNWRAP_SNAPSHOT_SCRIPT" >&2
    exit 1
fi
if [ ! -f "$JAR" ]; then
    echo "Jar not found at $JAR -- run 'mvn package' first." >&2
    exit 1
fi
if [ ! -f "$BASEMAP" ]; then
    echo "Basemap not found at $BASEMAP" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Zero-pad month/day for the output filename's date label. Strip any existing
# leading zero via parameter expansion first (not arithmetic) -- see
# unwrap_snapshot.sh's comment on why `$((10#$x))` isn't usable under plain
# /bin/sh (dash).
MM=$(printf '%02d' "${MONTH#0}")
DD=$(printf '%02d' "${DAY#0}")
DATE_LABEL="${YEAR}${MM}${DD}"

# hhmm (e.g. "0040") -> minutes since midnight, avoiding the same leading-zero
# arithmetic pitfall.
to_minutes() {
    hhmm="$1"
    hh="${hhmm%??}"
    mm="${hhmm#??}"
    hh="${hh#0}"
    mm="${mm#0}"
    echo $(( hh * 60 + mm ))
}

START_MIN=$(to_minutes "$START_HHMM")
END_MIN=$(to_minutes "$END_HHMM")
STEP_MINUTES="${STEP_MINUTES#0}"

if [ "$START_MIN" -gt "$END_MIN" ]; then
    echo "start_hhmm ($START_HHMM) is after end_hhmm ($END_HHMM) -- nothing to do" >&2
    exit 1
fi

canonical="$WORK_DIR/canonical.png"
combined="$WORK_DIR/combined.png"

minute=$START_MIN
while [ "$minute" -le "$END_MIN" ]; do
    hh=$(( minute / 60 ))
    mm=$(( minute % 60 ))
    hhmm=$(printf '%02d%02d' "$hh" "$mm")

    output_file="$OUTPUT_DIR/${DATE_LABEL}_${hhmm}.jpg"
    if [ ! -f "$output_file" ]; then
        echo "*** $hhmm ***"
        "$UNWRAP_SNAPSHOT_SCRIPT" "$YEAR" "$MONTH" "$DAY" "$hhmm" "$canonical"
        java -jar "$JAR" combine "$BASEMAP" "$canonical" "$combined"
        java -jar "$JAR" wrap "$combined" center "$CENTER" size "$SIZE" "$output_file"
    fi

    minute=$((minute + STEP_MINUTES))
done

echo "Done. Frames in $OUTPUT_DIR/"
