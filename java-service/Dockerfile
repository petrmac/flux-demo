FROM eclipse-temurin:21 AS builder

WORKDIR /home/spring

ADD build/libs/*.jar application.jar

RUN java -Djarmode=layertools -jar application.jar extract

FROM eclipse-temurin:21

RUN groupadd -g 1111 -r spring \
  && useradd -m -d /spring -g spring -s /usr/sbin/nologin -c spring -u 1111 spring

WORKDIR /home/spring

COPY --from=builder --chown=spring:spring /home/spring/dependencies/ ./
COPY --from=builder --chown=spring:spring /home/spring/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /home/spring/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /home/spring/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

USER spring