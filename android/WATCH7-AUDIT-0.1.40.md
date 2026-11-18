# Дімон 0.1.40 (50): аудит Watch7, кандидат для перевірки

База: main bdc5b654ae0b2e38f3a060e9a1256f8342396bd6, 18.11.2026.
**Це діагностичний кандидат, не підтверджена стабільність фізичного Watch7.**
Назва пакета com.andriy.jarvis.stable означає сумісність оновлення, а не результат фізичного QA.

## Факти та межі висновків

- 0.1.39/49 зібрано Actions 44344822645 із 1bf31c6f2e58d52d6a63dcd322c524a90eb73e96. Порівняння v0.1.39 → main показує лише зміну workflow. Вихідний Android-код попередніх Spotify-виправлень увійшов у дерево релізу. Це перевірка походження збірки; не декомпіляція встановленого на годиннику APK.
- Реліз 0.1.39 на GitHub має prerelease=true; попри слово stable у заголовку, фізичного QA там не наведено. QA-файли є ручними чек-листами, не протоколами виконання.
- MainActivity.onPause скидала processing=false навіть при незавершеній локальній/телефонній операції. Це втрата стану UI, а не самостійний доказ аварії процесу.
- Listener розпізнавання перевіряв лише тотожність SpeechRecognizer, без захисту від повторних terminal callbacks в одному екземплярі. Повторний результат міг потрапляти в інший стан слухання.
- Частина Accessibility-звернень у Maps, очікуванні Spotify та локальному керуванні була поза try/catch відкладених Handler callbacks. Виняток там міг завершити головний потік.
- Тайм-аут Spotify перевірявся лише при наступному кроці: втрата callback жесту залишала операцію RUNNING без повторного входу в перевірку дедлайну. Аналогічна проблема локального керування.
- Watch використовував той самий шлях ONNX TTS, що Pixel: встановлений голос міг утримувати модель приблизно 145 MB і додаткові робочі буфери. Це ризик OOM/LMK, **не підтверджений OOM конкретного Watch7**.
- MainActivity не викликає finish()/finishAffinity()/Home у звичайному завершенні голосової команди. Запуск Spotify/Maps закономірно переводить її у onPause/onStop.
- Немає отриманих FATAL EXCEPTION, ANR, ApplicationExitInfo або logcat фізичного Watch7. Єдина причина повідомленого користувачем повернення на циферблат поки не встановлена.

## Зміни

| Файл | Проблема | Зміна |
|---|---|---|
| MainActivity.java | callbacks, lifecycle, TTS | Одноразові terminal callbacks; новий recognizer на кожний цикл; інвалідація перед onPause; збереження busy; weak callback локальних операцій; системний TTS Watch; захист помилок TTS; журнал команд; очищення Handler при destroy |
| JarvisCallbackGate.java | старі/повторні завершення | Синхронізоване володіння операцією за поколінням |
| JarvisWatchLocalController.java | глобальний running без token і загального дедлайну | Token, watchdog 65 с, результат у пам'яті для повернення Activity, локальний маршрут без Pixel fallback |
| JarvisWatchSpotifyController.java | callback без дедлайну та одноразового завершення | Незалежний watchdog, gate кроків, контроль активності операції, повторне очікування |
| JarvisWatchMapsController.java | запуск із application context, зависання жесту | Початковий запуск із видимої Activity, обмежена тривалість сесії |
| JarvisWatchScreenService.java | винятки при зміні дерева Accessibility | Контрольований BLOCKED/WAIT із записом коду помилки; жодних координат Pixel |
| JarvisWearPhoneLink.java | пошук вузлів без дедлайну | 12-секундний тайм-аут пошуку, захист старого epoch, метадані маршруту/відповіді |
| JarvisApplication.java, JarvisDiagnostics.java | лише Spotify trace | Загальний bounded journal, lifecycle, process summary, uncaught handler із передачею системі, історія ApplicationExitInfo |
| JarvisDiagnosticsActivity.java | немає звіту в UI | Перегляд, копіювання, share, очищення; ⋮ → Діагностика → Журнал роботи |
| JarvisWatchSpotifyTrace.java | synchronous commit на UI кожного кроку | Асинхронний запис + Android process state summary |
| JarvisWearMessageReceiver.java, JarvisRecentRequests.java | Повторна доставка request ID | Обмежене стійке сховище 128 хешів; не повторювати побічну дію після повторної доставки або restart; read-only SYNC не витісняє команди |
| AndroidManifest.xml | ініціалізація діагностики | Application і неекспортований екран діагностики |

## Дані, безпека, відновлення

Пакет, постійний сертифікат, ключі старих preferences, нотатки, Finance і протокол Data Layer не мігруються. Оновлення лише поверх попередньої версії з тим самим підписом. Видаляти застосунок не потрібно.
Журнал у noBackupFilesDir: два файли приблизно по 64 KB, окремий короткий аварійний запис; enum події, числові коди, часові мітки, власні методи/номери рядків. Не пишуться фрази, відповіді, назви пісень, ключі, PIN, exception messages, accessibility tree або системні ANR traces. Експорт лише за дією користувача. Черга запису обмежена; при перевантаженні записи можуть пропускатися. Останній числовий стан також передається Android через setProcessStateSummary, якщо API доступний.

COMMAND_END означає, що спостережувана робота диспетчера завершилася, не гарантію результату сторонньої програми. LOCAL_DONE означає підтвердження контролера; натискання на пісню не є доказом звуку. Відсутність історії завершень не доводить відсутності аварії.

Після process death незавершені побічні дії не повторюються автоматично. Локальні операції, що пережили пересоздання UI в тому самому процесі, зберігають busy і останній результат у пам'яті; при знищенні процесу потрібно повторити команду вручну. При configuration change з’єднання Pixel зберігається через non-configuration instance, а нова Activity підбирає завершений результат. Після повної смерті процесу відповіді Pixel можуть потребувати повторного запиту. Повного стійкого сховища довгих операцій ця версія не заявляє.

OngoingActivity залишається ярликом повернення; не вважається гарантією безсмертя процесу. Немає нескінченних перезапусків, фонового always-on мікрофона або нового фіктивного FGS. Pixel companion FGS і AlarmManager не замінено. Окремий Wear APK можливий пізніше з тим самим package/cert і без ONNX/TDLib; зараз не вводиться без перевірки всіх посилань і оновлення.

## Автоматична перевірка

Workflow `watch7-audit.yml`: всі наявні Java contract tests + 100 конкурентних перевірок gate; компіляція debug і instrumentation; Wear OS emulator; підписаний release-кандидат тільки після проходження попередніх кроків. Результат конкретного запуску дивись у GitHub Actions і доданому test-evidence artifact.
`WatchLifecycleTest` подає 20 **синтетичних текстових команд** у той самий dispatcher, перевіряє збереження процесу, пересоздання Activity та відновлення lifecycle; перевіряє privacy звіту й екран діагностики. Додатково 20 синтетичних onResults із подвійним результатом та пізнім onError перевіряють одноразове передавання в dispatcher. Це не 20 голосових фраз із фізичного мікрофона. Spotify/Maps можуть бути відсутні на емуляторі; тоді перевіряються відмови, не успішне керування їх UI.

Обидва фізичні чек-листи мають статус **потребує перевірки на Watch7 / Pixel**. Немає доказу всіх 20 акустичних команд, реального Spotify, Bluetooth, успішного TTS, Bolt, Telegram або фінансових транзакцій на пристроях користувача.

## Як зняти ADB на Watch7 (Windows)

1. Годинник і ПК підключити до однієї довіреної Wi-Fi мережі. Встановити Android SDK Platform Tools на ПК.
2. На Watch7: Налаштування → Про годинник → Відомості про ПЗ → кілька разів натиснути «Версія ПЗ», доки не ввімкнеться режим розробника. Назви пунктів залежать від версії One UI Watch.
3. Налаштування → Параметри розробника → Налагодження ADB / Бездротове налагодження. Вибрати сполучення через код. Записати IP і **порт сполучення**, виконати `adb pair IP:PAIR_PORT` і ввести код у запиті. Код не надсилати в чат.
4. Повернутися на головний екран бездротового налагодження, взяти **порт з'єднання** (часто інший): `adb connect IP:CONNECT_PORT`.
5. `adb devices -l`. Якщо підключено кілька пристроїв, всюди використовувати `adb -s IP:CONNECT_PORT`.
6. Перевірити встановлений пакет: `adb -s IP:CONNECT_PORT shell dumpsys package com.andriy.jarvis.stable` — знайти versionName/versionCode.
7. Запустити запис до відтворення: `adb -s IP:CONNECT_PORT logcat -b main -b system -b crash -v threadtime > watch7-logcat.txt`.
8. Відкрити Дімона, виконати проблемну команду, дочекатися повернення на циферблат; зупинити запис Ctrl+C. Не фільтрувати лише за PID: після падіння PID зміниться.
9. `adb -s IP:CONNECT_PORT shell dumpsys activity exit-info com.andriy.jarvis.stable > watch7-exit-info.txt`.
10. `adb -s IP:CONNECT_PORT shell dumpsys activity activities > watch7-activities.txt` — допомагає відрізнити відкриття іншої Activity від смерті процесу. За ANR можна локально зняти `adb -s IP:CONNECT_PORT bugreport watch7-bugreport.zip`.
11. Повторно відкрити Дімона → ⋮ → Діагностика → Журнал роботи → скопіювати/поділитися. Після тесту вимкнути бездротове налагодження.

Системний logcat/bugreport, на відміну від внутрішнього журналу, може містити приватні дані інших програм. Перед передаванням переглянути та прибрати приватні фрагменти. Потрібний інтервал: приблизно 15 секунд до команди та 30 секунд після збою, причина exit-info і stack trace AndroidRuntime.

API: https://developer.android.com/reference/android/app/ApplicationExitInfo ; https://developer.android.com/reference/android/app/ActivityManager#getHistoricalProcessExitReasons(java.lang.String,int,int) ; https://developer.android.com/training/wearables/notifications/ongoing-activity ; https://developer.android.com/training/wearables/get-started/debugging
