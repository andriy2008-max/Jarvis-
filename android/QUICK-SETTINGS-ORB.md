# Jarvis floating face from Android Quick Settings

## Послідовні голосові команди з перевіркою екрана — код після 0.1.15

Приклад однієї команди:

> «Джарвіс, відкрий Ютуб, натисни пошук, напиши відео про армрестлінг і постав гучність на 70».

`JarvisCommandSequence` локально та детерміновано розбирає прохання на **впорядковані дії** (до шести): запуск застосунку, натискання безпечної кнопки, введення буквального тексту у вже активне поле, зміна медіагучності. Просте «Відкрий Ютуб» і «Постав гучність на 70» також підтримуються у ввімкненому режимі керування екраном, включно з окремим мікрофоном AccessibilityService.

`JarvisSequenceEngine` **не покладається на фіксовані затримки**: циклічно перевіряє **реально видимий пакет застосунку** через надану користувачем AccessibilityService; лише після відкриття відповідного пакета шукає **єдину** видиму кнопку з потрібною семантикою; після успішного `ACTION_CLICK` очікує **активного редагованого не-парольного поля в тому самому застосунку**; лише потім вводить текст; **медіагучність змінює останньою**. Між перевірками близько 400 мс, для кожного UI-кроку обмежений час очікування (до 18 с; поле — 12 с). Якщо крок не вдався, неоднозначний, небезпечний, не з'явився до тайм-ауту, відключено дозвіл або користувач скасував послідовність — **жодна наступна дія не виконується**.

- Плаваюче обличчя: натискання під час виконання черги **скасовує її**. Закриття обличчя також скасовує.
- Текст у команді «Напиши...» зберігається буквально (регістр, пунктуація, слова «і»). Межа наступної команди визначається лише за явним розпізнаним дієсловом, наприклад «і постав гучність на 70»; неоднозначну фразу краще продиктувати окремо.
- Паролі, банківські, видалення, покупки, надсилання та кнопки підтвердження залишаються недоступними для автоматичного натискання; натискання «Надіслати» після введення тексту **не виконується**.
- Зауваження: система Android може не дозволити запуск стороннього застосунку з фонової служби; якщо YouTube не відкривається, послідовність зупиниться. Без описаного в Accessibility UI значка Джарвіс не вгадує, куди натиснути.
- **Перевірки без APK:** [Java parser, state machine та компіляція Android](https://github.com/andriy2008-max/Jarvis-/actions/runs/42824255391). Тестова модель UI перевірила очікування кожного кроку, скасування й тайм-аути; реальне виконання в YouTube на фізичному телефоні **ще не перевірене**, оскільки за вимогою користувача **ніяких APK не збираємо**.

## Нові зміни в коді — рухоме обличчя й розпізнані слова

- **Пересування:** плаваюче кругле обличчя можна взяти пальцем і перетягнути в інше місце екрана. Це не запускає мікрофон. Координати зберігаються як відносна позиція у приватних налаштуваннях застосунку й відновлюються після закриття/повторного відкриття та зміни орієнтації. Вікно залишається у доступній зоні, не заходячи під системні панелі.
- **Налаштування вигляду:** в головному Джарвісі додана кнопка **«Змінити розмір і колір плаваючого обличчя»**. Розміри 96/112/144 dp, кольори — класичний блакитний, бірюзовий і фіолетовий. Якщо вікно вже відкрите, зміни застосовуються одразу.
- **Текст замість міміки:** коли користувач натиснув обличчя для розпізнавання, замість очей/рота відображається «Слухаю…», після чого живі часткові гіпотези розпізнавання (**якщо Android SpeechRecognizer їх надає**). Після фінального результату останні почуті слова видно ще приблизно 3,5 секунди, потім повертається обличчя. У маленькому колі показується лише короткий кінець довгого речення, щоб текст залишався читабельним.
- **Приватність:** розпізнані слова зберігаються **лише тимчасово в пам'яті самого плаваючого вікна**, не логуються й не записуються у файл; при новому сеансі, помилці або закритті вони очищуються. Виведення слів поверх інших програм відбувається на явний запит користувача.
- **Керування збережено:** короткий дотик починає слухати, повторний — завершує, довге утримання — закриває. Під час перетягування жодна голосова команда не запускається.
- **Поточний статус:** ці зміни внесені в GitHub, перевіряються компіляцією та unit-тестами **без створення APK**. Вони **не входять** до вже зібраного тестового APK 0.1.15. Відповідно до вимоги користувача жодних нових APK/емуляторних APK не збирати до прямої команди; підтримується тільки повна редакція Джарвіса.

## What was added

- `JarvisQuickTileService`: real Android Quick Settings tile labeled **Джарвіс**. Clicking it does **not** launch `MainActivity`. It launches a short-lived transparent permission activity compatible with Android 14/15 `PendingIntent` tile API.
- `JarvisBubbleLauncherActivity`: requests two user-controlled permissions only if missing: microphone and **Display over other apps** (`SYSTEM_ALERT_WINDOW`). No root and no Accessibility grant are required for the bubble itself.
- `FloatingJarvisService`: microphone-type foreground service with a visible ongoing notification and **Закрити** action. Uses `TYPE_APPLICATION_OVERLAY` to draw a small **112dp round 8-bit blue face** while keeping the current app visible. No automatic microphone recording when the bubble appears.
- `JarvisOrbView`: reusable lightweight pixel-art face with idle, listening, thinking and speaking animations, matching the main Jarvis visual concept.
- Short tap: start a **user-triggered** recognition session. Second tap: finish and process current utterance. Long press: close and release the microphone, speech runtime and overlay.
- Commands in the orb: **«Скажи [текст]»**, timer start/cancel/status, time/date, media play/pause/next/previous, volume, switch Dmytro/Lada and AI chat if a private HTTPS AI connection was previously configured. Speech replies use installed Dmytro/Lada offline model when available, then private TTS server, then Android TTS. The floating orb has its **own** recognition session and does not claim that every command in MainActivity is available.
- In the **full Jarvis app** with Accessibility previously manually granted, the user may say «Увімкни керування екраном» to enable one-step screen commands controlled through the **same orb**, without another microphone bubble. The user's dedicated screen permission is never enabled silently. The separate Lite edition has been retired.
- No persistent microphone recording; notifications show the foreground service while the bubble is on-screen. A microphone indicator can appear only when the user initiates recognition. The user can stop at any time by long-pressing the orb or pressing **Закрити** in its notification.

## Add the Quick Settings button

1. Open Jarvis and tap **«Додати Джарвіса в швидкі налаштування»**. Android 13+ may offer a one-tap system dialog to add the tile.
2. If the system does not show the dialog, swipe down twice, press the edit/pencil control and drag **Джарвіс** into your active Quick Settings tiles.
3. Tap the new Quick Settings tile. Android will ask for a one-time **Display over other apps** permission and, if missing, microphone permission. These permissions are required only when the user explicitly wants the bubble.
4. The shade collapses and a small round face appears over the current application. Tap to speak; tap again to finish, long-press to close.

A shortcut **«Показати плаваюче обличчя»** is also available in the main phone UI so users can test the bubble without editing Quick Settings.

## Important limitations

- Android version/vendor background-service and overlay policies vary; test on physical phones. Google Play Protect may still classify an APK with powerful permissions; do not advise disabling Play Protect.
- The floating bubble's separate `SpeechRecognizer` depends on available Android language packs or connectivity; offline voice **playback** does not guarantee offline speech **recognition**.
- A persistent low-importance notification shows while the bubble is visible, allowing the user to close the service.
- If a feature needs the full settings UI (e.g. adding AI credentials, first voice-model download, microphone permission for camera), open the main app **manually**; the Quick Settings tile never opens it for routine commands.
- **Android 11 and Android 15 Quick Settings integration PASSED** in [run 42814267846](https://github.com/andriy2008-max/Jarvis-/actions/runs/42814267846): real Quick Settings tile callback, microphone foreground service, visible floating face and a real tap on the orb were verified on both emulators. These automated tests do not check actual spoken audio or speech recognition quality on a physical phone.
- **Historical build:** version 0.1.15 / versionCode 25 had an original-certificate signed **release-QA** APK artifact in [run 42814492405](https://github.com/andriy2008-max/Jarvis-/actions/runs/42814492405). This earlier APK predates the dragging, size/color preferences and live transcript changes. The old Lite test artifact is no longer maintained. No newer APK has been requested or built.
- Android 17 / Pixel 9 physical-device behavior still requires voluntary user validation. Do not disable Play Protect merely to install the full build.
