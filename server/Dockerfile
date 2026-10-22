FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home rwun && mkdir /data && chown rwun /data
COPY app.py race_services.py races.json ./
USER rwun
EXPOSE 8000
CMD ["python", "app.py"]
