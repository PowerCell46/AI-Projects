# syntax=docker/dockerfile:1

# ---- Build stage: produce the static SPA bundle with Vite ----
FROM node:22-alpine AS build
WORKDIR /app


# The API base URL is baked into the bundle at build time. The default is a same-origin
# relative path, so the browser calls the edge proxy (which forwards /api to the backend).
# Override it only to point the SPA at a different API origin.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Install dependencies in their own cached layer keyed on the lockfile.
COPY package.json package-lock.json ./
RUN npm install

COPY . .
RUN npm run build


# ---- Runtime stage: serve the static bundle with nginx ----
FROM nginx:stable-alpine AS runtime

RUN apk add --no-cache curl

# Replaces the stock server block with our SPA config (history-API fallback + asset caching).
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -sf http://localhost:8080/ || exit 1
