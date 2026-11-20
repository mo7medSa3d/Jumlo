# Build from repo root:
#   docker build -f tools/media-service/Dockerfile -t jumlo-media-service .

FROM python:3.11-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

ARG YTDLP_VERSION=2026.10.26

RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir "yt-dlp==${YTDLP_VERSION}"

FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH" \
    JUMLO_MEDIA_HOST=0.0.0.0 \
    JUMLO_MEDIA_PORT=8787

RUN DEBIAN_FRONTEND=noninteractive apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin jumlo \
    && mkdir -p /app

COPY --from=builder /opt/venv /opt/venv
COPY --chmod=0444 tools/media-service/service.py /app/service.py

WORKDIR /app
USER jumlo

EXPOSE 8787
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD ["python3", "-c", "import os, urllib.request; port = os.environ.get('JUMLO_MEDIA_PORT', '8787'); req = urllib.request.Request(f'http://127.0.0.1:{port}/health', method='HEAD'); tok = os.environ.get('JUMLO_MEDIA_TOKEN') or os.environ.get('JUMLO_SERVICE_TOKEN'); tok and req.add_header('Authorization', f'Bearer {tok}'); urllib.request.urlopen(req, timeout=3).close()"]

ENTRYPOINT ["python3", "/app/service.py"]
