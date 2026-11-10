"""Check the repository navigation and local Markdown link targets."""

import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

required = [
    Path("README.md"),
    Path("AGENTS.md"),
    Path("PROGRESS.md"),
    Path("docs/android-design.md"),
    Path("docs/implementation-plan.md"),
    Path("docs/verification.md"),
]
documents = sorted(set(required) | set(Path("docs").rglob("*.md")))
errors = []

for document in documents:
    if not document.is_file():
        errors.append(f"{document}: required document is missing")
        continue

    for destination in re.findall(r"\]\(([^)]+)\)", document.read_text(encoding="utf-8")):
        link = urlsplit(destination)
        if link.scheme or link.netloc or not link.path:
            continue
        if not (document.parent / unquote(link.path)).exists():
            errors.append(f"{document}: broken local link {destination}")

for error in errors:
    print(error, file=sys.stderr)

sys.exit(bool(errors))
