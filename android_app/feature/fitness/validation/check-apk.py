"""Verify generated APK assets against catalog paths and the pinned source inventory."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("debug_apk", type=Path)
    parser.add_argument("--release-apk", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    inventory = json.loads((root / "content/fitness/sources/free-exercise-db/images.json").read_text(encoding="utf-8"))

    def verify_content(path, label):
        with ZipFile(path) as apk:
            names = set(apk.namelist())
            catalog = json.loads(apk.read("assets/fitness/catalog.json"))
            assert catalog["sourceCount"] == len(catalog["sourceRecords"]) == 876
            assert len(catalog["exercises"]) == 16 and catalog["reviewedCount"] == 0
            references = {image["path"] for item in catalog["sourceRecords"] + catalog["exercises"] for image in item["images"]}
            missing = sorted("assets/fitness/" + path for path in references if "assets/fitness/" + path not in names)
            assert not missing, f"Missing catalog image assets: {missing[:5]}"
            source_names = {name for name in names if name.startswith("assets/fitness/source-media/") and not name.endswith("/")}
            expected = {"assets/fitness/source-media/" + image["path"] for image in inventory}
            assert source_names == expected, "Source assets contain missing/extra/nested files"
            for image in inventory:
                data = apk.read("assets/fitness/source-media/" + image["path"])
                assert len(data) == image["bytes"] and hashlib.sha256(data).hexdigest() == image["sha256"], image["path"]
            assert len([name for name in names if name.startswith("assets/fitness/muscles/") and name.endswith(".svg")]) == 17
            assert "assets/fitness/attributions/LICENSE.source.md" in names
            print(f"{label} APK: {len(references)} image paths, {len(inventory)} source SHA-256 hashes, attribution and 17 SVG paths verified.")

    verify_content(args.debug_apk, "Debug")
    if args.release_apk:
        release_settings = (root / "content/fitness/android/release.properties").read_text(encoding="utf-8")
        enabled = any(line.strip() == "enabled=true" for line in release_settings.splitlines())
        if enabled:
            verify_content(args.release_apk, "Release")
        else:
            with ZipFile(args.release_apk) as apk:
                assert not any(name.startswith("assets/fitness/") for name in apk.namelist()), "Disabled fitness content leaked into release"
            print("Release APK: fitness is disabled and no fitness assets are packaged.")
    print("Content consistency only; this does not approve exercise teaching or replace device tests.")


if __name__ == "__main__":
    main()
