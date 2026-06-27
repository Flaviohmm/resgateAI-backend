# --- Build stage ---------------------------------------------------------
# Compila o jar usando uma imagem com Maven + JDK 21 (não depende do Maven local).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache de dependências: copia só o pom primeiro e baixa o offline cache.
COPY pom.xml ./
RUN mvn -q dependency:go-offline

# Copia o código e empacota (pula testes no build de imagem; `mvn test` roda no CI/local).
COPY src/ src/
RUN mvn -q clean package -DskipTests

# --- Runtime stage -------------------------------------------------------
# Imagem final enxuta, só com o JRE e o jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# A plataforma (Railway/Render) injeta a porta em $PORT; o ENTRYPOINT a repassa ao Spring.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
