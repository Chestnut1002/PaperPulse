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
- **前端骨架与登录闭环**(FE-1)
  - Vue Router 路由 + 全局登录守卫:未登录访问受保护页跳登录并记住原目标,已登录访问登录页回首页
  - axios 请求层 `api/client.js`:注入 `Authorization: Bearer`、统一错误归一化为 `ApiError`
    (`message` / `status` / `fieldErrors`),401 清登录态并跳登录页
  - **登录接口的 401(凭据错误)不触发登出** —— 与"会话过期"是两种语义
  - 登录态 `stores/auth.js`:localStorage 持久化,刷新不掉登录;存储损坏时按未登录处理
  - 登录页 / 注册页:前端校验规则与后端约束一致,后端 `fieldErrors` 回填到对应表单项;
    注册成功后自动登录(注册接口不返回 token)
  - 首页:挂载时回查 `GET /api/users/me`,验证 token 仍然可用
  - `AppLayout`:登录后外壳(品牌、当前用户、退出)
  - 开发期联调用 Vite 代理 `/api` 到后端,dev 与 preview 均生效,**不需要后端 CORS**
  - Element Plus **按需引入**:首屏 JS 从 1009 KB(gzip 326 KB)降到最大单块 79 KB(gzip 31 KB)
  - 前端测试设施:Vitest + jsdom,14 条单测(请求层 8 条 + 登录态 6 条)
- **视觉规范与兴趣标签选择器**(FE-2)
  - 设计令牌 `styles/theme.css`:墨色三级 + 单一强调色 `#2563EB`、四级字号、三级字重、
    4px 基数间距、权重梯 w1–w5。主色不用 Element Plus 默认的 `#409EFF`(白底仅 3.0:1,
    不满足 AA);对比度一律以最不利底色 `#F6F8FB` 核算
  - Element Plus 整体换色:`--el-*` 变量覆盖(含按 EP 混色规则预计算的 light-3…9)
    + 5 处逐组件补丁(输入框 focus 外环、按钮 hover 变深、卡片去阴影等)
  - 覆盖规则写作 `:root:root` 提高特异性 —— EP 样式按需引入、落在异步 chunk 里,
    加载顺序晚于主题文件,靠顺序压不住
  - **兴趣标签选择器**:6 分类 / 34 标签 / 权重 1–5 / 上限 10;点标签本体切换、
    点第 N 格设权重、← → 微调;右栏「已选强度」按权重降序;未保存改动离开时确认
  - 权重的**三重编码**(文字色恒定 + 底色五档 + 刻度格数),不靠单一颜色通道表达强度
  - 上限是后端硬约束且超限只返回笼统提示,故由界面自己拦住:未选项降权 + 计数变红提示,
    不用弹窗
  - 保存按钮按"是否有未保存改动"启用(而非"选中数是否为 0"),否则**清空后无法保存**;
    0 项时文案为「清空并保存」
  - 登录 / 注册页改版:抽出 `AuthShell` 消除重复;**接口级错误改用表单内提示条,
    不再用 `ElMessage` 浮层**(浮层容易被漏看)
  - 顶栏外壳改版:自绘导航(不用 `el-menu`),导航项由路由表推导
  - 前端测试增至 **43 条**:新增 jsdom 挂载测试,真实渲染视图并驱动交互
    (`InterestView` / `LoginView` / `AppLayout`)
- **检索 Agent(REQ-002,S1–S4)**
  - **Python 侧** `POST /search`:自然语言 → Agent 拆解 → 多源检索 → 带来源的论文列表
    - 查询拆解 `app/agent.py`:一个提示词 + `response_format: json_object`,**不引 LangChain**
      (要的是一次结构化输出,不是一套编排框架)
    - **Agent 只产出检索参数,不产出论文** —— 列表里每一条都来自真实数据库返回,模型没有编造的机会
    - 多源检索 `app/sources.py`:Semantic Scholar 为主、**Crossref 降级备选**,数据归一化成同一形状
    - 年份过滤 + 按外部 ID 去重;年份缺失时视为不符合(用户明确要"2024 年以后"时不塞未知年份的进来)
    - 失败语义:大模型出错 502、所有数据源失败 503
  - **Java 侧** `POST /api/papers/search`:调 ai-service → **由后端落库** → 返回带本地 id 的论文
    - **清偿 F6 遗留技术债**:元数据由后端自己拉取写入,客户端再也无法抢占 `(source, externalId)`
    - 复用 `PaperService.resolve` 的并发安全幂等 upsert,重复检索不会重复入库
    - 只接受元数据齐全的条目,一条坏数据不该毁掉整次检索
    - 上游故障 502 / 503 而非 500 —— 让"依赖挂了"和"我们的 bug"在监控里分得开
    - 复用 `app.ai-service.base-url` 配置,连接超时 3s、读超时 120s
  - **安全修复**:检索接口此前会在"合法签名 + 用户已被删除"时放行。
    现与 `GET /api/users/me` 一致,回查用户不存在则 401
  - **前端检索页** `SearchView.vue`:一句话输入 + 例子引导 + 检索中的明确等待提示(实测约 10 秒)
    - **显示 Agent 的拆解**(检索词 / 时间范围 / 理由),让"为什么搜出来的是这些"可见
    - 结果卡片带收藏按钮(进入时批量取收藏状态,按钮显示"已收藏"并可取消)
    - 查询同步到地址栏,刷新不丢结果
  - 补 `SEMANTIC_SCHOLAR_API_KEY` 支持(匿名共享池实测持续 429)
- **「我的论文」管理页**(FE-3,REQ-001 前端收尾)
  - 收藏 / 阅读历史 / 评分三个标签页,各自带条数;三种空态说的是三句不同的话
  - 收藏页可直接评分 —— 若评分只能对"已评分的论文"做,用户永远打不出第一个分
  - 历史页显示读过几次、最近何时读,可一键清空(带确认)
  - **检索页点开论文链接即记一次阅读**:否则阅读历史永远是空的。
    失败不打扰用户(不 await、不弹提示),但会在控制台留痕 —— 弱信号不该挡住"去看论文"
  - 评分控件改用强调色(EP 默认金色星会破坏"全站只有一个蓝"的配色)
  - 抽出共享物:`utils/paper.js`(作者/元信息/时间格式化)与论文行样式,检索页与管理页共用
- 开发工具:
  - `scripts/kill-port.ps1`:清理残留占用端口的开发服务进程

优化:
- `backend/pom.xml`:补充缺失的持久层依赖 `spring-boot-starter-data-jpa` 与 `jjwt` 0.12.6

修复:
- **`GET /api/interests` 会放行「签名合法但用户已被删除」的 token**:鉴权过滤器只验签不查库,
  "用户是否还在"由各接口自己负责,而用不到用户实体的接口最容易漏(检索接口此前也漏过一次)。
  现已与 `GET /api/users/me` 一致,查不到抛 401
- **论文标题里的 HTML 转义未还原**:`&amp;` 会原样显示在界面上(Crossref 的标题里并不罕见)。
  原先只对摘要做了转义还原(为去 JATS 标签),标题与会议名漏了 ——
  同一数据源的同一种脏数据不会只出现在一个字段上
- **同一篇论文因命中不同数据源而被存成两行**:论文的唯一约束是 `(来源, 外部 ID)`,
  而检索会在 Semantic Scholar 与 Crossref 之间降级 —— 同一个查询昨天走 A、今天走 B,
  同一篇论文就各存一行,用户的收藏 / 评分 / 阅读历史随之分裂到两行上
  - 引入 **DOI 作为跨源身份**:`paper` 表加 `doi` 列 + 唯一索引(可空,没有 DOI 的论文互不冲突)
  - `PaperService.resolve`:有 DOI 优先按 DOI 查,查不到再退回 `(来源, 外部 ID)`
  - DOI 规范化(小写、剥前缀、只接受 `10.xxx/yyy` 的形状)—— **形状不对返回 null 而非原样返回**,
    兜底一个假身份会把两篇不同的论文合并成一篇,比多存一行糟糕得多
  - S2 侧改为请求 `externalIds` 并从中取 DOI(DOI 不在顶层,取错地方功能会悄悄失效)
  - 已有行会在下次遇到时把 DOI 回填上去
  - **已知局限**:arXiv 预印本与正式发表版是两个不同的 DOI(`10.48550/arxiv.*` vs 期刊 DOI),
    因此不会被合并。合并它们需要按标题做模糊匹配,而同名论文真实存在,
    错误合并是不可逆的数据损坏 —— 留作独立方案评估
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
