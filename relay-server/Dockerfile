FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json tsconfig.json ./
RUN npm ci

COPY src ./src
RUN npm run build

FROM node:20-alpine AS runner

WORKDIR /app

COPY package*.json ./
RUN npm ci --only=production

COPY --from=builder /app/dist ./dist

ENV PORT=8080
ENV TCP_RANGE_START=15000
ENV TCP_RANGE_END=16000

EXPOSE 8080
EXPOSE 15000-16000

CMD ["node", "dist/server.js"]
