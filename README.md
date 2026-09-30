# PaperPulse 📄

科研论文研究助手 —— 个性化论文推荐 + Agent 文献检索 + 论文精读问答。

> 方向:推荐系统 / LLM / Agent 检索 / 可解释 AI(XAI)。

## 当前进度

| 需求 | 内容 | 状态 |
| ---- | ---- | ---- |
| REQ-001 | 用户注册/登录(JWT),兴趣标签、收藏、阅读历史、论文评分 | ✅ 已完成 |
| REQ-002 | 检索 Agent:自然语言 → 论文检索,返回带来源文献列表 | 🔨 S1–S6 完成,S7 待做 |
| REQ-003 | 论文精读问答:锚点+动态截断 / RAG,回答带引用 | 🔨 P1 / P1b / P3 完成,P2 / P4 待做 |
| REQ-004 | 个性化论文推荐:行为反馈闭环 + 探索位 | 🔨 R1–R3 完成,R4 待做 |
| REQ-005 | 可解释推荐理由(每条推荐附"为什么推荐") | 🔨 最简版已随推荐接口交付,尚未独立拆分 |
| REQ-006 | 离线评测:Recall@K、NDCG@K 对比实验 | 🔨 E1 / E2 完成,E3 / E4 待做 |

详细进度与拆分见 [`docs/requirements.md`](docs/requirements.md);每日开发记录见 [`docs/dev-log.md`](docs/dev-log.md)。

## 结构

- `backend/` — Java Spring Boot 主服务:用户与鉴权(JWT)、兴趣标签、论文图书馆、
  以及检索 / 精读问答 / 推荐的路由与落库
- `ai-service/` — Python FastAPI:检索 Agent、精读问答、推荐候选与理由生成。
  它**只被 backend 调用**,不直接对外
- `frontend/` — Vue 3 + Vite 前端
- `docs/` — 开发规范、开发日志、需求管理、架构设计
- `experiments/` — AI 实验记录
- `scripts/` — 开发辅助脚本

## 快速开始

### 1. 数据库

```sql
CREATE DATABASE IF NOT EXISTS paperpulse
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> 开发时直接复用本机已安装的 MySQL 8.0。若改用 `docker compose up -d mysql`,
> 注意 compose 里映射的宿主端口同为 3306,会与本机 MySQL 冲突,需改成 `3307:3306`。

### 2. 后端

```bash
cd backend
mvn spring-boot:run          # http://localhost:8080
```

数据库密码等本机凭据放在 `backend/src/main/resources/application-local.yml`(已被 `.gitignore` 忽略)。
从 `application.yml` 可以看到全部可配置项及其环境变量名,也可用环境变量覆盖(如 `DB_PASSWORD`)。

**`JWT_SECRET` 是必填的,且仓库里没有默认值** —— 未配置时后端会拒绝启动并说明原因:

```bash
openssl rand -base64 48      # 生成一个,填进 application-local.yml 的 jwt.secret 或环境变量
```

> 之所以不给默认值:JWT 的签名密钥一旦随仓库公开,**任何人都能签发一个冒充任意用户的 token**。
> 默认值会把「忘了配置」变成「无声地用一个公开密钥运行」,而校验把它变成一眼可见的启动错误。

### 3. 启用提交前钩子(每个克隆各做一次)

```bash
git config core.hooksPath .githooks
```

钩子会在提交前扫描**新增的行**,发现私钥头、常见令牌前缀、或"名字像凭据却写着字面量"的赋值时拒绝提交。
`${VAR}` / `getenv(...)` 这类引用不会被拦(引用不泄露东西) —— 误报太多的话,钩子最后一定会被 `--no-verify` 绕过。

### 4. AI 服务

这一端**只被 backend 调用**,不直接对外。缺 `DEEPSEEK_API_KEY` 时,检索与精读问答会失败。

```bash
cd ai-service
py -m venv .venv                                          # 仅第一次;python 命令不可用时用 py 启动器
.venv/Scripts/python -m pip install -r requirements.txt   # Windows;其他平台是 .venv/bin/python
cp .env.example .env                                      # 然后填上 DEEPSEEK_API_KEY

.venv/Scripts/python -m uvicorn app.main:app --port 8000
```

跑测试另需 `requirements-dev.txt`;离线评测(REQ-006)是单独的 `requirements-eval.txt` ——
它会连带拉起 torch(数百 MB),不做评测就别装。

### 5. 前端

```bash
cd frontend
npm install && npm run dev
```

### 配置放哪里

三个组件**各读各的文件**,填错地方不会报错,只会静默不生效:

| 组件 | 读哪个文件 | 模板 |
| ---- | ---- | ---- |
| Docker Compose(MySQL) | 仓库根目录 `.env` | [`.env.example`](.env.example) |
| ai-service | `ai-service/.env` | [`ai-service/.env.example`](ai-service/.env.example) |
| backend | `backend/src/main/resources/application-local.yml` | 键名见 [`application.yml`](backend/src/main/resources/application.yml) |
| frontend | — 不需要配置 | — |

根目录那份 `.env` **只有 Docker Compose 读**;backend 不读 `.env`,它的数据库口令和
`JWT_SECRET` 要写进 `application-local.yml`(或导出为环境变量)。
用 docker compose 起 MySQL 时,`MYSQL_ROOT_PASSWORD` 与 backend 的 `DB_PASSWORD` 要填同一个值。

[`.env.example`](.env.example) 的开头把这三条路径完整说明了一遍。

## API

除注册与登录外,**所有接口都需要** `Authorization: Bearer <token>`。
`/api/users/me/**` 一律作用于 token 对应的用户,**路径里不出现用户 id**。

### 认证

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| POST | `/api/auth/register` | 注册,返回用户信息(不含密码) |
| POST | `/api/auth/login` | 登录,返回 JWT |
| GET | `/api/users/me` | 返回当前登录用户 |

### 兴趣标签

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| GET | `/api/interests` | 标签词表,按分类返回,含权重区间与数量上限 |
| GET | `/api/users/me/interests` | 当前用户的标签 + 权重 |
| PUT | `/api/users/me/interests` | **全量替换**(传空数组即清空) |

### 论文与个人图书馆

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| POST | `/api/papers` | 提交论文元数据,**幂等 upsert**,返回本地 id |
| GET | `/api/papers/{paperId}` | 按本地 id 取一篇论文(精读页刷新用) |
| GET | `/api/users/me/favorites` | 收藏列表,最近收藏的在前 |
| POST | `/api/users/me/favorites/{paperId}` | 收藏(幂等,重复调用不报错) |
| DELETE | `/api/users/me/favorites/{paperId}` | 取消收藏,没收藏过返回 404 |
| GET | `/api/users/me/history?limit=` | 阅读历史,最近读的在前(默认 100,上限 500) |
| POST | `/api/users/me/history/{paperId}` | 记一次阅读,**次数累加**而非追加记录 |
| DELETE | `/api/users/me/history` | 清空历史 |
| GET | `/api/users/me/ratings` | 评分列表 |
| PUT | `/api/users/me/ratings/{paperId}` | 打分 / 改分,1–5 星 |
| DELETE | `/api/users/me/ratings/{paperId}` | 取消评分,没评过返回 404 |

### 检索、精读问答与推荐

这三个功能走 `backend → ai-service` 两跳:backend 管鉴权与落库,ai-service 管调模型、抓数据源。

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| POST | `/api/papers/search` | 自然语言检索。返回 Agent 拆解出的检索词与理由,命中结果由后端落库 |
| POST | `/api/papers/{paperId}/qa` | 单篇论文精读问答,回答带引用(节号 + 原文摘录) |
| POST | `/api/papers/{paperId}/arxiv-lookup` | 拿标题去 arXiv 反查编号,查到就写进库里(所有用户受益) |
| POST | `/api/papers/from-arxiv` | 粘贴 arXiv 编号或链接,直接打开这篇 |
| GET | `/api/users/me/recommendations` | 按兴趣标签推荐,每条附推荐理由 |

> **这几个接口依赖 ai-service 在跑。** 连不上返回 **503**,上游出错返回 **502** —— 都不是 500:
> 前者重试可能就好了,后者是对方的问题,两者的处理方式不该一样。
>
> ai-service 自身暴露 `GET /health`、`POST /search`、`POST /recommend/candidates`、
> `POST /lookup/arxiv`、`POST /lookup/arxiv-by-title`、`POST /qa`,**只被 backend 调用**,
> 不对外,也不需要鉴权。

错误响应统一为 `{ timestamp, status, error, message }`,参数校验失败时额外带 `fieldErrors`。
未认证返回 401,格式与上同 —— 前端只需一套解析逻辑。

```bash
# 完整链路:登录拿 token → 访问受保护接口
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}' | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

兴趣标签用 `PUT` 提交**最终状态**,不是增量 —— 没出现在请求体里的标签会被删掉:

```bash
curl -s -X PUT http://localhost:8080/api/users/me/interests \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"interests":[{"tag":"recommender_system","weight":5},{"tag":"nlp","weight":3}]}'
```

标签 key 从 `GET /api/interests` 取。权重 1–5,一个用户最多 10 个标签
(上限由接口返回,前端不必硬编码)。

论文与行为数据分两步:先把元数据交给后端换一个**本地 id**,再用这个 id 记行为。

```bash
# 1. 论文落库,拿到本地 id(同一篇提交多次返回同一个 id)
PAPER_ID=$(curl -s -X POST http://localhost:8080/api/papers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"source":"semantic_scholar","externalId":"1706.03762",
       "title":"Attention Is All You Need","authors":["Ashish Vaswani"],
       "publicationYear":2017,"venue":"NeurIPS"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['id'])")

# 2. 收藏 / 记录阅读 / 打分,都用这个 id
curl -s -X POST http://localhost:8080/api/users/me/favorites/$PAPER_ID -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8080/api/users/me/history/$PAPER_ID   -H "Authorization: Bearer $TOKEN"
curl -s -X PUT  http://localhost:8080/api/users/me/ratings/$PAPER_ID \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"score":5}'
```

论文在用户之间是**共享**的 —— 别人收藏过的论文,你直接用同一个 id 即可,不必再提交一次元数据。

## 测试

```bash
cd backend
mvn test
```

集成测试会启动**真实的 Spring 容器 + 真实 Tomcat**(随机端口),用真实 HTTP 请求打过去,
并连接真实 MySQL。这样才能真正验证 BCrypt、JWT 签名和 Spring Security 过滤器链的行为。

**唯一的替身是 ai-service**:检索 / 推荐 / 精读 / 反查这几个测试类用 `StubAiService`
(JDK 自带的 `HttpServer` 起一个假上游)顶掉 Python 侧。也就是说,它们验证的是
**后端在真实 HTTP 往返下的行为**,不验证检索质量 —— 那是 ai-service 自己的 146 条测试的事。

数据库口令与运行应用时是同一套:本机放在 `backend/src/main/resources/application-local.yml`,
其他环境用 `DB_PASSWORD` 环境变量。**`JWT_SECRET` 同样必需**(`JwtProperties` 构造期校验,
未配置即拒绝启动),测试上下文不另外提供它 —— 来源与跑应用时一致。

测试连的是**独立数据库 `paperpulse_test`**,不会碰开发库 `paperpulse`。
该库首次运行自动创建,表结构由 `ddl-auto: create-drop` 管理,跑完即删。

> `TestDatabaseGuard` 会在容器启动**之前**核对库名,连错库直接中止构建。
> 时机很关键:`create-drop` 在容器启动时就会删表重建,等到测试方法里再检查,开发库的表已经没了。

| 测试类 | 条数 | 覆盖 |
| ---- | ---- | ---- |
| `AuthApiIntegrationTest` | 10 | 登录签发 JWT:签名可独立复算、载荷正确、防用户名枚举、计时侧信道 |
| `AuthorizationApiIntegrationTest` | 9 | 受保护接口鉴权:无 token / 签名被篡改 / 已过期 / 用户不存在 |
| `ErrorHandlingIntegrationTest` | 6 | 客户端错误分类:404 / 400 / 405 / 415 不再一律报 500 |
| `InterestApiIntegrationTest` | 16 | 兴趣标签:全量替换语义、改权重时的写入次序、校验先于删数据 |
| `LibraryApiIntegrationTest` | 25 | 论文落库与三类行为:幂等边界、计数而非追加、元数据不被空值擦除、**并发首次提交的冲突恢复** |
| `PaperMatcherTest` | 33 | 跨源合并规则(纯逻辑,不起 Spring) |
| `PaperMergeApiIntegrationTest` | 11 | 跨源合并:同一篇论文因命中不同数据源而在库里存成两行 |
| `DoiApiIntegrationTest` | 8 | 论文的跨源身份(DOI) |
| `ArxivReferenceTest` | 12 | 从用户粘贴的内容里抽 arXiv 编号(纯逻辑) |
| `OpenByArxivApiIntegrationTest` | 9 | 粘贴 arXiv 编号 / 链接直接打开论文 |
| `SearchApiIntegrationTest` | 17 | 自然语言检索接口(REQ-002) |
| `ReadingApiIntegrationTest` | 10 | 精读问答(REQ-003) |
| `ResolveArxivApiIntegrationTest` | 14 | 按标题反查 arXiv 预印本(REQ-003 P3) |
| `RecommendationApiIntegrationTest` | 13 | 个性化推荐(REQ-004) |
| `JwtPropertiesTest` | 5 | JWT 配置的启动期校验(纯逻辑,不起 Spring) |
| `BackendApplicationTests` | 1 | Spring 上下文能起来 |

**后端 199 条**,全绿。另两端:ai-service 146 条(pytest)、前端 113 条(vitest)。

`LibraryApiIntegrationTest` 里有一条专门盯并发:`CyclicBarrier` 对齐八个线程同时提交同一篇新论文,
断言它们全部成功且收敛到同一个 id —— 这条用例是删不掉的,删掉并发恢复代码它就红。

## 开发提示

停止 `mvn spring-boot:run` 后,若端口仍被占用(Windows 上 Ctrl+C 可能残留 Java 子进程):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/kill-port.ps1
```
