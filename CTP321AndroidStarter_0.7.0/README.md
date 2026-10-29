# CTP Mobile Lab 0.8.2

Android prototype for local Январь 7.2 / J7ES firmware inspection.

## Implemented
- Open BIN/ROM and .j7 calibration descriptor files
- File size and SHA-256
- HEX preview
- Byte-by-byte file comparison
- Save a copy of the firmware
- Edit one byte by hexadecimal file offset and save an edited copy
- Inspect J7 descriptor address-marker candidates and view the corresponding BIN HEX region
- Browse a clearly labelled reference catalog of J7ES calibration addresses from the public `cmgt/j7es` disassembly

## Important limitations
- .j7 names are currently extracted as text fragments; this is not a validated calibration-table parser.
- Calibration values, axes, dimensions and scaling are not decoded yet; marker addresses remain candidates until validated against CTP321 output.
- Public reference addresses are from a separate disassembly and must not be applied to the user's firmware until version and address mapping are verified.
- Byte editing does not recalculate ECU checksums or validate firmware compatibility. Never flash a modified file until its format and checksum requirements have been verified.
- This build is a development step, not a complete replacement for ChipTuningPRO/CTP321.

## Research references
- Public J7ES disassembly and calibration comments: https://github.com/cmgt/j7es/blob/master/j7es_data_area.asm
- Janvar 7.2 programming notes (including calibration protection/checksum caveats): https://auto-scaner.com/ecu_help/combildr/j7prog.htm
- J7ES feature and tuning workflow overview: https://ru.scribd.com/document/623558625/Perekhod-na-sportivnuyu-proshivku-j7es-dlya-kontrollera-Yanvar-7-2-Microsoft-Office-Word
- Community overview of tuning tools and map files: https://ivanglazunov.ru/repair/kak-pereproshit-mozgi-na-vaz-2114-samomu.html

The public assembly listing is useful for understanding table names, declared dimensions and units, but it is not a verified map pack for every J7ES/J7ESA version. In particular, do not treat a displayed ASM address as a BIN file offset until the target firmware's memory layout and compression state are confirmed. The app intentionally does not write calibration cells yet.
