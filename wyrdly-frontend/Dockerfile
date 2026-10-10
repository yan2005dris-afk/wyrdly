# Stage 1: Build Frontend with pnpm
FROM docker.io/library/node:22-alpine AS build

WORKDIR /app

# Enable pnpm via corepack
RUN corepack enable && corepack prepare pnpm@latest --activate

# Copy package descriptors and lockfile
COPY package.json pnpm-lock.yaml ./

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy source code and build
COPY . .

# Build args are injected by compose.yaml. They map 1:1 to vite.config.ts.
# PROD-EXPECTED values (see .env.prod):
#   VITE_API_BASE_URL=""                                 (SPA → same origin via Nginx)
#   VITE_BACKEND_URL=https://api.wyrdly.yandro.tech      (used only by dev proxy)
#   VITE_WS_URL=wss://api.wyrdly.yandro.tech             (used only by dev proxy)
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ARG VITE_BACKEND_URL="http://localhost:8080"
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL
ARG VITE_WS_URL="ws://localhost:8080"
ENV VITE_WS_URL=$VITE_WS_URL

# Optional: emit sourcemaps in the bundle. PROD-EXPECTED: false
ARG VITE_SOURCEMAP="false"
ENV VITE_SOURCEMAP=$VITE_SOURCEMAP

RUN pnpm run build

# Stage 2: Serve with Unprivileged Nginx Alpine (Rootless)
FROM docker.io/nginxinc/nginx-unprivileged:alpine

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy build artifacts to Nginx docroot
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

USER 101

CMD ["nginx", "-g", "daemon off;"]