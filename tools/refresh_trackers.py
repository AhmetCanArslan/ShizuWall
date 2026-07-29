import argparse
import json
import os
import sys
import urllib.request

API = "https://reports.exodus-privacy.eu.org/api/trackers"
ASSET = os.path.join("app", "src", "main", "assets", "trackers.json")


def split_signature(raw):
    return sorted({s.strip().strip(".") for s in (raw or "").split("|") if len(s.strip().strip(".")) > 3})


def fetch():
    with urllib.request.urlopen(API, timeout=60) as response:
        if response.status != 200:
            sys.exit("HTTP %s from %s" % (response.status, API))
        return json.load(response)["trackers"]


def trim(raw_trackers):
    out = []
    for entry in raw_trackers.values():
        signatures = split_signature(entry.get("code_signature"))
        if not signatures:
            continue
        out.append({
            "id": entry["id"],
            "name": entry["name"],
            "categories": entry.get("categories") or [],
            "website": entry.get("website") or "",
            "signatures": signatures,
        })
    out.sort(key=lambda t: t["id"])
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="report changes without writing")
    args = parser.parse_args()

    if not os.path.isfile(ASSET):
        sys.exit("run me from the repo root (missing %s)" % ASSET)

    with open(ASSET, encoding="utf-8") as f:
        old = {t["id"]: t for t in json.load(f)["trackers"]}

    new_list = trim(fetch())
    new = {t["id"]: t for t in new_list}

    added = [new[i]["name"] for i in sorted(set(new) - set(old))]
    removed = [old[i]["name"] for i in sorted(set(old) - set(new))]
    changed = [new[i]["name"] for i in sorted(set(old) & set(new))
               if old[i]["signatures"] != new[i]["signatures"]]

    print("upstream: %d definitions (was %d)" % (len(new), len(old)))
    for label, names in (("added", added), ("removed", removed), ("signatures changed", changed)):
        print("  %s: %s" % (label, ", ".join(names) if names else "none"))

    if args.dry_run:
        print("dry run, nothing written")
        return
    if not (added or removed or changed):
        print("no changes")
        return

    payload = {
        "version": 1,
        "source": API,
        "license": "ODbL-1.0",
        "license_url": "https://opendatacommons.org/licenses/odbl/1-0/",
        "attribution": "Tracker definitions derived from the Exodus Privacy database, "
                       "licensed under ODbL v1.0. https://exodus-privacy.eu.org/",
        "trackers": new_list,
    }
    with open(ASSET, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    print("wrote %s" % ASSET)


if __name__ == "__main__":
    main()
