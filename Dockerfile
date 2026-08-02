# syntax=docker/dockerfile:1.7

ARG NODE_IMAGE=node:24-bookworm-slim
ARG JAVA_BUILD_IMAGE=eclipse-temurin:25-jdk-noble
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:25-jre-noble

FROM ${NODE_IMAGE} AS frontend-build

WORKDIR /workspace

RUN npm install --global pnpm@11.9.0

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY astro.config.mjs tsconfig.json ./
COPY public ./public
COPY src ./src

ARG SPEAIVE_SITE_URL=http://127.0.0.1:4321
ENV SPEAIVE_SITE_URL=${SPEAIVE_SITE_URL}

RUN pnpm build:frontend \
    && pnpm prune --prod

FROM ${NODE_IMAGE} AS frontend

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=4321

WORKDIR /app

RUN mkdir -p /app/.astro \
    && chown -R node:node /app

COPY --from=frontend-build --chown=node:node /workspace/package.json ./package.json
COPY --from=frontend-build --chown=node:node /workspace/node_modules ./node_modules
COPY --from=frontend-build --chown=node:node /workspace/dist ./dist

USER node

EXPOSE 4321

CMD ["node", "dist/server/entry.mjs"]

FROM ${JAVA_BUILD_IMAGE} AS backend-build

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/backend

COPY backend ./
RUN --mount=type=cache,id=speaive-maven-repository,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM ${JAVA_RUNTIME_IMAGE} AS backend

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 speaive \
    && useradd --uid 10001 --gid speaive --no-create-home --shell /usr/sbin/nologin speaive \
    && mkdir -p /app /data/media /data/inbox/imported /data/inbox/rejected \
    && chown -R speaive:speaive /app /data

WORKDIR /app

COPY --from=backend-build --chown=speaive:speaive \
    /workspace/backend/speaive-blog-start/target/speaive-blog-backend.jar \
    /app/speaive-blog-backend.jar

ENV SPEAIVE_BACKEND_HOST=0.0.0.0 \
    SPEAIVE_BACKEND_PORT=8080 \
    SPEAIVE_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0

USER speaive

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/speaive-blog-backend.jar"]
