# CHANGELOG

## v0.2.0 (2026-09-16,未发布)

新增:
- **REQ-001 用户系统(进行中)**
  - 用户注册 `POST /api/auth/register`:BCrypt 加密、参数校验、用户名/邮箱查重
  - 用户登录 `POST /api/auth/login`:BCrypt 校验后签发 JWT(HS512)
  - 鉴权过滤器 `JwtAuthenticationFilter`:解析 `Authorization: Bearer <token>`,认证后写入 `SecurityContext`
  - 当前用户 `GET /api/users/me`
  - 统一错误响应 `GlobalExceptionHandler`:`timestamp / status / error / message`,校验失败附 `fieldErrors`
  - `RestAuthenticationEntryPoint`:未认证的 401 与上述格式一致
  - Spring Security 配置:无状态、关闭 CSRF 与表单登录、未认证统一返回 401
  - 配置分层:`application.yml`(可提交)+ `application-local.yml`(本机私有、已忽略)
- **集成测试**(25 条验收用例入库,`mvn test` 可跑)
  - 起真实容器 + 真实 Tomcat + 真实 MySQL,用真实 HTTP 请求验证,不用 mock
  - `ApiClient`:基于 JDK `HttpClient` 的测试客户端(`TestRestTemplate` 在 Spring Boot 4 中已移除)
  - `TokenForger`:用 `javax.crypto.Mac` **独立**签 JWT,不借助 jjwt —— 否则等于用被测实现验证它自己
  - `TestDatabaseGuard`:容器启动前核对库名,防止 `create-drop` 误删开发库数据
  - `application-test.yml`:测试库 `paperpulse_test`,与开发库完全隔离
- 开发工具:
  - `scripts/kill-port.ps1`:清理残留占用端口的开发服务进程

优化:
- `backend/pom.xml`:补充缺失的持久层依赖 `spring-boot-starter-data-jpa` 与 `jjwt` 0.12.6

修复:
- 四类客户端错误返回 500 而非正确状态码:`GlobalExceptionHandler` 改为继承
  `ResponseEntityExceptionHandler`,覆盖 `handleExceptionInternal` 统一响应格式
  - 路径不存在 → 404(原 500)
  - 请求体不是合法 JSON → 400(原 500)
  - 方法不支持 → 405(原 500)
  - Content-Type 不支持 → 415(原 500)

## v0.1.0 (2026-09-10)

新增:
- 项目骨架:backend(Spring Boot)/ ai-service(FastAPI)/ frontend(Vue 3)三端
- 开发规范落地:CLAUDE.md + docs/standards/ 两份规范文档
- 规范配套文件:docs/dev-log.md、docs/requirements.md、experiments/、.env.example、docker-compose.yml
