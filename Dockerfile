FROM maven:3.8.6-openjdk-18-slim AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package -P production

FROM openjdk:17-jdk-alpine
COPY --from=build /usr/src/app/target/nazdar-baby-2.0-SNAPSHOT.jar nazdar-baby-2.0-SNAPSHOT.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/nazdar-baby-2.0-SNAPSHOT.jar"]