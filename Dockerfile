# ===== Этап сборки =====
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Копируем файлы для загрузки зависимостей
COPY .mvn .mvn
COPY mvnw pom.xml ./

# ✅ Добавляем права на выполнение для mvnw (фикс Permission denied)
RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B

# Копируем исходники и собираем JAR
COPY src src
RUN ./mvnw package -DskipTests -B

# Извлекаем слои для оптимизации кэша
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

# ===== Финальный образ =====
FROM eclipse-temurin:17-jre-jammy AS runner
VOLUME /tmp

# ⚠️ НЕТ WORKDIR — как в твоём рабочем примере!

ARG DEPENDENCY=/app/target/dependency
COPY --from=builder ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=builder ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=builder ${DEPENDENCY}/BOOT-INF/classes /app

# Порт сервиса
EXPOSE 8089

# Переменная окружения
ENV SPRING_PROFILES_ACTIVE=prod

# ✅ ENTRYPOINT в точности как у тебя (только класс и порт другие)
ENTRYPOINT ["java", "-cp", "app:app/lib/*", "org.example.AuthServiceAdminApplication"]