FROM node:24-alpine AS builder

WORKDIR /app

# Install dependencies (cached unless the lock file changes)
COPY package.json package-lock.json ./

RUN npm ci

# VITE_BASE_API_URL is left unset: the SPA calls /api on its own origin and nginx proxies it to the gateway
COPY index.html tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts ./

COPY public ./public

COPY src ./src

# Typecheck and bundle into dist/
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine

# conf.d is loaded in the http context (the upstream and the server); the snippet is included by the server. tls.conf is
# not copied: docker-compose.prod.yml mounts it over default.conf
COPY nginx/gateway.conf nginx/default.conf /etc/nginx/conf.d/

COPY nginx/app.conf nginx/security-headers.conf /etc/nginx/snippets/

COPY --from=builder /app/dist /usr/share/nginx/html

# The unprivileged image runs as the nginx user and cannot bind a port below 1024
EXPOSE 8080
