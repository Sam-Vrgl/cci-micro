# ---- Etape de build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY config-server/pom.xml config-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY class-service/pom.xml class-service/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

COPY eureka-server/src eureka-server/src

RUN mvn -q -pl eureka-server -am package -DskipTests

# ---- Etape d'execution ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/eureka-server/target/eureka-server-*.jar app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
