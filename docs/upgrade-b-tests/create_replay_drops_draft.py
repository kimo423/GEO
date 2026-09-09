# Logic demo: create idempotency replay returns existing id and ignores the
# current draft (LedgerRepository.saveTransaction + AddTransactionViewModel.save).
# After process death, SavedStateHandle may restore client_op_key without local id.

first_draft = {"amount": 100, "attachments": ["A", "B"]}
second_draft = {"amount": 200, "attachments": ["A", "C"]}
db = {}


def save(requested_id, draft, client_op_key):
    existing = next((row for row in db.values() if row["key"] == client_op_key), None)
    if requested_id is None and existing is not None:
        return existing["id"]  # current code: no-op, even if draft changed
    if requested_id is None:
        db[1] = {"id": 1, "key": client_op_key, **draft}
        return 1
    db[requested_id] = {**db[requested_id], **draft}
    return requested_id


save(None, first_draft, "op-1")
returned = save(None, second_draft, "op-1")
assert returned == 1
assert db[1]["amount"] == 100
assert db[1]["attachments"] == ["A", "B"]
print("replay reported success id=1 but A,B->A,C and amount 200 were dropped")
print("minimal fix: if create replay hits an active row, continue as edit of that id")
