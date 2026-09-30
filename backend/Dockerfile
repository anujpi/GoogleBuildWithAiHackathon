FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar /app.jar
# Render/Cloud Run inject PORT; 8080 locally.
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app.jar"]
