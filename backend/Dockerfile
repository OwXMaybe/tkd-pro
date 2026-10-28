FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv

# libgl/libglib are needed by OpenCV/MediaPipe for optional server-side video analysis.
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements-motion.txt ./
ARG WITH_MOTION=true
RUN if [ "$WITH_MOTION" = "true" ]; then pip install --no-cache-dir -r requirements-motion.txt; \
    else pip install --no-cache-dir -r requirements.txt; fi

RUN mkdir -p models && if [ "$WITH_MOTION" = "true" ]; then \
    curl -fsSL -o models/pose_landmarker_full.task \
    https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task; fi

COPY app ./app

RUN useradd --create-home appuser
USER appuser

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers"]
