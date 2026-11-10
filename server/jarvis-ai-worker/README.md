# JARVIS AI proxy (personal Cloudflare Worker)

This is an **optional, separately deployed backend** for JARVIS Android 0.1.7. It is not live by default.
The phone sends a text prompt to `POST /chat`; the Worker forwards it to the OpenAI Responses API.
The Android app contains **no OpenAI API key**. The user's device holds only a separate revocable access token encrypted by Android Keystore.

## Setup

1. Create a Cloudflare Workers account and install Node.js. In this folder run `npm install`.
2. Set a **server-only** OpenAI project key: `npx wrangler secret put OPENAI_API_KEY`. Never put it in source code, APK, `.env` committed to Git, or in chat.
3. Generate a **different**, random personal client code (for example, `openssl rand -hex 32`) and set it with `npx wrangler secret put JARVIS_CLIENT_TOKEN`.
4. `npm test` and then `npx wrangler deploy`. Read the printed public HTTPS URL. The chat endpoint is `https://<worker>.<account>.workers.dev/chat`.
5. In JARVIS Android tap **ШІ — підключення**, enter the HTTPS `/chat` endpoint and the client code (NOT the OpenAI API key).
6. Test: say `Привіт, Джарвіс`, then `Як пройшов мій день?` or ask follow-up questions. Use `Очистити контекст ШІ` to start over.

`GET /health` returns a status marker; it does not check billing or whether secrets are set. No secret goes in the health response.

## Security / costs / limitations

- Both `OPENAI_API_KEY` and `JARVIS_CLIENT_TOKEN` must be Worker secrets. `OPENAI_MODEL` is a non-sensitive optional setting, defaulting to `gpt-6-luna`. Model access and prices depend on your OpenAI API project.
- Restrict access to your Worker, keep client code private, rotate it if a phone is lost, configure account-level spending limits and preferably Cloudflare rate limiting. **A single shared client token is suitable only for a private prototype**, not for publicly distributed users; a multi-user application needs proper user authentication and per-user quotas.
- Responses are requested with `store: false`. Server code does not intentionally log messages. They are transmitted to the Worker and OpenAI for processing. The Android app retains up to 14 prior messages **in RAM only**; a new session or clearing context forgets them.
- Current prototype supports **text in / text out**, not streaming or low-latency live conversation. The existing Android SpeechRecognizer and TTS convert voice to text and read answers aloud. Internet is required.
- Device actions are never delegated to the model; commands such as `Увімкни ліхтарик` remain handled locally in Android.
- Cloudflare Workers and OpenAI API may incur **separate charges**; ChatGPT subscriptions do not supply API credits.
- All `/chat` requests require a bearer code, bounded message and conversation history sizes, and use a limited output-token budget. A static code is not a substitute for robust authentication or quota enforcement.

## Tests

```bash
npm test
```

Deploying this server, setting secrets and configuring an HTTPS endpoint are **separate setup steps**; pushing the files to GitHub does not deploy them.

## Без комп'ютера — через браузер телефону

Cloudflare Workers підтримує імпорт GitHub-репозиторію без локального Node.js. У браузері відкрий <https://dash.cloudflare.com/>, потім **Workers & Pages → Create application → GitHub** і дозволь Cloudflare читати репозиторій `andriy2008-max/Jarvis-`. У налаштуваннях збирання вибери **Root directory**: `server/jarvis-ai-worker`, а **Deploy command**: `npx wrangler deploy`. Після успішного імпорту отримаєш адресу `*.workers.dev`. Вона працює для `/health` і `/chat` лише після налаштування секретів.

На сторінці самого Worker додай **runtime secrets**, не відкриті Build variables: `OPENAI_API_KEY` (особистий API-ключ OpenAI, створений через OpenAI Platform) та `JARVIS_CLIENT_TOKEN` (інший випадковий довгий код, щонайменше 20 символів). Для останнього не використовуй пароль OpenAI чи GitHub. Збережи свої секрети приватно — їх **не слід надсилати в чат**. Після додавання змінних перевір HTTPS-адресу `...workers.dev/health` — вона лише підтверджує, що Worker відповідає, але не гарантує активність платного API. Потім у додатку введи `...workers.dev/chat` і окремий код `JARVIS_CLIENT_TOKEN`.

Увага: Cloudflare Git integration автоматично розгортає **сервер** після пушів у GitHub, але **не збирає APK Android**. Workflow APK залишається ручним.

## Розширені можливості ШІ (оновлення)

Після розгортання поточної версії Worker застосунок підтримує:

- **Звичайний діалог:** програмування, переклад, пояснення, написання текстів, планування, математика. У налаштуваннях ШІ обирається глибина міркування: none/low/medium/high.
- **ШІ-пошук:** окремий режим `web` з офіційним інструментом `web_search`. Worker повертає відповідь і HTTPS-посилання на джерела з анотацій моделі. Пошукові запити без звертання «Джарвіс» і далі відкривають браузер без озвучення.
- **Аналіз фото:** користувач вручну обирає JPEG/PNG/WEBP через системний вибір Android; лише цей файл надсилається через Worker до OpenAI.
- **Читання документів:** PDF, TXT, MD, код, CSV/JSON, Word, Excel, PowerPoint. Для файлів ліміт 2,7 МБ; великі фото зменшуються. Сервер не зберігає файли.
- **Створення й редагування зображень:** окремий режим `image` використовує `image_generation`, за замовчуванням `gpt-image-2.5-flare`. Результат відкривається в основному застосунку; лише після натискання «Зберегти зображення» потрапляє у галерею.
- **Виконання Python для перевірки розрахунків:** тільки явно вибраний режим `analysis`, інструмент `code_interpreter` у відокремленому середовищі OpenAI, без доступу до Android/локальних команд.

Режими `web`, `analysis` і `image` можуть оплачуватися **додатково** до токенів моделі. Запити з фото, документами й зображеннями теж збільшують витрати. Для приватного прототипу перевір API billing і встанови бюджетні обмеження в OpenAI Platform. Подальший доступ до служб залежить від тарифу та дозволів API-акаунта.

Файли обираються користувачем; прихованого сканування галереї або файлової системи немає. Система не має довготривалої пам'яті, доступу до приватних облікових записів або автономного контролю телефона. Для таких дій потрібні окремі інтеграції з підтвердженням.

**Розгортання:** зміна коду в GitHub **не гарантує**, що Cloudflare Worker уже працює на новій версії. Перевір або запусти деплой `server/jarvis-ai-worker`, секрети `OPENAI_API_KEY` та `JARVIS_CLIENT_TOKEN`. Телефон має використовувати URL `https://...workers.dev/chat`. Секрети не додаються до GitHub та APK. Новий APK зі змінами Android збирається **лише за окремою командою**.
