# JARVIS Finance — checklist of the UNBUILT development version
Date: 2026-11-15  
Branch: `feature/finance-local-ledger` · Draft PR #4.  
**APK has NOT been built, installed or signed in this development cycle. Stable `main` has not been updated.**

## Included in the current source (not yet installed)

- [x] Native Finance on the phone, separate from the former external Money Manager UI/API route; four tabs: Overview, Operations, Plan, Reports.
- [x] First-account setup prompt, known/unknown opening amounts and explicit default account for voice.
- [x] SQLite schema v5, preserving existing data through onUpgrade; integer minor units and capture-time balance cutoff, no phantom income from opening amounts.
- [x] Accounts and saved voice aliases, expenses, income, tips, refunds linked to original purchases, own-account transfers with atomic database operations.
- [x] Confirmed cash reconciliation with previous records kept in history; reconciliation date is the moment of confirmation, NOT arbitrary historical cutoff entry.
- [x] Cross-currency exchange with exact debit and credit amounts and optional separate expense for fees; never substitutes an NBU rate for the actual exchange.
- [x] Manual crypto positions retain exact quantity and optional manually provided UAH price plus valuation date; no auto trading or live crypto price feed.
- [x] Expense/income voice commands with known categories and explicitly marked account suffixes, including Ukrainian number words.
- [x] Voice record dates today/yesterday/day before yesterday, bound to capture time; last-entry correction `не 85, а 95` with a 3-minute same-device window.
- [x] Explicit multi-expense phrase `запиши витрати кава 85 і таксі 250`, 2–5 items, all-or-nothing commit and single immutable receipt ID.
- [x] Allowlisted slash aliases for entries, balance, summary, budgets and payments; nonimplemented slash actions do not silently mutate data.
- [x] Phone as sole ledger; Watch7 sends immutable request IDs. Locally persisted FIFO queue of up to 20 financial records; retries on foreground heartbeat, acknowledges success and releases definitively rejected entries without blocking later ones.
- [x] Safe cancellation of clearly unsent Watch7 queue entries. Already-sent or uncertain commands require checking the Pixel journal before reversal.
- [x] Operations journal with search and filters: account, category/note, kind, date period and amount bounds; audited editing, confirmed delete and restore.
- [x] Monthly and weekly budgets with spent/remaining calculations that account for linked refunds.
- [x] Goals and nonduplicating cash reservations; release reserves, partial receivable/payable settlements; actual principal payments stay separate from earned income.
- [x] Payment recurrence daily/weekly/monthly/yearly, original-day anchoring across short months and leap years, explicit confirmation before posting; safe undo of the most recent settlement where v5 history exists.
- [x] Grouped shift tips across cash/card accounts with exact split sum, optional entered work hours and an hourly summary.
- [x] Period reports (today/week/month/previous month), top categories, cash-flow breakdown, recorded-tips totals, six-month UAH bar chart and textual data.
- [x] Evidence-bounded audit of possible duplicates/anomalies, conditional percentage-cut scenario, known upcoming payments, UAH financial-health view and emergency-buffer estimate only with sufficient history.
- [x] Optional generic payment reminders, opt-in weekly/monthly digests, configurable quiet hours and at most 3 financial notifications/day; lock-screen text excludes transaction amounts.
- [x] Dated manual NBU rate refresh and separate UAH valuation (no invented rate).
- [x] Encrypted password-protected JFB v5 portable backups with v2–v4 restore compatibility, asynchronous file I/O and encrypted automatic pre-restore safety snapshot. CSV export remains available.
- [x] Android credential gate on Finance screen, FLAG_SECURE, re-authentication after backgrounding and lock-screen voice report opt-in.
- [x] Plaintext working finance database and cached Watch requests excluded from ordinary Android cloud/device-transfer backups; **this does NOT encrypt the live SQLite database**.

## Verified automatically on GitHub (not on physical devices)
- [x] `tools/finance/test_schema.py`: SQLite schema/FK/cutoff/refund/FX/settlement/shift/alias contracts.
- [x] Java parser tests for finance entries, amount correction, Ukrainian number words, slash aliases, multi-expense batches.
- [x] Java recurrence calendar tests for leap days and month boundaries.
- [x] Whole Android **`compileDebugJavaWithJavac`** passes, including the Wear dependencies; no APK packaging or signing.
- Passing CI: **GitHub Actions run #139**, 2026-11-15, using CI-only validation PR #6: `https://github.com/andriy2008-max/Jarvis-/actions/runs/43751628511`.

## Still unverified / not fully implemented
- [ ] No real Pixel or Galaxy Watch7 integration test, including locked screen, microphone, app lifecycle, retransmission, 20-item queue and notification delivery. Runtime correctness is not established by compilation.
- [ ] No APK build, signing, installation or upgrade-migration smoke test; no release versionCode bump.
- [ ] The **working SQLite database itself is not encrypted at rest**. Portable backups are encrypted. Rooted devices carry additional data-exposure risks.
- [ ] Complete open-ended natural-language dialog, multiple-turn confirmation and all slash commands from the original specification are not implemented. Supported voice commands are deterministic and conservative.
- [ ] Fully configurable opening-balance historical cutoff and transaction-level retrospective reconciliation are not complete (current-time snapshots work).
- [ ] Crypto trades/cost basis/history, live market pricing, automated bank integration, and mixed-currency full net-worth conversion are not implemented.
- [ ] Watch queue processing is not guaranteed when the watch app is closed; background heartbeat currently works while the companion link is alive.
- [ ] Encrypted pre-restore snapshot and v2–v5 restore path have not been tested end-to-end on real devices.
- [ ] Advanced per-transaction tags, multi-merchant learning, complete shift expense attribution, audit advice and confidence-based forecasting are incomplete.
- [ ] A small UAH-only bar chart is included; a multi-currency interactive graph suite is not.
- [ ] Independently confirmed hardware-based offline Ukrainian speech recognition is not available yet.
- [ ] Synchronization of simultaneously changing finance data on multiple phones is outside the single-Pixel-ledger architecture.

**Release guard:** Do not merge this draft feature to `main` or call it production-ready until device tests and security checks are complete. The next APK should be assembled only after an explicit user command.
