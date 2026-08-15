### Build stage ###
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

### Runtime stage ###
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin kcpc
COPY --from=build /build/target/kcpc-mkt-mvp.war /app/app.war
USER kcpc
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.war"]
