# Build stage
FROM gradle:9.7.1-jdk25-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew build --no-daemon

# Package stage
FROM amazoncorretto:26.0.1-alpine
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/nazdar-baby.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/nazdar-baby.jar"]
