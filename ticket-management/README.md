# Ticket Management

Java 纯后端票务项目，使用 Java 21、Spring Boot、MyBatis、MySQL、Redis、Redisson、RabbitMQ、Sa-Token、支付宝 SDK 和 Spring AI。

交易整改版本默认关闭交易写入和自动迁移。启用前请阅读 [迁移、接口与验证说明](../docs/transaction-hardening.md)，不要直接对现有数据卷重跑初始化 SQL。

业务持久层已统一为 MyBatis Mapper，优先复用原技术栈的约定见仓库根 [AGENTS.md](../AGENTS.md)。最新构建结果与待复验项目见 [MyBatis统一验证记录](../docs/mybatis-unification-validation.md)。

## 本地开发配置

新克隆项目后，在本目录执行：

```powershell
Copy-Item config/application-local.properties.example config/application-local.properties
```

在 `config/application-local.properties` 中填写数据库、Redis、RabbitMQ、邮箱、微信、支付宝及 AI 服务的实际配置。该文件不进入 Git，也不会打入 JAR。

`application.yml` 支持从仓库根目录或本模块目录启动，自动导入上述本地文件。系统环境变量可覆盖本地值。Spring Boot 不会自动读取下面供 Docker Compose 使用的 `.env` 文件。

安装 Java 21 和 Maven 后，可在本目录执行：

```powershell
mvn -DskipTests package
java -jar target/ticket-management-0.0.1-SNAPSHOT.jar
```

运行应用需要可用的 MySQL、Redis、RabbitMQ，以及本地配置中涉及的外部服务。支付和微信回调地址应替换为当前可访问的地址。

## Docker Compose

新克隆项目后，在本目录执行：

```powershell
Copy-Item .env.example .env
```

填写 `.env` 中的本地凭据。连接已有数据卷时，数据库与 RabbitMQ 密码必须使用已有值；改变环境变量不会重置数据卷内的密码。

```powershell
docker compose config --quiet
mvn -DskipTests package
docker compose up -d --build
```

Compose 使用 `.env` 为应用和中间件提供配置。`.dockerignore` 排除本地配置与中间件数据，Docker 镜像只复制打包后的应用 JAR。

服务端口为应用 `8080`、MySQL `3307`、Redis `6379`、RabbitMQ `5672`，RabbitMQ 管理端口为 `15672`。

`docker-data/mysql/init/01-init.sql` 供新建空数据卷时初始化数据库使用，不作为已有数据库的增量迁移脚本。中间件运行数据、上传文件、构建产物和测试用户 Token 文件均不提交到 Git。

## 配置模板

- `config/application-local.properties.example`：直接运行 Spring Boot 时使用的本地配置模板。
- `.env.example`：Docker Compose 使用的环境变量模板。
- `src/main/resources/application.yml`：通用配置和环境变量引用。
- `src/main/resources/application-prod.yml`：容器环境配置。

实际的 `.env`、`config/application-local.properties` 与本地备份均不提交。配置模板不包含实际密钥。
