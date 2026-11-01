# ========================================================
# Stage 1: Build Angular Frontend
# ========================================================
FROM node:22-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ========================================================
# Stage 2: Build Spring Boot Backend with Bundled Frontend
# ========================================================
FROM eclipse-temurin:21-jdk-jammy AS backend-builder
WORKDIR /app

# Cache Maven wrapper and POM dependencies
COPY management/.mvn management/.mvn
COPY management/mvnw management/pom.xml ./management/
WORKDIR /app/management
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy compiled frontend static assets directly into Spring Boot static resources
COPY --from=frontend-builder /app/frontend/dist/frontend/browser /app/management/src/main/resources/static

# Copy backend source code and build production JAR
COPY management/src ./src
RUN ./mvnw clean package -DskipTests -B

# ========================================================
# Stage 3: Lightweight Production Runtime
# ========================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

# Application data directory (student photos, first-run credentials)
RUN mkdir -p /app/data

COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

COPY --from=backend-builder /app/management/target/*.jar /app/app.jar

ENV CAMPUSFLOW_DATA_DIR=/app/data
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:+UseG1GC"
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
