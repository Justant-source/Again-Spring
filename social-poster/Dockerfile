FROM mcr.microsoft.com/playwright:v1.44.0-jammy

WORKDIR /app

RUN apt-get update && apt-get install -y \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

COPY package.json ./
# nodemon: dev 핫리로드용 (docker-compose.dev.yml volume mount + command override와 함께 사용)
RUN npm install && npm install -g nodemon

COPY src/ ./src/

EXPOSE 9100

CMD ["node", "src/server.js"]
