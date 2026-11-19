# Build from repo root:
#   docker build -f tools/media-service/Dockerfile -t jumlo-media-service .

FROM python:3.11-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir yt-dlp

FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH" \
    JUMLO_MEDIA_HOST=0.0.0.0 \
    JUMLO_MEDIA_PORT=8787

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin jumlo \
    && mkdir -p /app \
    && chown -R jumlo:jumlo /app

COPY --from=builder /opt/venv /opt/venv
COPY --chown=jumlo:jumlo tools/media-service/service.py /app/service.py

WORKDIR /app
USER jumlo

EXPOSE 8787

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD ["python3", "-c", "import os, urllib.request; req = urllib.request.Request('http://127.0.0.1:8787/health'); tok = os.environ.get('JUMLO_MEDIA_TOKEN'); tok and req.add_header('Authorization', f'Bearer {tok}'); urllib.request.urlopen(req, timeout=3)"]

ENTRYPOINT ["python3", "/app/service.py"]
