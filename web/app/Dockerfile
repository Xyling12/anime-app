# Node 22, а не 20: pnpm 11 подключает встроенный модуль node:sqlite,
# которого в Node 20 нет.
FROM node:22-alpine AS builder

WORKDIR /app
RUN corepack enable && corepack prepare pnpm@11 --activate

# Зависимостями управляет pnpm. Раньше здесь был `npm ci` по package-lock.json,
# но лок описывал давно устаревшую установку и был удалён — сборка по нему
# ставила не то, что работает в проде.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# node-linker=hoisted: обычное дерево node_modules вместо симлинков на store,
# иначе COPY --from в следующий слой перенесёт битые ссылки.
RUN pnpm install --frozen-lockfile --node-linker=hoisted

COPY . .
RUN pnpm build

FROM node:22-alpine AS runner

WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

COPY --from=builder /app/package.json ./
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public

# Без USER процесс в контейнере идёт от root: перехватив его, атакующий
# получает root внутри контейнера. Пользователь node (uid 1000) уже есть
# в образе node:alpine, заводить своего не требуется.
USER node

EXPOSE 3000
CMD ["node_modules/.bin/next", "start"]
