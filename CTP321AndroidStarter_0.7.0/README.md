# CTP Mobile Lab 0.8.0

Android prototype for local Январь 7.2 / J7ES firmware inspection.

## Implemented
- Open BIN/ROM and .j7 calibration descriptor files
- File size and SHA-256
- HEX preview
- Byte-by-byte file comparison
- Save a copy of the firmware
- Edit one byte by hexadecimal file offset and save an edited copy

## Important limitations
- .j7 names are currently extracted as text fragments; this is not a validated calibration-table parser.
- Calibration values, axes, dimensions, scaling and firmware addresses are not decoded yet.
- Byte editing does not recalculate ECU checksums or validate firmware compatibility. Never flash a modified file until its format and checksum requirements have been verified.
- This build is a development step, not a complete replacement for ChipTuningPRO/CTP321.
