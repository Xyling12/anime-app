# AniPulse — Android-приложение для аниме (HANDOFF / контекст проекта)

> **Это главный документ для передачи проекта.** Прочитай его целиком, затем `DEVLOG.md`
> (хронология всех изменений). Держи оба актуальными после каждого блока работы.
> **Обновлено: 2026-07-10.** Свежее (07-09/10): OAuth VK+Яндекс работают; выпадающее меню вместо нижней панели (☰ с красной точкой при непрочитанном); соцчасть v2/v3: ЛС, уведомления, @упоминания (подсветка+автодополнение+тап по нику=@обращение), карточка пользователя (тап по аватару: био/статистика/любимый жанр/онлайн), друзья с заявками, онлайн-статус (lastSeen), «О себе» в профиле + синк статистики; пуши WorkManager (каналы ЛС/@упоминания/друзья/новые серии). ⚠️ Эмулятор: перед запуском ОБНУЛЯТЬ HTTP_PROXY/HTTPS_PROXY (иначе крэш gfxstream). SSH-ключ агента установлен на шлюз (беспарольный ssh + allow-правило в .claude/settings.json).

---

## 1. Что это и статус
Нативное Android-приложение (Kotlin/Compose) для просмотра аниме с рус. озвучкой. Бренд **AniPulse**.
**Работает end-to-end на реальном телефоне и эмуляторе**: каталог → тайтл → выбор озвучки → свой плеер → HD-видео, с сохранением прогресса. Распространение — APK (не Google Play).

## 2. Ключевые решения (согласованы с владельцем)
- **Свой нативный плеер** (Media3 ExoPlayer) для всех источников. Тёмная тема, Material 3.
- **Бесплатно, HD**. Владелец платить не готов — только бесплатные источники.
- **Каталог** — Shikimori (id == MAL id).
- **Видео**: за интерфейсом `VideoSource`:
  - **Kodik** — ОСНОВНОЙ. Точное сопоставление по `shikimori_id`, весь каталог, HD 720p, все озвучки.
  - **AniLibria** — доп., нативный FHD 1080 где есть (лицензированных мега-тайтлов у неё нет).
- **Всё через свой шлюз** на VPS (обход блокировок РФ: Shikimori режется по SNI).

## 3. Архитектура и ГЛАВНЫЙ секрет (Kodik)
```
Каталог/метаданные → Shikimori (через шлюз)
Видео → VideoSource → KodikSource (основной) + AnilibriaSource
Таймкоды опенинга/эндинга → AniSkip (через шлюз)
```
**⚡ Kodik API ЖИВ на `kodik-api.com`** (НЕ мёртвый kodikapi.com!):
- `get-player?shikimoriID=X&token=447d179e875efe44217f20d1ee2146be` → ссылка плеера по shikimori_id (точно, весь каталог, HD). Токен публичный, взят из embed-скрипта `kodik-add.com/add-players.min.js`.
- Все озвучки: страница сериала kodikplayer.com содержит `<option data-media-id data-media-hash data-title data-translation-type>` для каждого перевода → готовые ссылки.
- Извлечение m3u8: страница `kodikplayer.com/serial/{id}/{hash}/720p?season=1&episode=N` содержит `vInfo.type/hash/id` конкретной серии → POST `/ftor` (+подписи d_sign/pd_sign/ref_sign из `urlParams`) → `links[quality].src` расшифровать **Caesar-сдвигом (сейчас 18, авто-подбор 1..25) + base64**. CDN = solodcdn.com.
- Всё это делает ШЛЮЗ server-side (kodikplayer доступен только из не-заблокированной сети).

## 4. Шлюз (Node.js на VPS)
**Сервер:** `5.42.99.195` (Timeweb Москва, там же TakerTap). SSH root пароль — см. локальный `SECRETS.local.md` (НЕ в git; ⚠️ пароль засвечен в чате — сменить). Ubuntu 24.04, Node20, **Caddy** (80/443), **RAM всего 961МБ**.
- Сервис: `systemctl {status|restart} anipulse-gateway`. Файл: **`/opt/anipulse/gateway.js`** (бэкапы gateway.js.bak*).
- Домен: **`5-42-99-195.sslip.io`** (бесплатный sslip, валидный TLS, отдельный блок Caddy — TakerTap не трогать; бэкапы Caddyfile.bak*).
- **Сменить домен позже** = только `data/Api.kt` (const GATEWAY) в приложении + блок Caddy.
- **Эндпоинты** (`/alapi/...`):
  - `{shikimori|anilibria|animego|anime365|aniskip|jikan|malcdn|anilistcdn}/*` — прокси (follow-redirects + cookie ddos-guard + кэш 60с).
  - `poster/{id}` → 302 на постер: **AniList(idMal) → Jikan/MAL** (для тайтлов без обложки на Shikimori).
  - `kodik-find?shikimoriId=X` → get-player (одна ссылка).
  - `kodik-dubs?shikimoriId=X` → `[{title,type,link}]` ВСЕ озвучки Kodik.
  - `kodik?link=<url>&episode=N` → `{quality:m3u8}` извлечение серии.

## 5. Что РАБОТАЕТ (проверено на телефоне/эмуляторе)
- Каталог (постеры, фильтры Все/Онгоинги/По рейтингу + **жанры с мультивыбором** (bottom-sheet, 46 жанров Shikimori), бесконечная прокрутка, автоподбор постеров AniList/Jikan).
- Поиск + выпадающие подсказки.
- Страница тайтла: описание, жанры, **много озвучек** (AniLibria + все Kodik: 2x2/AniDUB/AniFilm/…), список серий, галочки просмотренных, «продолжить MM:SS».
- Диалог «Сначала / Продолжить» на начатой серии.
- Плеер: HD видео, **play/pause** (фикс), сикбар, **выбор качества** (дефолт ≤720p чтобы не лагало, 1080 вручную), **переключение озвучки**, **⏮/⏯/⏭ серии по центру**, жесты (тап/двойной тап ±10с/свайпы яркость-громкость), **метки опенинга(жёлтая)/эндинга(оранжевая)** на дорожке, **пропуск опенинга/повтора** (точно по AniSkip + fallback +85с), **кнопка «след.серия» на эндинге**, шестерёнка → **автопропуск опенинга/повтора** (SettingsStore), увеличенный буфер, мгновенный выход, PiP-манифест.
- Прогресс (Room `episode_progress`): место остановки (сохр каждые ~5с и при выходе), отметка watched при >90%.
- Главный экран «Продолжить просмотр» (лента, свежая сборка).
- Плавные кросс-фейд переходы; нижняя навигация Главная/Каталог/Календарь/Моё/Профиль.
- **«Моё»**: сердечко + статусы «Смотрю/В планах/Просмотрено» (чипы на тайтле, фильтры и плашки в сетке; Room v3).
- **Главная-витрина**: баннер-карусель онгоингов, «Для вас» (рекомендации по similar Shikimori), «Популярное», «Высший рейтинг», кнопка чата (заглушка).
- **Профиль**: статистика из Room, настройки плеера (автопропуски + автопереход к след. серии), кнопки входа Shikimori/Яндекс/VK (неактивны до OAuth-ключей).
- **«Календарь»**: Расписание (Shikimori /api/calendar?censored=false, по дням, время МСК) + Обновления (шлюз `/alapi/anilibria-updates`, свежие серии AniLibria; тап → поиск тайтла в Shikimori).

## 6. Что НЕ сделано / дальше
- **Профиль** — заглушка. Решено: вход кнопками **Shikimori / Яндекс / VK** (OAuth). Вход даёт чат/комменты/свой рейтинг 1–10/синхронизацию; просмотр и «Моё» — без входа. ⏳ Владелец должен зарегистрировать OAuth-приложения: shikimori.one/oauth/applications, oauth.yandex.ru, id.vk.com (dev.vk.com) — redirect на шлюз `https://5-42-99-195.sslip.io/alapi/auth/callback` (домен потом сменим). Сервер: /auth-эндпоинты + аккаунты в SQLite (этап 5 вместе с чатом).
- **Статусы** (смотрю/запланировано/…) — избранное есть, статусы нет.
- **Редизайн** (запрос владельца 2026-07-08: «дизайн не нравится, хочу современный») — нужен анализ Anixart/Crunchyroll и модернизация UI.
- **Уведомления о новых сериях** онгоингов (WorkManager).
- ✅ СДЕЛАНО 07-08: аккаунты (ник/почта/пароль, scrypt+HMAC-токены, /alapi/auth/*), общий чат (/alapi/chat, пулинг 4с), комментарии к тайтлам (/alapi/comments), свой рейтинг 1–10 (/alapi/rating, бейдж «♥ avg AniPulse»), 12 аватаров-пресетов (/alapi/avatar). Хранение: JSON-файлы в /opt/anipulse (users/chat/comments/ratings) — при росте мигрировать в SQLite. Осталось: OAuth Shikimori/Яндекс/VK (ждём ключи), комментарии к сериям (сервер готов: ключ `id:epN`), **@теги-упоминания** (автодополнение @ник, подсветка, уведомление тегнутому — решение владельца 07-08), **личные сообщения (ЛС)** user↔user, модерация (админ-роль владельцу: удаление/бан/жалобы), уведомления об ответах/тегах (WorkManager-поллинг + локальные пуши), «Мои обсуждения» в чате, **спойлер-защита** (флаг «спойлер» у комментария + авто-детект слов → текст заблюрен, тап раскрывает — решение владельца), **мат-фильтр** (словарь корней server-side: мат → «***», призывы к насилию → блок отправки + в жалобы админу), подтверждение почты (SMTP).
- Мультисезонность Kodik (сейчас season=1), точность пропуска на Kodik где нет AniSkip.
- FHD ограничен: Kodik макс 720p (бесплатный потолок), 1080 только у AniLibria.
- Смена пакета `com.animelib.app` → `com.anipulse.app` перед релизом; иконка; подписанный release APK.

## 7. Окружение и сборка (Windows, БЕЗ Android Studio)
- Проект: `c:\project\anime-app`. JDK: `C:\dev\jdk\jdk-17.0.19+10`. SDK: `C:\dev\android-sdk`. Gradle: `C:\dev\gradle-8.10.2`.
- **Сборка:** `set JAVA_HOME=C:\dev\jdk\jdk-17.0.19+10` затем `C:\dev\gradle-8.10.2\bin\gradle.bat assembleDebug`. APK: `app\build\outputs\apk\debug\app-debug.apk`.
- SDK ставился вручную из zip (sdkmanager не проходит через локальный прокси) — обновлять так же.
- ⚠️ Прокси машины прописан в `gradle.properties` (systemProp) — нужен для скачивания зависимостей.

## 8. Тестовые устройства
- **Эмулятор (основной для автономных тестов):** AVD `anipulse` (android-35 x86_64). Запуск:
  `set ANDROID_HOME=C:\dev\android-sdk` → `Start-Process C:\dev\android-sdk\emulator\emulator.exe -Args "-avd anipulse -gpu host -no-audio -no-boot-anim"` (Start-Process, иначе PowerShell убивает по stderr; **-gpu host** обязателен, swiftshader падает). AEHD-драйвер установлен (ускорение). Устройство: `emulator-5554`.
- **Реальный телефон владельца** по Wi-Fi ADB (когда доступен): `adb pair <ip:port> <код>` затем `adb connect`. Установка: `adb -s <dev> install -r <apk>`.
- Скриншот: `adb -s <dev> shell screencap -p /sdcard/t.png; adb pull ...` (НЕ `exec-out > file` — PowerShell портит бинарь).

## 9. Ключевые файлы кода
- `data/Api.kt` — адрес шлюза (одна точка смены домена).
- `data/video/`: VideoSource, KodikSource (get-player+dubs+extract), AnilibriaSource, VideoRepository (+AniSkip skipTimes), VideoModels (EpisodeStream.defaultQuality ≤720), PlaybackSession.
- `data/db/`: EpisodeProgress, ProgressDao, AppDatabase (Room).
- `data/SettingsStore.kt` — авто-пропуск (SharedPreferences).
- `ui/player/PlayerScreen.kt` + PlayerViewModel — весь плеер.
- `ui/title/`, `ui/catalog/`, `ui/home/`, `ui/AnimeLibRoot.kt` (навигация).
- `di/NetworkModule.kt`, `di/DatabaseModule.kt`.

## 10. Риски
- Kodik-механика недокументирована (эндпоинт/шифр-сдвиг могут смениться) — авто-подбор сдвига есть, но следить.
- RAM сервера мало (~370МБ свободно) — бэкенд чата держать лёгким (Node+SQLite).
- Правки Caddy/сервера — только аддитивно, с бэкапом, TakerTap не трогать.
