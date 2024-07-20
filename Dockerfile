# Build stage
FROM gradle:8.9.0-jdk21-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

# Package stage
FROM amazoncorretto:21.0.4-alpine
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/nazdar-baby.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/nazdar-baby.jar"]
