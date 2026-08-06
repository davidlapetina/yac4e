FROM eclipse-temurin:21-jre
WORKDIR /app
COPY backend/target/quarkus-app/lib/ /app/lib/
COPY backend/target/quarkus-app/*.jar /app/
COPY backend/target/quarkus-app/app/ /app/app/
COPY backend/target/quarkus-app/quarkus/ /app/quarkus/
ENV HTTP_PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "quarkus-run.jar"]
