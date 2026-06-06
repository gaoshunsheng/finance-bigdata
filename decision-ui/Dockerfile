# ============================================================================
# Multi-stage Dockerfile for decision-ui (Vue 3 + Nginx)
# ============================================================================
# Build:
#   docker build -t finance-decision-ui .
# ============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build with Node.js
# ---------------------------------------------------------------------------
FROM node:20-alpine AS builder

WORKDIR /build

# Layer 1: Install dependencies (cached unless package.json changes)
COPY package.json package-lock.json* ./
RUN npm install --registry=https://registry.npmmirror.com

# Layer 2: Build source
COPY . .

# Build-time API base URL (can be overridden via --build-arg)
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2: Serve with Nginx
# ---------------------------------------------------------------------------
FROM nginx:1.25-alpine

LABEL maintainer="finance-bigdata-team"
LABEL service="decision-ui"
LABEL description="Finance Bigdata Platform - Admin Frontend"

# Remove default nginx config
RUN rm -f /etc/nginx/conf.d/default.conf

# Copy custom nginx config
COPY <<'EOF' /etc/nginx/conf.d/default.conf
server {
    listen 3000;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript image/svg+xml;
    gzip_min_length 256;

    # SPA fallback — all routes serve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Static assets with long cache
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # Health check endpoint
    location /health {
        access_log off;
        return 200 '{"status":"UP"}';
        add_header Content-Type application/json;
    }
}
EOF

# Copy built assets from builder
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=10s \
    CMD wget -qO- http://localhost:3000/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
