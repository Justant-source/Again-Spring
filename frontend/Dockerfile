# ── Deps stage ──
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# ── Build stage ──
FROM node:20-alpine AS build
WORKDIR /app

ARG NEXT_PUBLIC_APP_URL
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
# 카카오·네이버는 FE에 미구현 (lib/auth/oauth.ts 의 Provider = 'google').
# BE 쪽 KAKAO_CLIENT_ID / NAVER_CLIENT_ID (application.yml) 와는 별개다 — 그건 유지한다.
# 검색엔진 소유 확인 메타태그 (2026-08-29). NEXT_PUBLIC_* 는 빌드 타임에 인라인되므로
# 반드시 build arg 로 넘겨야 한다 — environment 로만 주면 항상 undefined 가 된다.
ARG NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION
ARG NEXT_PUBLIC_NAVER_SITE_VERIFICATION

ENV NEXT_PUBLIC_APP_URL=$NEXT_PUBLIC_APP_URL
ENV NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID
ENV NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION=$NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION
ENV NEXT_PUBLIC_NAVER_SITE_VERIFICATION=$NEXT_PUBLIC_NAVER_SITE_VERIFICATION

COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ── Runtime stage ──
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs
USER nextjs

COPY --from=build --chown=nextjs:nextjs /app/.next .next/
COPY --from=build --chown=nextjs:nextjs /app/public public/
COPY --from=deps --chown=nextjs:nextjs /app/node_modules node_modules/
COPY --from=build --chown=nextjs:nextjs /app/package.json .

EXPOSE 3000
CMD ["npm", "start"]
