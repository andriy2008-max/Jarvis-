# JARVIS Finance implementation status — 2026-11-15

Branch: `feature/finance-local-ledger`. Native finance ledger inside Jarvis (Pixel is the source of truth). **This is an in-development implementation, not a production-tested release. No APK has been built for this branch.**

## Implemented in source
- Separate on-phone Finance activity with Overview, Operations, Plan, Reports tabs; guided first-account prompt. First account does **not** silently become the voice-default: the user explicitly chooses it.
- Local SQLite ledger v4 with exact integer minor units, per-account currency and known/unknown opening balance. Newly created known openings carry a capture-time cutoff: earlier expenses are kept in history but not subtracted from the captured balance. v1–v3 migration preserves older accounts using a legacy (0) cutoff. Full account reconciliation for historical imports is not yet implemented.
- Expense, income and tips categories, same-currency atomic internal transfers, linked purchase refunds, net refund-adjusted spending and budgets. Edits preserve refund constraints; plan payments/debt principal remain distinct from earned income.
- Operations search; confirmed deletion/restoration and audit rows for amount edits and voice corrections. Plans: monthly budgets, goal reservations with available cash calculated separately, due dates, debt receivables/payables, confirmed partial settlements, monthly/yearly payment recurrence; plans can now be edited with progress validation.
- Phone and Watch7 voice entry through Wear messages. Immutable ID + captured time, atomic receipt idempotency, one pending command on watch with foreground retry; no false assertion of guaranteed background delivery. Natural expense/income and Ukrainian 'today/yesterday/day before yesterday' dates, plus same-device latest-entry correction 'не 85, а 95' within a 3-minute interval.
- Read-only voice questions via the on-phone ledger: balance, today's/this week's/this month's net spending and income, monthly budgets and upcoming obligations; watch requests are answered by Pixel. Private readouts from locked Pixel are forbidden unless explicitly enabled.
- CSV export, six-month spending trends, dated official NBU conversion-cache evaluation (manual refresh), limited one-per-day generic future-payment reminders, password-encrypted portable JFB v4 backup and atomic restoration from JFB v2/v3/v4.
- Finance screen uses Android device credential when a secure lock is configured, re-lock after backgrounding, and FLAG_SECURE. Automatic Android cloud/transfer backups exclude the plaintext finance SQLite DB, which is **not encrypted at rest** by Jarvis itself.

## Automated contract checks
- `tools/finance/test_schema.py`: SQLite schema, balance cutoff migration, amount accounting and refund tests.
- `FinanceVoiceCommandsTest` and `FinanceVoiceFollowupsTest`: strict Ukrainian parsing and amount correction cases.
- `.github/workflows/finance-contracts.yml`: isolated Python + javac contract suite **and** full Android `compileDebugJavaWithJavac` compilation; no APK packaging or signing.
- Verified on GitHub Actions: SQLite schema and voice parser jobs **passed**, and full Android Java compile-only job **passed** (2026-11-15 run #17). This is not a physical-device or APK acceptance test.

## Pending before daily-use/release claims
- Signed release build by explicit user request, live Pixel and Watch7 end-to-end tests (including microphone, retransmission, lock screen and app staying open). Compile-only passed; device behavior remains unverified.
- Full natural-language/slash-command and interactive clarification coverage, amount words, multi-entry tips per shift and hours, persistent multi-command watch queue, and explicit cancellation.
- User-controlled opening balance effective-date input/reconciliation, cross-currency transfers with actual FX rate and fees, crypto amount/valuation.
- Weekly and daily recurring schedules, quiet hours, consolidated notification quotas and digests, richer filters and visual charts, spending audit and scenario forecasting.
- Encrypted working database, offline Ukrainian speech verification, asynchronous/password-safe backup UX and pre-restore safety snapshot.
- Complete settlement reversal/history so deleting a posted plan payment safely reverses plan progress.

**Guardrail:** Planned/estimated payments do not become actual transactions until the user confirms them. The finance ledger is separate from the old external Money Manager UI/API implementation on `main`. Do not merge or announce the feature as ready before tests pass.
