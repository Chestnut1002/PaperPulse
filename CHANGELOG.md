# CHANGELOG

## v0.2.0 (2026-09-16,未发布)

新增:
- **REQ-001 用户系统(进行中)**
  - 用户注册 `POST /api/auth/register`:BCrypt 加密、参数校验、用户名/邮箱查重
  - 用户登录 `POST /api/auth/login`:BCrypt 校验后签发 JWT(HS512)
  - 统一错误响应 `GlobalExceptionHandler`:`timestamp / status / error / message`,校验失败附 `fieldErrors`
  - Spring Security 配置:无状态、关闭 CSRF 与表单登录、未认证统一返回 401
  - 配置分层:`application.yml`(可提交)+ `application-local.yml`(本机私有、已忽略)
- 开发工具:
  - `scripts/kill-port.ps1`:清理残留占用端口的开发服务进程

优化:
- `backend/pom.xml`:补充缺失的持久层依赖 `spring-boot-starter-data-jpa` 与 `jjwt` 0.12.6

修复:
- 无

## v0.1.0 (2026-09-10)

新增:
- 项目骨架:backend(Spring Boot)/ ai-service(FastAPI)/ frontend(Vue 3)三端
- 开发规范落地:CLAUDE.md + docs/standards/ 两份规范文档
- 规范配套文件:docs/dev-log.md、docs/requirements.md、experiments/、.env.example、docker-compose.yml
