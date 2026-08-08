#!/bin/sh
# unwrap_snapshot.sh <year> <month> <day> <hhmm> [output.png]
#
# Finds the one matching image per satellite source for the given date/time
# and runs `unwrap` to composite all four into one canonical equirectangular
# image.
#
# Takes an explicit date, not a loosely-matching time-of-day fragment like the
# original version's "1200" -- with only a few weeks of archives, an hhmm alone
# is ambiguous about which day, and it never worked correctly for GOES anyway:
# each source encodes its timestamp in the filename differently, so a single
# shared pattern can't match all four. This script builds the right pattern
# per source instead:
#
#   himawari  : himawari_<year>-<month>-<day>_<hhmm>Z.png       (year-month-day, as-is)
#   meteosat  : msg_iodc_ir108_<year><month><day>_<hhmm>.png    (year-month-day, as-is)
#   goes_*    : <year><day-of-year, 3 digits><hhmm>_GOES...jpg  (year + JULIAN DAY, not month/day)
#
# Example:
#   ./unwrap_snapshot.sh 2026 08 06 1200
#   ./unwrap_snapshot.sh 2026 08 06 1200 canonical_20260806_1200z.png

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# ---------------------------------------------------------------------------
# Tunable paths -- override by editing the defaults below, or without editing
# the script at all by setting the same-named environment variable before
# invoking it, e.g.:
#   GOES_EAST_DIR=/path/to/goes_fetcher/goes_19_timelapse ./unwrap_snapshot.sh ...
# Defaults below match this checkout's actual samples/ layout; point these
# elsewhere (e.g. a differently-organized archive directory) as needed.
# ---------------------------------------------------------------------------
JAR="${JAR:-$SCRIPT_DIR/target/globe-wrapping-tool-2.0-SNAPSHOT-jar-with-dependencies.jar}"
CONFIG="${CONFIG:-$SCRIPT_DIR/globe-wrapping-tool.yaml}"
GOES_EAST_DIR="${GOES_EAST_DIR:-$SCRIPT_DIR/samples/goes_east/images}"
GOES_WEST_DIR="${GOES_WEST_DIR:-$SCRIPT_DIR/samples/goes_west/images}"
HIMAWARI_DIR="${HIMAWARI_DIR:-$SCRIPT_DIR/samples/himawari/images}"
METEOSAT_DIR="${METEOSAT_DIR:-$SCRIPT_DIR/samples/meteosat/images}"
# ---------------------------------------------------------------------------

if [ "$#" -lt 4 ]; then
    echo "Usage: $0 <year> <month> <day> <hhmm> [output.png]" >&2
    echo "  e.g. $0 2026 08 06 1200" >&2
    exit 1
fi

YEAR="$1"
RAW_MONTH="$2"
RAW_DAY="$3"
HHMM="$4"
OUTPUT="${5:-}"

# Zero-pad month/day to 2 digits (accept "8" or "08" either way). Strip any
# existing leading zero via parameter expansion (not arithmetic) first --
# `$((10#$x))` base-prefix notation is a bash/ksh extension, not POSIX, and
# this script targets plain /bin/sh (dash on most Linux systems), where a
# leading-zero value like "08" would otherwise be misparsed as octal.
MM=$(printf '%02d' "${RAW_MONTH#0}")
DD=$(printf '%02d' "${RAW_DAY#0}")

[ -n "$OUTPUT" ] || OUTPUT="canonical_${YEAR}${MM}${DD}_${HHMM}.png"

# Day-of-year (Julian day), 3 digits zero-padded -- needed for the GOES
# filename pattern below, which encodes year+day-of-year+hhmm, NOT year-month-day.
DOY=$(date -u -d "${YEAR}-${MM}-${DD}" +"%j" 2>/dev/null) || true
if [ -z "${DOY:-}" ]; then
    DOY=$(date -u -j -f "%Y-%m-%d" "${YEAR}-${MM}-${DD}" +"%j" 2>/dev/null) || true
fi
if [ -z "${DOY:-}" ]; then
    echo "Could not compute day-of-year for ${YEAR}-${MM}-${DD} -- check date/coreutils availability." >&2
    exit 1
fi

if [ ! -f "$JAR" ]; then
    echo "Jar not found at $JAR -- run 'mvn package' first." >&2
    exit 1
fi

# find_one <dir> <pattern> -- locates exactly one file directly under <dir>
# whose name starts with <pattern>, erroring clearly if none or more than one match.
find_one() {
    dir="$1"
    pattern="$2"
    set -- "$dir"/"$pattern"*
    if [ "$#" -eq 0 ] || [ ! -e "$1" ]; then
        echo "No file matching '${pattern}*' found under $dir/" >&2
        exit 1
    fi
    if [ "$#" -gt 1 ]; then
        echo "Multiple files matching '${pattern}*' found under $dir/, need exactly one:" >&2
        printf '  %s\n' "$@" >&2
        exit 1
    fi
    printf '%s\n' "$1"
}

GOES_PATTERN="${YEAR}${DOY}${HHMM}_GOES"
HIMAWARI_PATTERN="himawari_${YEAR}-${MM}-${DD}_${HHMM}Z"
METEOSAT_PATTERN="msg_iodc_ir108_${YEAR}${MM}${DD}_${HHMM}"

GOES_EAST_IMAGE=$(find_one "$GOES_EAST_DIR" "$GOES_PATTERN")
GOES_WEST_IMAGE=$(find_one "$GOES_WEST_DIR" "$GOES_PATTERN")
HIMAWARI_IMAGE=$(find_one "$HIMAWARI_DIR" "$HIMAWARI_PATTERN")
METEOSAT_IMAGE=$(find_one "$METEOSAT_DIR" "$METEOSAT_PATTERN")

echo "goes19 (goes_east):  $GOES_EAST_IMAGE"
echo "goes18 (goes_west):  $GOES_WEST_IMAGE"
echo "himawari9:           $HIMAWARI_IMAGE"
echo "meteosat0:            $METEOSAT_IMAGE"

java -jar "$JAR" unwrap "$OUTPUT" \
    --config "$CONFIG" \
    goes19 "$GOES_EAST_IMAGE" \
    goes18 "$GOES_WEST_IMAGE" \
    himawari9 "$HIMAWARI_IMAGE" \
    meteosat0 "$METEOSAT_IMAGE"

echo "Wrote $OUTPUT"
