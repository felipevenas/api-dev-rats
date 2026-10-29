FROM eclipse-temurin:25-jdk AS build

WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
RUN addgroup -S devstreak && adduser -S devstreak -G devstreak
COPY --from=build /app/target/*.jar app.jar
USER devstreak

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
