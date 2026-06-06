FROM python:3.11-slim

WORKDIR /app

# System dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc g++ && \
    rm -rf /var/lib/apt/lists/*

# Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Application code
COPY . .

# Storage directories
RUN mkdir -p /app/storage/models /app/storage/data /app/storage/reports

EXPOSE 8082 50051

CMD uvicorn app.main:app --host ${APP_HOST:-0.0.0.0} --port ${APP_PORT:-8082} --workers ${APP_WORKERS:-1}
