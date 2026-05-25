FROM mcr.microsoft.com/playwright:v1.44.0-jammy

WORKDIR /app

RUN apt-get update && apt-get install -y \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

COPY package.json ./
RUN npm install

COPY src/ ./src/

EXPOSE 9000

CMD ["node", "src/server.js"]
