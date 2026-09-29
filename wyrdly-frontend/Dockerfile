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
