FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN addgroup --system hostvero && adduser --system --ingroup hostvero hostvero
WORKDIR /app
COPY --from=build /workspace/target/guest-platform-0.0.1-SNAPSHOT.jar /app/app.jar
USER hostvero
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/app.jar --server.port=${PORT:-8080}"]
