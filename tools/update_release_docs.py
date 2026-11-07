#!/usr/bin/env python3
"""Updates the landing page and the README for a new release.

Usage: python3 tools/update_release_docs.py v1.9.0

The script takes the section of the new version from CHANGELOG.md and writes
it into the landing page, so the site always shows what the newest release
brings. The Ukrainian bullets come from CHANGELOG.uk.md when that file has a
section for the same version. The version badge of the README is updated too.
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LANDING = ROOT / "docs" / "index.html"
README = ROOT / "README.md"
CHANGELOG = ROOT / "CHANGELOG.md"
CHANGELOG_UK = ROOT / "CHANGELOG.uk.md"

AMP = chr(38)


def changelog_items(path: pathlib.Path, version: str):
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8")
    match = re.search(
        r"^## " + re.escape(version) + r"\s*$(.*?)(?=^## |\Z)",
        text,
        re.M | re.S,
    )
    if not match:
        return []
    items = []
    for line in match.group(1).splitlines():
        line = line.strip()
        if line.startswith("- "):
            items.append(line[2:].strip())
    return items


def escape(text: str) -> str:
    return (
        text.replace("&", AMP + "amp;")
        .replace("<", AMP + "lt;")
        .replace(">", AMP + "gt;")
        .replace(chr(34), AMP + "quot;")
    )


def js_list(items) -> str:
    if not items:
        return "[]"
    parts = []
    for item in items:
        text = escape(item).replace("\n", " ")
        parts.append("'" + text.replace("'", "\\'") + "'")
    return "[" + ", ".join(parts) + "]"


def update_landing(version: str, items, items_uk) -> bool:
    html = LANDING.read_text(encoding="utf-8")
    changed = False

    # The version under the name of the app
    marker = "<h1>Vuzol</h1>"
    version_tag = f'<p class="version" id="appVersion">{escape(version)}</p>'
    if 'id="appVersion"' in html:
        # The old line is taken away and the new one goes to the right place
        html = re.sub(
            r'\s*<p class="version" id="appVersion">.*?</p>',
            "",
            html,
            count=1,
            flags=re.S,
        )
        if marker in html:
            html = html.replace(marker, marker + "\n      " + version_tag, 1)
            changed = True
    elif marker in html:
        html = html.replace(marker, marker + "\n      " + version_tag, 1)
        changed = True

    # The block with the changes of the release
    if items:
        block = (
            '    <div class="card" id="whatsNewCard">\n'
            '      <h2 data-i18n="whatsNewTitle">What\'s new</h2>\n'
            '      <ul class="changes" id="whatsNewList">\n'
            + "".join(f"        <li>{escape(item)}</li>\n" for item in items)
            + "      </ul>\n"
            "    </div>\n\n"
        )
        if 'id="whatsNewCard"' in html:
            html = re.sub(
                r'    <div class="card" id="whatsNewCard">.*?\n    </div>\n\n',
                block,
                html,
                count=1,
                flags=re.S,
            )
        else:
            anchor = '    <div class="card" id="joinCard"'
            if anchor in html:
                html = html.replace(anchor, block + anchor, 1)
            else:
                html = html.replace("  </div>\n\n  <script", block + "  </div>\n\n  <script", 1)
        changed = True

    # The heading and the list in both languages
    def replace_block(text: str, title: str, entries) -> str:
        if "whatsNewTitle:" in text:
            return re.sub(
                r"(\n\s*)whatsNewTitle:.*?,(\n)",
                lambda m: m.group(1)
                + "whatsNewTitle: "
                + title
                + ","
                + m.group(2)
                + m.group(1)
                + "whatsNewItems: "
                + js_list(entries)
                + ","
                + m.group(2),
                text,
                count=1,
                flags=re.S,
            )
        # The keys are not there yet: they go right after allReleases
        match = re.search(r"(\n\s*)allReleases:.*?,(\n)", text)
        if not match:
            return text
        return (
            text[: match.end()]
            + match.group(1)
            + "whatsNewTitle: "
            + title
            + ","
            + match.group(2)
            + match.group(1)
            + "whatsNewItems: "
            + js_list(entries)
            + ","
            + match.group(2)
            + text[match.end() :]
        )

    html = replace_block(html, '"What\'s new"', items)

    uk_items = items_uk or items
    uk_start = html.find("uk: {")
    if uk_start >= 0:
        head = html[:uk_start]
        tail = html[uk_start:]
        tail = replace_block(tail, "'Що нового'", uk_items)
        html = head + tail

    # The list is filled from the dictionary when the language changes
    if "whatsNewItems" in html and "getElementById('whatsNewList')" not in html:
        anchor = "      var buttons = document.querySelectorAll('.langs button');"
        code = (
            "      var list = document.getElementById('whatsNewList');\n"
            "      if (list) {\n"
            "        var items = dict.whatsNewItems || [];\n"
            "        list.innerHTML = '';\n"
            "        for (var k = 0; k < items.length; k++) {\n"
            "          var li = document.createElement('li');\n"
            "          li.textContent = items[k];\n"
            "          list.appendChild(li);\n"
            "        }\n"
            "      }\n"
        )
        if anchor in html:
            html = html.replace(anchor, code + anchor, 1)

    # The style of the new block
    if ".version {" not in html:
        html = html.replace(
            "</style>",
            "  .version { margin: 4px 0 0; font-size: 13px; color: var(--muted); letter-spacing: .06em; }\n"
            "  .changes { margin: 8px 0 0; padding-left: 20px; color: var(--muted); line-height: 1.7; }\n"
            "  .changes li { margin: 4px 0; }\n"
            "</style>",
            1,
        )

    if changed or 'id="appVersion"' in html:
        LANDING.write_text(html, encoding="utf-8")
    return changed


def update_readme(version: str) -> bool:
    text = README.read_text(encoding="utf-8")
    updated = re.sub(
        r"(Релізи-)v\d+\.\d+\.\d+(-00C851)",
        lambda m: m.group(1) + version + m.group(2),
        text,
    )
    if updated != text:
        README.write_text(updated, encoding="utf-8")
        return True
    return False


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: update_release_docs.py <version>")
        return 1
    version = sys.argv[1].strip()
    if not version.startswith("v"):
        version = "v" + version
    items = changelog_items(CHANGELOG, version)
    items_uk = changelog_items(CHANGELOG_UK, version)
    print(f"version {version}: {len(items)} changes, {len(items_uk)} ukrainian")
    changed_landing = update_landing(version, items, items_uk)
    changed_readme = update_readme(version)
    print("landing updated" if changed_landing else "landing unchanged")
    print("readme updated" if changed_readme else "readme unchanged")
    return 0


if __name__ == "__main__":
    sys.exit(main())
