FROM amazoncorretto:21.0.1-alpine

WORKDIR /app
#RUN file="$(ls -1 /app/repository )" && echo $file
COPY ./target/nazdar-baby-2.0-SNAPSHOT.jar .

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/nazdar-baby-2.0-SNAPSHOT.jar"]
