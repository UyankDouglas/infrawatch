# --- build ---
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -q -B package -DskipTests

# --- runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S agent && adduser -S agent -G agent
COPY --from=build /workspace/target/infrawatch-agent.jar ./agent.jar
USER agent
# Somente saída: nenhuma porta exposta.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/agent.jar"]
