# Spring Boot backend. Build: docker build -t agri-backend backend/
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/backend-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/app.jar app.jar
EXPOSE 8080
# Required env: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, ML_SERVICE_URL, CORS_ALLOWED_ORIGINS.
# Optional: GEMINI_API_KEY (AI advisory; without it a labelled template is used), GEMINI_MODEL.
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
