FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM eclipse-temurin:17-jre-jammy AS runner
VOLUME /tmp

ARG DEPENDENCY=/app/target/dependency
COPY --from=builder ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=builder ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=builder ${DEPENDENCY}/BOOT-INF/classes /app

EXPOSE 8089

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-cp", "app:app/lib/*", "com.example.auth_service_admin.AuthServiceAdminApplication"]