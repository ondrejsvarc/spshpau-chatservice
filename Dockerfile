FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests


FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ARG JAR_FILE=target/chatservice-0.1.1-ALPHA.jar
COPY --from=build /app/${JAR_FILE} app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]