# The jar is built by Maven before this runs (see the pipeline); the image only adds a runtime and a user.
# Pinned by digest; the pipeline refuses a Dockerfile whose FROM has none. Tag when pinned: 21-jre-alpine, 2026-11-01.
FROM eclipse-temurin:24-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app

WORKDIR /app
COPY target/pipeline-service.jar /app/service.jar

USER 10001:10001
ENV PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/service.jar"]
