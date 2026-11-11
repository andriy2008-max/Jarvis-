# JARVIS 0.1.20 — MacroDroid bridge

Signed stable update over 0.1.19, preserving the stable application ID and signing certificate.

## Included

- Macro editor now has an optional “Передати запуск макросу в MacroDroid” mode.
- After a voice phrase matches and Jarvis constraints pass, Jarvis broadcasts `com.andriy.jarvis.MACRO_TRIGGER` to the MacroDroid package with the normalized `jarvis_trigger`, `jarvis_macro_name`, and `jarvis_macro_id` extras.
- The action list in Jarvis is hidden for macros delegated to MacroDroid. Configure the actions and any additional constraints in MacroDroid.

To connect a MacroDroid macro, add the “Інтент отриманий” trigger, use action `com.andriy.jarvis.MACRO_TRIGGER`, and match the `jarvis_trigger` extra to the phrase configured in Jarvis. The broadcast confirms only signal dispatch; MacroDroid does not currently send action-completion status back to Jarvis.
