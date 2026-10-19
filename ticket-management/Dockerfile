FROM eclipse-temurin:21-jdk

WORKDIR /app

# 复制打包好的 jar 包
COPY target/ticket-management-0.0.1-SNAPSHOT.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
