# samples/

This directory holds real satellite imagery used for manual testing and as example input for
`unwrap_snapshot.sh`/`make_timelapse_frames.sh`. The image files themselves are not checked into
version control (see the top-level `.gitignore`) — only this directory structure is.

To populate it, obtain a full-disk image from each of the following satellites, making sure all
four captures are from the **same** date/time (so the resulting composite doesn't show a seam
between day and night, or mismatched cloud patterns), and place a copy of each in its
corresponding `images/` folder below:

- **GOES-19** ("GOES East") → `goes_east/images/`
- **GOES-18** ("GOES West") → `goes_west/images/`
- **Himawari 9** → `himawari/images/`
- **Meteosat** — the satellite centered over the prime meridian (0° service position, sometimes
  referred to as "Meteosat IODC's 0° neighbor" depending on provider naming) → `meteosat/images/`

Any recent full-disk capture works; there's no requirement to use a specific provider or exact
filename format — `globe-wrapping-tool.yaml` and `unwrap_snapshot.sh` will need to know each
alias's calibration and how to find each file by date, so check those before scripting bulk
downloads.
