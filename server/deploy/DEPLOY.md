# Выкладка на прод (5.42.99.195)

Процедуры установлены опытным путём 29.08.2026 и проверены живыми выкладками.
До этого они нигде не были записаны — и именно поэтому дважды обнаруживалось,
что на сервере работает не то, что лежит в git (см. «Расхождения» ниже).

> **Правило:** правки вносятся в репозиторий и выкладываются из него.
> Обратный порядок — правка на сервере с намерением «потом закоммитить» —
> и есть источник расхождений: коммит не случается, а следующая выкладка
> затирает живой код старым.

---

## 1. Сайт anipulsetv.ru

Не systemd-юнит, а docker-контейнер `anipulse-web-web-1` (порт `127.0.0.1:8091`).
Собирается из **отдельного репозитория [anipulse-web](https://github.com/Xyling12/anipulse-web)**,
ветка `master`. Каталог `web/app` в этом репозитории к боевому сайту отношения
не имеет (см. `web/app/README.md`).

```bash
# 1. Архив из смердженного master (git archive не тянет node_modules и .next)
cd /path/to/anipulse-web
git fetch origin
git archive --format=tar.gz -o /tmp/anipulse-web-src.tar.gz origin/master
scp /tmp/anipulse-web-src.tar.gz root@5.42.99.195:/tmp/

# 2. Развернуть в новый каталог и переключить (старое НЕ удаляется)
ssh root@5.42.99.195 'set -eu; cd /opt/migrated/anipulse-web
  TS=$(date +%Y%m%dT%H%M%SZ)
  install -d -m 0755 "app.stage.$TS"
  tar -xzf /tmp/anipulse-web-src.tar.gz -C "app.stage.$TS" --no-same-owner --no-same-permissions
  install -m 0644 "app.stage.$TS/migration/deploy/anipulse-web.Dockerfile" "app.stage.$TS/Dockerfile"
  test -s "app.stage.$TS/pnpm-lock.yaml"
  mv app "app.prev.$TS"; mv "app.stage.$TS" app
  echo "бэкап: app.prev.$TS"'

# 3. Сборка и запуск
ssh root@5.42.99.195 'cd /opt/migrated/anipulse-web && docker compose build web && docker compose up -d web'
```

Откат:

```bash
ssh root@5.42.99.195 'cd /opt/migrated/anipulse-web
  P=$(ls -td app.prev.* | head -1); mv app app.failed.$(date +%s); mv "$P" app
  docker compose up -d --build web'
```

> **Не заворачивайте вывод сборки в `| tail`.** Код возврата конвейера берётся от
> последней команды, и упавшая сборка выглядит успешной. Ловилось на практике:
> Docker Hub ответил `429 Too Many Requests` на базовый образ, сборка упала,
> а наружу ушёл ноль. Пишите в файл и печатайте `$?` отдельно.

---

## 2. Шлюз API (/alapi)

systemd-юнит `anipulse-gateway`, один файл `/opt/anipulse/gateway.js`,
процесс от пользователя `anipulse`, слушает только `127.0.0.1:8090`.

```bash
scp server/gateway.js root@5.42.99.195:/tmp/gateway.js.new
ssh root@5.42.99.195 'set -e
  cp /opt/anipulse/gateway.js /opt/anipulse/gateway.js.bak-$(date +%F-%H%M%S)
  cp /tmp/gateway.js.new /tmp/gateway-check.js && node --check /tmp/gateway-check.js
  chown anipulse:anipulse /tmp/gateway.js.new && chmod 644 /tmp/gateway.js.new
  mv /tmp/gateway.js.new /opt/anipulse/gateway.js
  systemctl restart anipulse-gateway && sleep 3 && systemctl is-active anipulse-gateway'
curl -s -o /dev/null -w "%{http_code}\n" https://anipulsetv.ru/alapi/shikimori/api/genres
```

> `node --check` требует расширения `.js` — на файле `gateway.js.new` он падает
> с `ERR_UNKNOWN_FILE_EXTENSION`, и это легко принять за ошибку синтаксиса.
> Поэтому проверка идёт через копию с правильным именем.

На сервере Node 20 — код не должен использовать возможности новее.

Откат:

```bash
ssh root@5.42.99.195 'cp $(ls -t /opt/anipulse/gateway.js.bak-* | head -1) /opt/anipulse/gateway.js
  systemctl restart anipulse-gateway'
```

---

## 3. Caddy

`/etc/caddy/Caddyfile`, 12 хостов. Эталон — [Caddyfile](Caddyfile) в этом каталоге.

```bash
scp server/deploy/Caddyfile root@5.42.99.195:/tmp/Caddyfile.new
ssh root@5.42.99.195 'cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.bak-$(date +%F-%H%M%S)
  cp /tmp/Caddyfile.new /etc/caddy/Caddyfile
  caddy validate --config /etc/caddy/Caddyfile && systemctl reload caddy'
```

Файл в репозитории содержит шапку-комментарий, которой нет на сервере, — при
сверке она игнорируется (см. скрипт проверки).

> На сервере живут ещё четыре проекта (mineage2, maclencat, два такси-домена).
> После правки проверяйте их все, а не только AniPulse.

---

## Расхождения: как они возникали

| Дата | Что нашли |
|---|---|
| 29.08.2026 | В репозитории лежал `Caddyfile` на 3 хоста **без единой CSP**, на сервере — 12 хостов и 5 политик. Его раскатка сняла бы защиту от XSS сразу с нескольких сайтов. |
| 29.08.2026 | `gateway.js` на сервере отставал от `main`: закрытый в git обход rate-limit (перебор паролей) **на боевом оставался открытым**. |

Обе находки случайны — их заметили при подготовке выкладки. Чтобы не полагаться
на случай, есть [check-deploy-drift.sh](check-deploy-drift.sh):

```bash
./server/deploy/check-deploy-drift.sh
```

Скрипт только читает: сверяет контрольные суммы живых файлов с репозиторием и
возвращает ненулевой код при расхождении. Разумно повесить в cron раз в сутки.
