FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-dev.txt
COPY sync.py .
COPY tests tests/
RUN python -m pytest -q
CMD ["python", "-u", "sync.py"]
