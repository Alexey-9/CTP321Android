# J7 descriptor format — verifiable specification draft

Status: **DRAFT / partial, evidence-labelled**  
Scope: the user-supplied J7ES 0.7.5 descriptor and associated 64 KiB firmware. This document intentionally does not claim a complete format decode.

## 1. Corpus and reproducibility

The observations below refer to these exact input files:

| Role | File | Size | SHA-256 |
|---|---|---:|---|
| Calibration descriptor | `j7es_0.7.5.j7` | 186401 bytes | `61cb9c36c95e4d046b6d33edc4f6e0863c37e13ee9a8bf75464289313a65e04a` |
| Firmware | `72_1093_21116_j7esa_0_7_5_7281.bin` | 65536 bytes | `ed0258bbbf8a83dabfd785260468825f9e7794419e79ed9602dab9c5b3052b7a` |

Hash files before each experiment. If either hash differs, record a new corpus entry instead of silently reusing these conclusions.

## 2. Evidence levels

- **CONFIRMED**: directly reproducible from the listed bytes using the stated test.
- **OBSERVED**: true for this sample only; not yet proven as a format-wide rule.
- **HYPOTHESIS**: plausible interpretation requiring an independent fixture or controlled edit.
- **UNKNOWN**: no defensible interpretation yet.

## 3. Header and container

| Claim | Status | Evidence / test |
|---|---|---|
| Descriptor begins with ASCII bytes `43 54 4D` (`CTM`) | CONFIRMED for this fixture | Assert bytes 0..2 equal `CTM` |
| Descriptor length is 186401 bytes | CONFIRMED for this fixture | File-size check |
| Bytes 3..7 are `03 01 00 0f 51` | OBSERVED | Assert exact bytes; semantics unknown |
| Bytes 8..11 are `98 04 00 00` | OBSERVED | Assert exact bytes; do not infer meaning |
| Header fields encode version, offsets, or counts | UNKNOWN | Requires more samples and a validated layout |
| All strings use Windows-1251 | HYPOTHESIS | Many Russian labels decode plausibly as CP1251; mixed binary data means this is not proof of a global text encoding |

No field after the `CTM` signature is assigned a semantic name until cross-file tests support it.

## 4. Marker tokens

A byte scan of this fixture finds 115 ASCII tokens matching:

```regex
!([0-9]{3})-([0-9A-Fa-f]{4})(?:-([0-9A-Fa-f]{2}))?
```

Observed first examples include `!001-613C-40`, `!003-6064-60`, and `!004-6064-60`.

Observed frequency of the optional final two-hex-digit field:

| Suffix | Occurrences |
|---|---:|
| `40` | 65 |
| absent | 21 |
| `60` | 16 |
| `5E` | 13 |

Observed first three-digit prefix frequencies: `001`: 22, `002`: 43, `003`: 5, `004`: 22, `005`: 2, `006`: 20, `012`: 1.

**Not established:** whether the four-hex-digit field is a firmware address, descriptor-relative offset, identifier, or encoded value; whether the suffix denotes type, flags, width, or something else. In particular, the suffix MUST NOT be interpreted as map length without independent proof.

### Marker parser contract

1. Match the exact ASCII token pattern above; preserve original token and byte offset in the descriptor.
2. Parse numeric-looking fields only as lexical values (prefix string and hex strings).
3. Do not convert a token into a firmware address or map size in the UI.
4. Report token count and frequency deterministically.
5. Keep unknown suffixes and tokens without suffixes intact.
6. A malformed or truncated token is data, not a fatal parse error.

## 5. Calibration labels and records

The descriptor contains readable Russian labels, including labels resembling calibration/map names (for example, mixture composition at idle) and axis labels. This confirms that the file contains human-readable calibration metadata, but **does not** by itself establish where each map's values are stored, their dimensions, numeric representation, scaling, or link to firmware.

A label becomes a validated calibration record only when all of the following are independently established:

- stable record boundary and identifier;
- map dimensions / axis dimensions;
- value encoding, byte order, signedness and scaling;
- a mapping to a firmware region supported by evidence;
- controlled-edit or cross-version test that changes the expected data and leaves unrelated regions unchanged.

Until then, expose labels as descriptor text, not as editable map definitions.

## 6. Firmware assumptions

The paired BIN fixture is 65536 bytes. This establishes its size only. It does not prove that every marker field is a direct offset into this BIN, nor that all firmware builds use the same address mapping.

Any proposed direct address must pass:
- integer range check: `0 <= offset < firmware_size`;
- region range check: `offset + byte_length <= firmware_size`;
- independent evidence linking the marker to the region;
- consistency across at least one additional compatible firmware/descriptor pair.

No automatic patching should be enabled from a candidate address alone.

## 7. Required fixture matrix

The following data is needed to move from a sample-specific observation to a format rule:

| Fixture | Descriptor | Firmware | Controlled change |
|---|---|---|---|
| A | J7ES 0.7.5 | matching BIN | baseline |
| B | same descriptor copied byte-for-byte | same BIN | verifies parser determinism |
| C | compatible J7ES version | its matching BIN | identifies stable vs version-specific fields |
| D | same firmware with a calibration changed in a legitimate editor | before/after BIN and descriptor if available | ties one record to changed bytes |
| E | descriptor for another calibration set | matching BIN | tests generality and false positives |

Record provenance and SHA-256 for every fixture. Do not compare different firmware versions as though they were a controlled one-variable change.

## 8. Automated acceptance tests

A parser release is not format-validated until these tests pass:

1. **Signature:** fixture A starts with `CTM`.
2. **Stable input:** parsing the same bytes twice produces identical structured output.
3. **No mutation:** parsing never changes input bytes.
4. **Bounds:** every reported byte span lies entirely within the descriptor.
5. **Marker accounting:** fixture A yields 115 matches for the stated regex.
6. **Token preservation:** every reported token equals the original bytes at its recorded offset.
7. **Unknown preservation:** unknown fields survive parse → serialize byte-for-byte, or serialization is explicitly disabled.
8. **Negative fixture:** random/truncated input does not produce a valid descriptor merely because it contains the substring `CTM`.
9. **Address safety:** candidate addresses are never used to write firmware unless separately validated.
10. **Regression:** fixture hashes and expected observations are checked in CI; changed fixture hashes require an explicit review.

## 9. Safe implementation boundary

The first implementation should be a read-only forensic parser: header bytes, printable strings, marker tokens, offsets, counts, and confidence labels. It should not guess map dimensions or scaling and should not write firmware. Add semantic fields only with a test and fixture demonstrating their meaning.

## 10. Change log

- Initial draft: signature, fixture sizes and hashes, marker counts, and evidence labels recorded from the supplied J7ES 0.7.5 pair.
- All semantic interpretations remain provisional until corroborated by independent fixtures.
