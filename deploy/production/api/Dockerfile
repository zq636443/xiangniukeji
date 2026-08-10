FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre

WORKDIR /app
COPY rental-api-0.1.0.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
