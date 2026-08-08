# Multi-stage production image for the FlowCRM Spring Boot backend.
# Railway: set service Root Directory to `backend` (this folder is the Docker build context).

# ---- build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Defensive: strip CRLF if the wrapper was checked out on Windows.
RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && ./mvnw -B -q dependency:go-offline -DskipTests

COPY src ./src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/flowcrm-backend.jar /workspace/application.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S flowcrm && adduser -S flowcrm -G flowcrm

COPY --from=build /workspace/application.jar /app/application.jar

USER flowcrm

# Documented default; Railway sets PORT at runtime and application.yml maps it to server.port.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
