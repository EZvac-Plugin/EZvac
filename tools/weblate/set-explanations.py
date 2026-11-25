"""Set Weblate's "Explanation" on EZvac's source strings from explanations.yml.

Usage (PowerShell), from the repository root:
    $env:WEBLATE_TOKEN = "<your personal API key from Weblate: Profile -> API access>"
    python tools/weblate/set-explanations.py            # dry run: shows what would change
    python tools/weblate/set-explanations.py --apply    # writes the changes

Only standard Python, nothing to install. The token is read from the environment, never
stored. Re-running is safe: strings already holding the same explanation are skipped.
Weblate keys use "->" where en.yml uses dots (screen.heat-to is screen->heat-to).
"""
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request

SERVER = os.environ.get("WEBLATE_URL", "https://hosted.weblate.org").rstrip("/")
PROJECT, COMPONENT, SOURCE = "ezvac", "plugin", "en"
HERE = pathlib.Path(__file__).resolve().parent
LINE = re.compile(r'^([a-z0-9][a-z0-9._-]*):\s*"(.*)"\s*$')


def explanations():
    """key (with dots) -> text, from the simple 'key: "text"' lines of explanations.yml."""
    out = {}
    for number, line in enumerate((HERE / "explanations.yml").read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        match = LINE.match(line)
        if not match:
            sys.exit(f"explanations.yml line {number} isn't 'key: \"text\"': {line}")
        out[match.group(1)] = json.loads('"' + match.group(2) + '"')
    return out


def call(method, url, token, body=None):
    request = urllib.request.Request(url, method=method, data=None if body is None else json.dumps(body).encode(),
                                     headers={"Authorization": "Token " + token, "Accept": "application/json",
                                              "Content-Type": "application/json",
                                              "User-Agent": "ezvac-weblate-explanations"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 429 and attempt < 4:  # rate limited: wait and retry
                time.sleep(int(error.headers.get("Retry-After", "30")))
                continue
            sys.exit(f"{method} {url} -> HTTP {error.code}: {error.read().decode(errors='replace')[:300]}")


def source_units(token):
    """Weblate key ('a->b') -> (unit id, current explanation), for every English source string."""
    units, url = {}, f"{SERVER}/api/translations/{PROJECT}/{COMPONENT}/{SOURCE}/units/"
    while url:
        page = call("GET", url, token)
        for unit in page["results"]:
            key = unit.get("context") or ""
            units[key] = (unit["id"], unit.get("explanation") or "")
        url = page.get("next")
    return units


def main():
    apply = "--apply" in sys.argv
    token = os.environ.get("WEBLATE_TOKEN", "").strip()
    if not token:
        sys.exit("Set WEBLATE_TOKEN first (Weblate: your profile -> API access -> personal API key).")
    wanted = explanations()
    units = source_units(token)
    print(f"{len(units)} source strings on Weblate, {len(wanted)} explanations in the file.")
    missing = [key for key in wanted if key.replace(".", "->") not in units]
    if missing:
        print("Not on Weblate (renamed or removed?):", ", ".join(missing))
    changes = [(key, units[key.replace(".", "->")][0], text) for key, text in wanted.items()
               if key.replace(".", "->") in units and units[key.replace(".", "->")][1] != text]
    print(f"{len(changes)} to set, {len(wanted) - len(changes) - len(missing)} already up to date.")
    for key, unit_id, text in changes:
        if not apply:
            print(f"  would set {key}: {text}")
            continue
        call("PATCH", f"{SERVER}/api/units/{unit_id}/", token, {"explanation": text})
        print(f"  set {key}")
        time.sleep(0.3)
    if not apply and changes:
        print("Dry run. Add --apply to write these.")


if __name__ == "__main__":
    main()
