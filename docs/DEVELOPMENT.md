# Сборка и разработка

## Требования

| Компонент | Требуется |
|---|---|
| Android-приложение | JDK 17, Android SDK 35, Build Tools 35.0.1 |
| API-шлюз | Node.js 20+ (внешних зависимостей нет) |
| Веб-клиент | Node.js 20+, pnpm |

## Первый запуск

```bash
git clone https://github.com/Xyling12/anime-app.git
cd anime-app
git config core.hooksPath .githooks   # проверка коммитов на секреты
cp gradle.properties.example gradle.properties
```

## Android

```bash
./gradlew assembleDebug        # отладочная сборка → app/build/outputs/apk/debug/
./gradlew test                 # юнит-тесты
./gradlew lint                 # Android Lint
./gradlew assembleRelease      # release-сборка
```

Release подписывается ключом из `keystore.properties` в корне проекта:

```properties
storeFile=/абсолютный/путь/anipulse.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Файл в репозиторий не входит. Если его нет, release соберётся неподписанным —
сборка не упадёт.

Версия приложения задаётся в [`app/build.gradle.kts`](../app/build.gradle.kts)
(`versionCode` / `versionName`), версии зависимостей — в
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml).

## API-шлюз

```bash
node --test server/tests       # весь набор тестов
node --test server/tests/sync.test.js
```

Тесты используют встроенный раннер Node.js и поднимают шлюз во временном
каталоге, поэтому боевые данные не затрагиваются.

Для локального запуска шлюзу нужны файлы конфигурации рядом с `gateway.js`
(токены источников, SMTP, OAuth). Их состав описан в
[`server/deploy/DEPLOY.md`](../server/deploy/DEPLOY.md); в репозитории есть
только образец `server/deploy/legal.example.json`.

## Веб-клиент

```bash
cd web/app
pnpm install
pnpm dev      # http://localhost:3000
pnpm lint
pnpm build
```

## Конвенции

- Ветки: `feat/…`, `fix/…`, `docs/…`, `ops/…`.
- Коммиты — Conventional Commits, тело на русском.
- Комментарии и пользовательские строки — на русском, идентификаторы — на английском.
- Секреты, IP-адреса и домены боевой инфраструктуры в код и документацию не попадают:
  только переменные окружения и плейсхолдеры. Это проверяют
  [`.githooks/pre-commit`](../.githooks/pre-commit) и
  [CI](../.github/workflows/security.yml) (Gitleaks + Semgrep).

## Перед пул-реквестом

```bash
./gradlew test lint
node --test server/tests
cd web/app && pnpm lint && pnpm build
```
