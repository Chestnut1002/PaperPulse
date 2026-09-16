# CHANGELOG

## v0.2.0 (2026-09-16,未发布)

新增:
- **REQ-001 用户系统(已完成)**
  - 用户注册 `POST /api/auth/register`:BCrypt 加密、参数校验、用户名/邮箱查重
  - 用户登录 `POST /api/auth/login`:BCrypt 校验后签发 JWT(HS512)
  - 鉴权过滤器 `JwtAuthenticationFilter`:解析 `Authorization: Bearer <token>`,认证后写入 `SecurityContext`
  - 当前用户 `GET /api/users/me`
  - 统一错误响应 `GlobalExceptionHandler`:`timestamp / status / error / message`,校验失败附 `fieldErrors`
  - `RestAuthenticationEntryPoint`:未认证的 401 与上述格式一致
  - Spring Security 配置:无状态、关闭 CSRF 与表单登录、未认证统一返回 401
  - 配置分层:`application.yml`(可提交)+ `application-local.yml`(本机私有、已忽略)
- **兴趣标签**(F5)
  - `GET /api/interests`:标签词表,34 个标签分 6 类,含权重区间与数量上限
  - `GET /api/users/me/interests` / `PUT /api/users/me/interests`:读取与**全量替换**(含清空)
  - 受控词表 `InterestTag`:细分研究领域,每项携带对应的 Semantic Scholar 过滤值与检索词,
    供 REQ-002 检索与 REQ-004 推荐直接消费
  - 新表 `user_interest`,含 `(user_id, tag_key)` 唯一约束
  - 权重 1–5,单个用户最多 10 个标签
- **收藏 / 阅读历史 / 论文评分**(F6,REQ-001 收尾)
  - `POST /api/papers`:论文元数据**幂等 upsert**,论文在用户间共享,`(来源, 外部 ID)` 唯一
  - `GET|POST|DELETE /api/users/me/favorites[/{paperId}]`:收藏,重复收藏幂等、取消未收藏返回 404
  - `GET|POST|DELETE /api/users/me/history[/{paperId}]`:阅读历史,**重读累加次数而非追加记录**,可清空
  - `GET|PUT|DELETE /api/users/me/ratings[/{paperId}]`:1–5 星评分,改分覆盖
  - 新表 `paper` / `paper_favorite` / `paper_read_history` / `paper_rating`,后三张各带 `(user_id, paper_id)` 唯一约束
  - `PaperService#resolve`:并发首次提交同一篇论文时,插入失败后重查收敛到同一行
    (实测八个线程并发有七个撞唯一约束),插入走独立的 `REQUIRES_NEW` 事务
  - `applyMetadata` 只覆盖非空字段 —— 一次信息不全的提交不会把已存好的摘要抹掉
  - 列表接口批量取论文(一次查询),避免 N+1
- **集成测试**(`mvn test` 可跑,共 66 条:F3=10、F4=9、E=6、F5=15、F6=25、上下文=1)
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
- `InterestTag.key()` 缺少 `Locale.ROOT`:土耳其语区域的 JVM 上 `toLowerCase()` 会把 `I`
  映射成无点 `ı`,`INFORMATION_RETRIEVAL` 生成的 key 与库中已存的 key 不一致,
  用户会**静默丢失**该兴趣(查询过滤掉、不报错)。本机永远复现不了,靠代码审查发现
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
