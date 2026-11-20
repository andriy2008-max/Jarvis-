# Дімон 0.1.43 — підписаний тестовий кандидат Pixel + Galaxy Watch7

Новий сценарій: перший рядок пошуку Spotify на Pixel після Enter може бути видимим, але без точного текстового Accessibility-вузла. Додана перевірена користувачем координата першого результату (відносний Y=0.2125, тобто близько 510 для 2400px) із суворими умовами. Додано послідовні голосові команди «інша пісня», «не та пісня» для телефону та окремо для годинника, локальні курсори до 10 хвилин.

## GitHub Actions
1. Тести Java контрактів, включно з SpotifyResultCursorTest, SpotifyMusicCommandsTest, SpotifyTrackSearchRulesTest, JarvisWatchLocalCommandsTest.
2. Android compilation + debug AndroidTest package.
3. Синтетичні тести життєвого циклу на Wear OS емуляторі.
4. Підписаний assembleRelease, перевірка пакета com.andriy.jarvis.stable, versionCode=53, versionName=0.1.43 і pin-сертифіката SHA-256.
5. ZIP-артефакт із APK, окремими чек-листами Pixel/Watch7 і контрольними сумами.

**Тестова версія.** Реальний Spotify на Pixel або Samsung Watch7 не перевірений автоматично. Після інсталяції потрібен ручний тест обох сценаріїв. Ніколи не обіцяти старт музики тільки на підставі підтвердження Android жесту.
