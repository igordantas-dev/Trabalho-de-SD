# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl financeiro-service -am install -DskipTests \
    && mvn -B -ntp -pl financeiro-service org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/financeiro-service/target/financeiro-service-*.jar /app/app.jar
COPY --from=build --chown=10001:10001 /build/financeiro-service/target/lib/ /app/lib/
USER 10001:10001
EXPOSE 9093
ENTRYPOINT ["java", "-cp", "/app/app.jar:/app/lib/*", "academia.financeiro.ServidorFinanceiroGrpc"]
