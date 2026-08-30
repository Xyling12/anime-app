#!/bin/bash
# ==============================================================================
# Сверка боевых файлов с репозиторием
# ==============================================================================
# Дважды за одну сессию 29.08.2026 обнаруживалось, что на сервере работает не то,
# что лежит в git: Caddyfile без CSP в репозитории против 5 политик на сервере, и
# отставший gateway.js, из-за которого закрытый в git обход rate-limit на боевом
# оставался открытым. Оба раза — случайно, при подготовке выкладки.
#
# Скрипт только ЧИТАЕТ: забирает содержимое удалённых файлов и сравнивает с
# репозиторием. Ничего не меняет ни на сервере, ни локально.
#
#   ./check-deploy-drift.sh              # проверить
#   HOST=1.2.3.4 ./check-deploy-drift.sh # другой сервер
#
# Коды выхода: 0 — расхождений нет, 1 — есть, 2 — не удалось проверить.
# ==============================================================================

set -uo pipefail

HOST="${HOST:?задайте HOST=<ip или домен сервера>}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

ssh_run() {
  ssh -i "$SSH_KEY" -o BatchMode=yes -o ConnectTimeout=20 "root@$HOST" "$1" 2>/dev/null
}

drift=0
checked=0

red() { printf '\033[31m%s\033[0m\n' "$1"; }
grn() { printf '\033[32m%s\033[0m\n' "$1"; }
yel() { printf '\033[33m%s\033[0m\n' "$1"; }

echo "=============================================="
echo " Сверка прода с репозиторием: $HOST"
echo " Время: $(date -Is)"
echo "=============================================="

if ! ssh_run 'echo ok' | grep -q ok; then
  red "Нет доступа к $HOST по ключу $SSH_KEY — проверить не удалось."
  exit 2
fi

# Нормализация делается ТОЛЬКО на локальной стороне: содержимое с сервера
# сначала складывается во временный файл и лишь потом обрабатывается. Если гонять
# tr через ssh, экранирование проходит два слоя кавычек и незаметно ломается —
# проверка тогда молча сравнивает не то, что нужно (наступал на это здесь же).
#
# Что убирается перед сравнением:
#   * CR — рабочая копия на Windows хранит файлы с CRLF, сервер с LF; без этого
#     расходится сумма у каждого файла и проверка превращается в шум;
#   * строки-комментарии и пустые строки — только в режиме skip-header, для
#     файлов, где в репозитории есть поясняющая шапка, которой нет на сервере.
normalize() {
  local file="$1" mode="$2"
  if [ "$mode" = "skip-header" ]; then
    tr -d '\015' < "$file" | grep -v '^[[:space:]]*#' | grep -v '^[[:space:]]*$'
  else
    tr -d '\015' < "$file"
  fi
}

# $1 — путь на сервере, $2 — файл в репозитории, $3 — имя, $4 — режим сравнения.
compare() {
  local remote="$1" local_file="$2" title="$3" mode="${4:-exact}"
  checked=$((checked + 1))

  if [ ! -f "$REPO_DIR/$local_file" ]; then
    yel "  [?]           $title — нет в репозитории: $local_file"
    return
  fi

  local fetched="$TMP_DIR/$(echo "$remote" | tr '/' '_')"
  ssh_run "cat '$remote'" > "$fetched"
  if [ ! -s "$fetched" ]; then
    yel "  [?]           $title — не удалось прочитать $remote на сервере"
    return
  fi

  local remote_sum local_sum
  remote_sum=$(normalize "$fetched" "$mode" | sha256sum | cut -d' ' -f1)
  local_sum=$(normalize "$REPO_DIR/$local_file" "$mode" | sha256sum | cut -d' ' -f1)

  if [ "$remote_sum" = "$local_sum" ]; then
    grn "  [ок]          $title"
  else
    red "  [РАСХОЖДЕНИЕ] $title"
    echo "                сервер: $remote"
    echo "                репо:   $local_file"
    drift=$((drift + 1))
  fi
}

echo ""
echo "── Файлы"
compare /opt/anipulse/gateway.js         server/gateway.js         "Шлюз API (gateway.js)"
compare /opt/anipulse/proxy-security.js  server/proxy-security.js  "Защита прокси (proxy-security.js)"
compare /opt/anipulse/analytics-store.js server/analytics-store.js "Хранилище аналитики"
compare /etc/caddy/Caddyfile             server/deploy/Caddyfile   "Конфиг Caddy" skip-header

echo ""
echo "=============================================="
echo " Проверено: $checked, расхождений: $drift"
echo "=============================================="

if [ "$drift" -gt 0 ]; then
  red " На сервере работает не то, что лежит в git."
  echo " Прежде чем выкладывать — разберитесь, какая версия верна:"
  echo "   ssh root@$HOST 'cat <путь>' | diff - <файл в репозитории>"
  echo " Слепая выкладка затрёт живой код (так уже едва не потеряли CSP)."
  exit 1
fi

grn " Расхождений нет."
exit 0
