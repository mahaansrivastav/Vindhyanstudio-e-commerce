# Build: docker build -t vindhyan-backend ./backend
# Run:   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev -e DB_URL=... vindhyan-backend
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 app && mkdir -p /app/data && chown app /app/data
COPY --from=build /src/target/backend-*.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
