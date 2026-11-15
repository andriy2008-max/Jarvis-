# Finance implementation status — 2026-11-15

Implemented in the finance branch:
- Native ledger, accounts, income/expenses, atomic same-currency transfers, edit history, deletion/restore, search and CSV.
- Four sections, monthly budgets and dated goal/payment/debt registers.
- Goal allocations backed by known account balances; no double reservation. Partial debt repayments update cash and progress atomically, excluded from earned income/spending reports.
- Confirmed payment posting with monthly/yearly recurrence. Plan deletion releases goal reserves and preserves posted cash entries.
- Persisted watch command ID, text and capture time; retries reuse the ID; phone atomically stores receipt with entry. Foreground watch heartbeat retries one pending finance command. New different finance command waits for that pending command. No claim of background delivery while watch app is stopped.
- Default voice account; case-insensitive Ukrainian account matching in Java.
- Device-credential gate on opening Finance when the device has a secure lock.
- Optional periodic reminder job, at most one generic notification per day, with no financial amounts on lock screen; upcoming three events displayed in Finance.
- Monthly category report, six-month recorded-spending trend, surplus rate and explicitly approximate monthly spending pace.
- Manual refresh of dated official NBU rates; account valuation in UAH; incomplete values remain marked rather than invented.
- Password-encrypted portable backups and atomic restore; reads prior v2 backup format; database schema migrated to v3 without dropping entries.

Validation: nine finance-related Java classes compile against Android 15 APIs using ECJ Java 17 (credential API deprecation warning only). Extended parser test passes; 11 SQLite schema tests pass. Whole-app compilation, wearable dependency compilation, live NBU fetch and Pixel/Watch testing have not been completed. No APK built.

Remaining requirements from the original full concept: guided onboarding; arbitrary natural-language/slash command coverage and follow-up dialog; date/account cutoff reconciliation; refunds; multiple queued watch commands and explicit cancellation; daily/weekly anchored recurrence; adjustable quiet hours and digest schedules; FX transfer entry and crypto quantities; complete financial-health/scenario/audit advice; encrypted working database; re-lock after backgrounding; asynchronous backup UX and automated pre-restore snapshot; plan/category editing; exact per-period report selectors. Goal reservations do not yet appear deducted from the overview balance. Generic deletion/edit of posted plan settlements is deliberately blocked until reversal accounting is implemented.

The branch remains a development implementation, not completion of every requirement in JARVIS_Finance_Concept.md. Do not claim production/device verification.
