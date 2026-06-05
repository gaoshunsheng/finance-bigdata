# Finance Bigdata Platform - Deployment Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | >= 24.0 | Container runtime |
| Docker Compose | >= 2.20 | Local orchestration |
| Kubernetes | >= 1.28 | Production orchestration |
| kubectl | >= 1.28 | K8s CLI |
| Helm | >= 3.14 | K8s package manager (optional) |
| JDK 17 | Temurin / GraalVM | Building Java services |
| Maven | >= 3.9 | Java build tool |
| Python | 3.11 | Building model-platform |

---

## Quick Start with Docker Compose

### 1. Clone and configure

```bash
git clone <repo-url> && cd finance-bigdata
cp docs/deployment/.env.example .env
# Edit .env with your passwords
vim .env
```

### 2. Build images

```bash
# Build Java services (decision-server, decision-admin)
docker build --build-arg SERVICE_NAME=decision-server \
  -f docs/deployment/Dockerfile.java -t finance-decision-server .

docker build --build-arg SERVICE_NAME=decision-admin \
  -f docs/deployment/Dockerfile.java -t finance-decision-admin .

# Build model-platform
docker build -f model-platform/Dockerfile -t finance-model-platform model-platform/
```

### 3. Start all services

```bash
cd docs/deployment
docker compose up -d
```

### 4. Verify

```bash
docker compose ps
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/health
```

### 5. Stop

```bash
docker compose down          # Stop containers, keep data
docker compose down -v       # Stop and remove all volumes
```

---

## Kubernetes Deployment

### 1. Create namespace and secrets

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
```

Edit secrets before applying:

```bash
kubectl edit secret finance-platform-secrets -n finance-platform
```

### 2. Deploy infrastructure (MySQL, Redis, Elasticsearch)

Use Helm charts or your preferred operators:

```bash
# Example with Bitnami charts
helm repo add bitnami https://charts.bitnami.com/bitnami

helm install mysql bitnami/mysql -n finance-platform \
  --set auth.rootPassword=$MYSQL_ROOT_PASSWORD \
  --set auth.database=credit_platform \
  --set auth.username=credit_admin \
  --set auth.password=$MYSQL_PASSWORD

helm install redis bitnami/redis -n finance-platform \
  --set auth.password=$REDIS_PASSWORD

helm install elasticsearch bitnami/elasticsearch -n finance-platform \
  --set security.enabled=false
```

### 3. Deploy application services

```bash
kubectl apply -f k8s/decision-server-deployment.yaml
kubectl apply -f k8s/decision-admin-deployment.yaml
kubectl apply -f k8s/model-platform-deployment.yaml
```

### 4. Deploy ingress

```bash
kubectl apply -f k8s/ingress.yaml
```

### 5. Verify

```bash
kubectl get pods -n finance-platform
kubectl get svc -n finance-platform
kubectl get ingress -n finance-platform
```

---

## Environment Variable Reference

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/credit_platform?...` | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `credit_admin` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | _(secret)_ | Database password |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `com.mysql.cj.jdbc.Driver` | JDBC driver |

### Redis

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_DATA_REDIS_PASSWORD` | _(secret)_ | Redis password |

### Decision Engine

| Variable | Default | Description |
|----------|---------|-------------|
| `DECISION_ENGINE_CACHE_MAX_SIZE` | `10000` | Max cache entries |
| `DECISION_ENGINE_CACHE_EXPIRE_HOURS` | `24` | Cache TTL |
| `DECISION_ENGINE_RATE_LIMIT_MAX_REQUESTS_PER_SECOND` | `500` | Rate limit threshold |
| `DECISION_ENGINE_AUTH_SECRET_KEY` | _(secret)_ | JWT signing key |
| `DECISION_ENGINE_AUTH_TOKEN_EXPIRE_HOURS` | `24` | Token TTL |

### Model Platform

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_PORT` | `8082` | HTTP listen port |
| `APP_WORKERS` | `4` | Uvicorn worker count |
| `DATABASE_URL` | `mysql+pymysql://...` | SQLAlchemy connection URL |
| `ELASTICSEARCH_URL` | `http://elasticsearch:9200` | Elasticsearch URL |
| `MODEL_STORAGE_PATH` | `/app/storage/models` | Model artifact directory |

---

## Port Mapping

### Docker Compose (localhost)

| Service | Port | URL |
|---------|------|-----|
| decision-server | 8080 | `http://localhost:8080` |
| decision-admin | 8081 | `http://localhost:8081` |
| model-platform | 8082 | `http://localhost:8082` |
| MySQL | 3306 | `localhost:3306` |
| Redis | 6379 | `localhost:6379` |
| Elasticsearch | 9200 | `http://localhost:9200` |

### Kubernetes Ingress

| Path | Service |
|------|---------|
| `/api/decision/*` | decision-server-svc:8080 |
| `/admin/*` | decision-admin-svc:8081 |
| `/model/*` | model-platform-svc:8082 |

---

## Troubleshooting

```bash
# Check container logs
docker compose logs -f decision-server

# Check K8s pod logs
kubectl logs -f deployment/decision-server -n finance-platform

# Describe a failing pod
kubectl describe pod <pod-name> -n finance-platform

# Port-forward for local debugging
kubectl port-forward svc/decision-server-svc 8080:8080 -n finance-platform
```
