# Repro for DataArchive.read directory whitelist using String.startsWith
# without a path-segment boundary (DataArchive.kt directory check).
# This is a logic demo; it does not import into the app.

allowed = [
    "manifest.json",
    "transactions.json",
    "audit_logs.json",
    "attachments/2026-09-05_收入_1.00_a31f82c4/current/a.pdf",
]


def directory_accepted(name: str) -> bool:
    return any(item.startswith(name) for item in allowed)


# Canonical parents should pass:
assert directory_accepted("attachments/")
assert directory_accepted("attachments/2026-09-05_收入_1.00_a31f82c4/")

# Extra prefix directories must fail per data_format.md, but currently pass:
assert directory_accepted("attach")  # not a parent of attachments/
assert directory_accepted("a")
assert directory_accepted("manifest.json")  # file name used as directory

print("prefix bypass: extra dirs attach/ and a/ are accepted")
print("minimal fix: require allowed item == dir or startswith(dir.rstrip('/') + '/')")
