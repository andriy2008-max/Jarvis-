# JARVIS Finance — unreleased native implementation status
Date: 2026-11-15
Branch: `feature/finance-local-ledger` · Draft PR #4 · Pixel is the sole authoritative ledger.
**No APK has been built or signed. No code was merged into `main`. Compile-only tests do not validate hardware or production encryption migrations.**

## Included in the current UNBUILT source

- [x] In-app finance activity with Overview, Operations, Plan and Reports; guided account creation, typed/voice entry and quick filters.
- [x] **SQLCipher-encrypted active SQLite database, schema v6** using the maintained `net.zetetic:sqlcipher-android:4.20.0` driver, compiled against API 36 (target SDK stays 35). Per-install random passphrase is AES-GCM-wrapped under an Android Keystore key. Neither passphrase nor database file is uploaded by normal Android Auto Backup.
- [x] Existing **unencrypted** finance DB migration to a different encrypted file: copy in one transaction, verify foreign keys and table row counts, persist migration marker, and only then delete old file. Fail visibly and preserve original database on migration failure. **Actual hardware migration test still required.**
- [x] Reconciliation at current instant or **end of any historical past date**, retaining the original entries, previous opening/cutoff, and immutable reconciliation audit; prevent accounting double deductions.
- [x] Accounts with explicit voice-default selection and user-defined voice aliases, opening balances (including unknown), UAH/EUR/USD and other ISO-currency account denominations.
- [x] Expense, income, tips, same-currency transfers, linked purchase refunds, exact actual-currency FX exchanges and separate fees; amounts use integer minor units. Bank transfer amounts do not count as income or spending.
- [x] Goals, allocated/reserved cash, release of reserves, monthly/weekly budgets, principal debt tracking, confirmed settlements and safe undo with posted settlement history.
- [x] Daily/weekly/monthly/yearly anchored recurrence, including month-end and leap-year rules; no automatic posting of an unconfirmed payment.
- [x] Grouped shift tips with strict cash/card splits and optional recorded hours; report per shift and per hour.
- [x] **Manual** crypto holdings/price dates, confirmed buy/sell history, cash account asset movements plus one separately recorded commission, FIFO acquisition cost/realized result, and explicit warnings when original holding costs are unknown. Does not place orders.
- [x] Local speech/typed expense & income parsing with digits, Ukrainian amount words, deterministic categories, aliases, today/yesterday/day before yesterday and slash aliases; atomic two-to-five-entry voice batches.
- [x] Idempotent receipt IDs for phone/Watch7 writes; bounded same-device last-entry amount correction.
- [x] **Two-turn clarification** for a missing amount or category (e.g., 'витрата кава' → '85'), valid for 2 minutes, cancelable and stored with Keystore encryption. Only recognized deterministic finance categories and explicit amounts are accepted.
- [x] Watch7-to-Pixel transmission; a persistent **20-item encrypted** Wear queue with immutable IDs, safe unsent cancellation and persisted JobScheduler retry when watch UI is closed. Pending commands survive watch process restart; jobs may still be delayed by Wear OS battery policies.
- [x] Read-only financial speech queries for balances, periods, budgets, upcoming bills, tips/shift reports, all-currency net worth, manual crypto trade/cost history and dated NBU rates. Restricted private readouts while Pixel is locked unless explicitly enabled.
- [x] Account/category/kind/period/amount operations filtering, local spending charts, 6-month trend, duplicate/anomaly flags, what-if percentage scenarios and known-commitment projection.
- [x] Approximate net worth in UAH across currencies by explicitly dated NBU rates and manually entered crypto valuations, showing incomplete-data flags. No invented exchange rate.
- [x] Configurable quiet hours, opt-in weekly/monthly digests, generic planned-payment and 80/100% budget warnings with daily cap; notifications hide sensitive amounts.
- [x] Password-encrypted portable `JFB v6` backups of all account/plan/reconciliation/crypto/shift/alias data, backward restore support for JFB v2-v5, nonblocking file operations and encrypted recoverable pre-restore snapshot. Plain CSV export is intentional and user-triggered.
- [x] Android device-credential-gated Finance activity, FLAG_SECURE, re-lock on background, private file exclusions from Android cloud/transfer backups.

## Automated verification (no APK)

- [x] Python SQLite schema, FK, cutoff, settlement, cash-flow, reconciliation, alias, crypto and refund contract tests.
- [x] Pure Java Ukrainian voice command/query matcher, amount-correction, number words, slash aliases, batch, month-end recurrence and FIFO cost basis tests.
- [x] Whole Android `:app:compileDebugJavaWithJavac` (NO APK), including SQLCipher and Wear OS libraries.
- [x] Verified non-APK GitHub Actions run #231 (2026-11-15) — parser/schema jobs and Android compile job successful:
  https://github.com/andriy2008-max/Jarvis-/actions/runs/43755092451
- [ ] No real Pixel, Galaxy Watch7, Android Keystore restore, Doze/Battery Saver, call microphone or encrypted migration runtime test. Real-world functionality is **not** confirmed solely by passing CI.

## Remaining limitations requiring devices, permissions, providers or decisions

- [ ] **No signed APK:** by explicit prior user constraint, build/release/installation must only follow a separate user command. `main` remains untouched.
- [ ] Real Pixel+Watch7 acceptance testing: long-term delivery across app/process termination, reboot and energy-saving modes; microphone and Ukrainian OS speech-to-text; no Android JobScheduler can promise exact-time or guaranteed delivery under all device restrictions.
- [ ] Android Keystore on-device migration and JFB2–JFB6 portable restore tests, including lost/unavailable hardware key. For device changes or a factory reset, **only password-protected portable JFB backups can transfer the ledger**; Keystore keys are not exportable.
- [ ] Unrestricted conversational AI, arbitrary merchant/category recognition, complex open-ended multi-turn chats, and voice-only high-impact bill payments remain intentionally bounded by deterministic parsers and explicit confirmations to prevent false entries.
- [ ] Automatic bank/OAuth import and live cryptocurrency-market feeds are not enabled: they need a provider-specific API, user credentials/consent, rate-limit/error/privacy rules. Manual confirmed recording works independently.
- [ ] FIFO profit calculation intentionally marks manual opening holdings with unknown acquisition costs as incomplete.
- [ ] Offline Ukrainian STT and bundled model voice files must be validated separately on target devices; Android text compilation does not establish real offline speech availability.
- [ ] Multi-phone concurrent ledger synchronization is not implemented. Single Pixel is the authoritative database; portable encrypted backups are the recovery strategy.
- [ ] Advanced merchant-learning/predictive cash-flow/shift expense attribution require further real-life examples and recorded history to avoid inventing accounting classifications.
- [ ] Hardware/root compromise cannot be eliminated with SQLCipher or Keystore; user-controlled root can compromise a running unlocked process.

**Guardrail:** The current branch is an uninstalled feature candidate, not a verified production finance app. Keep PR #4 as draft until migration, device and finance reconciliation acceptance tests pass. Do not auto-post planned payments or use unsupported external APIs.
