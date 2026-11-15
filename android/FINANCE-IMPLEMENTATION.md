# Finance implementation — draft

Implemented: local SQLite ledger, accounts with unknown opening balances, income/expense journal, same-currency atomic transfers, confirmed soft deletion of linked transfer entries, native Finance screen, existing finance commands routed to local storage instead of an external app.

Not release-ready. Android compilation and device testing are outstanding; this environment lacks Android SDK and javac. Python tests validate the real SQL schema only, not Android runtime behavior.

Outstanding concept requirements: full natural-language parser and clarification flow; onboarding/default account; stable transport command IDs and persistent watch queue; editing/undo and audit history; date selection and opening-balance cutoffs; category management; budgets/goals/debts/subscriptions; notifications; period reports and forecasting; FX/crypto; encrypted storage, export and portable backups; privacy controls. Existing voice grammar still applies. Do not claim full concept implementation or watch reliability.

No APK requested or built. Keep this branch as a draft until remaining requirements and Android validation are complete.
