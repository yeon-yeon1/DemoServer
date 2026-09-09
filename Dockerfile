FROM eclipse-temurin:21-jre
WORKDIR /app

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 힙 상한을 컨테이너 mem_limit에 비례해 잡는다. -Xmx를 숫자로 박아두면
# 호스트를 옮길 때마다 다시 계산해야 하지만, 이 방식은 compose만 고치면 된다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# base 이미지에 curl이 이미 들어있어 RUN 레이어가 필요 없다.
# start-period는 JVM + Hibernate 기동 시간을 감안한 값.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
