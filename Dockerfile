FROM openjdk:17-jdk-alpine
COPY target/nazdar-baby-2.0-SNAPSHOT.jar nazdar-baby-2.0-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","/nazdar-baby-2.0-SNAPSHOT.jar"]