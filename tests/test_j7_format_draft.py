#!/usr/bin/env python3
"""Executable regression checks for the evidence-labelled J7 draft.

This deliberately validates only byte-level observations. It does not infer map
addresses, dimensions, scaling, or firmware compatibility.
"""
from __future__ import annotations

import hashlib
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "docs" / "J7_FORMAT_SPEC_DRAFT.md"
MANIFEST = ROOT / "tests" / "fixtures" / "j7_corpus_manifest.json"
MARKER = re.compile(rb"!([0-9]{3})-([0-9A-Fa-f]{4})(?:-([0-9A-Fa-f]{2}))?")


def inspect_descriptor(data: bytes) -> dict:
    """Return deterministic, read-only observations without semantic guesses."""
    matches = []
    for match in MARKER.finditer(data):
        matches.append({
            "offset": match.start(),
            "token": match.group(0).decode("ascii"),
            "prefix": match.group(1).decode("ascii"),
            "hex_field": match.group(2).decode("ascii"),
            "suffix": match.group(3).decode("ascii") if match.group(3) else None,
        })
    return {
        "size": len(data),
        "signature": data[:3].decode("ascii", errors="replace"),
        "markers": matches,
    }


class J7DraftTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    def test_spec_exists_and_labels_uncertainty(self):
        spec = SPEC.read_text(encoding="utf-8")
        self.assertIn("UNKNOWN", spec)
        self.assertIn("Not established", spec)
        self.assertIn("MUST NOT", spec)

    def test_manifest_entries_have_valid_metadata(self):
        self.assertGreaterEqual(len(self.manifest["candidates"]), 5)
        for item in self.manifest["candidates"]:
            with self.subTest(file=item["archive_path"]):
                self.assertEqual(item["signature"], "CTM")
                self.assertGreater(item["size"], 3)
                self.assertRegex(item["sha256"], r"^[0-9a-f]{64}$")
                self.assertGreater(item["marker_count"], 0)
                self.assertEqual(item["status"], "unpaired_candidate")

    def test_primary_fixture_fingerprint_is_documented(self):
        primary = self.manifest["primary_fixture"]
        self.assertEqual(primary["size"], 186401)
        self.assertEqual(
            primary["sha256"],
            "61cb9c36c95e4d046b6d33edc4f6e0863c37e13ee9a8bf75464289313a65e04a",
        )
        self.assertEqual(primary["marker_count"], 115)

    def test_parser_is_deterministic(self):
        data = b"CTM\x03\x01demo !001-613C-40 text !003-6064-60"
        before = bytes(data)
        first = inspect_descriptor(data)
        second = inspect_descriptor(data)
        self.assertEqual(first, second)
        self.assertEqual(data, before)

    def test_token_offsets_and_bytes_are_exact(self):
        data = b"prefix !001-613C-40\x00 !003-6064-60"
        result = inspect_descriptor(data)
        for item in result["markers"]:
            start = item["offset"]
            token = item["token"].encode("ascii")
            self.assertGreaterEqual(start, 0)
            self.assertLessEqual(start + len(token), len(data))
            self.assertEqual(data[start:start + len(token)], token)

    def test_suffixless_and_unknown_suffix_are_preserved(self):
        data = b"!001-ABCD !002-1234-FF !003-0000-40"
        found = [m["token"] for m in inspect_descriptor(data)["markers"]]
        self.assertEqual(found, ["!001-ABCD", "!002-1234-FF", "!003-0000-40"])
        self.assertIsNone(inspect_descriptor(data)["markers"][0]["suffix"])
        self.assertEqual(inspect_descriptor(data)["markers"][1]["suffix"], "FF")

    def test_malformed_and_truncated_data_does_not_crash(self):
        for data in (b"", b"CT", b"CTM", b"CTM!001-", b"!001-12", b"\xff\x00!001-ABCD-"):
            with self.subTest(data=data):
                result = inspect_descriptor(data)
                self.assertIsInstance(result["markers"], list)

    def test_signature_is_not_accepted_from_substring_alone(self):
        self.assertNotEqual(inspect_descriptor(b"junkCTMjunk")["signature"], "CTM")
        self.assertEqual(inspect_descriptor(b"CTMjunk")["signature"], "CTM")

    def test_every_marker_span_is_within_input(self):
        data = b"CTM" + (b"xx!001-613C-40yy" * 30)
        for item in inspect_descriptor(data)["markers"]:
            self.assertLessEqual(item["offset"] + len(item["token"]), len(data))

    def test_no_firmware_address_or_write_api_is_exposed(self):
        # This reference parser exposes lexical fields only; no address mapping or writes.
        result = inspect_descriptor(b"!001-613C-40")
        self.assertNotIn("firmware_address", result["markers"][0])
        self.assertNotIn("write", result)


if __name__ == "__main__":
    unittest.main(verbosity=2)
