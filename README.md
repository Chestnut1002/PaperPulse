# PaperPulse 📄

科研论文研究助手 —— 个性化论文推荐 + Agent 文献检索 + 论文精读问答。

> 方向:推荐系统 / LLM / Agent 检索 / 可解释 AI(XAI)。

## 当前进度

| 需求 | 内容 | 状态 |
| ---- | ---- | ---- |
| REQ-001 | 用户注册/登录(JWT),兴趣标签、收藏、阅读历史、论文评分 | ✅ 已完成 |
| REQ-002 | 检索 Agent:自然语言 → 论文检索,返回带来源文献列表 | ⏳ 待开始 |
| REQ-003 | 论文精读问答:锚点+动态截断 / RAG,回答带引用 | ⏳ 待开始 |
| REQ-004 | 个性化论文推荐:行为反馈闭环 + 探索位 | ⏳ 待开始 |
| REQ-005 | 可解释推荐理由(每条推荐附"为什么推荐") | ⏳ 待开始 |
| REQ-006 | 离线评测:Recall@K、NDCG@K 对比实验 | ⏳ 待开始 |

详细进度与拆分见 [`docs/requirements.md`](docs/requirements.md);每日开发记录见 [`docs/dev-log.md`](docs/dev-log.md)。

## 结构

- `backend/` — Java Spring Boot 用户系统(注册登录 JWT、收藏、行为记录)
- `ai-service/` — Python FastAPI(检索 Agent、精读问答、个性化推荐、可解释理由)
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

### 3. AI 服务

```bash
cd ai-service
.venv/Scripts/python -m uvicorn app.main:app --port 8000
```

### 4. 前端

```bash
cd frontend
npm install && npm run dev
```

环境变量参照 [`.env.example`](.env.example)。

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
| GET | `/api/users/me/favorites` | 收藏列表,最近收藏的在前 |
| POST | `/api/users/me/favorites/{paperId}` | 收藏(幂等,重复调用不报错) |
| DELETE | `/api/users/me/favorites/{paperId}` | 取消收藏,没收藏过返回 404 |
| GET | `/api/users/me/history?limit=` | 阅读历史,最近读的在前(默认 100,上限 500) |
| POST | `/api/users/me/history/{paperId}` | 记一次阅读,**次数累加**而非追加记录 |
| DELETE | `/api/users/me/history` | 清空历史 |
| GET | `/api/users/me/ratings` | 评分列表 |
| PUT | `/api/users/me/ratings/{paperId}` | 打分 / 改分,1–5 星 |
| DELETE | `/api/users/me/ratings/{paperId}` | 取消评分,没评过返回 404 |

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
并连接真实 MySQL —— 不用 mock。这样才能真正验证 BCrypt、JWT 签名和 Spring Security 过滤器链的行为。

数据库口令与运行应用时是同一套:本机放在 `backend/src/main/resources/application-local.yml`,
其他环境用 `DB_PASSWORD` 环境变量。

测试连的是**独立数据库 `paperpulse_test`**,不会碰开发库 `paperpulse`。
该库首次运行自动创建,表结构由 `ddl-auto: create-drop` 管理,跑完即删。

> `TestDatabaseGuard` 会在容器启动**之前**核对库名,连错库直接中止构建。
> 时机很关键:`create-drop` 在容器启动时就会删表重建,等到测试方法里再检查,开发库的表已经没了。

| 测试类 | 覆盖 |
| ---- | ---- |
| `AuthApiIntegrationTest` | 登录签发 JWT:签名可独立复算、载荷正确、防用户名枚举、计时侧信道 |
| `AuthorizationApiIntegrationTest` | 受保护接口鉴权:无 token / 签名被篡改 / 已过期 / 用户不存在 |
| `ErrorHandlingIntegrationTest` | 客户端错误分类:404 / 400 / 405 / 415 不再一律报 500 |
| `InterestApiIntegrationTest` | 兴趣标签:全量替换语义、改权重时的写入次序、校验先于删数据 |
| `LibraryApiIntegrationTest` | 论文落库与三类行为:幂等边界、计数而非追加、元数据不被空值擦除、**并发首次提交的冲突恢复** |

当前共 66 条用例。其中 F6-25 专门盯并发:`CyclicBarrier` 对齐八个线程同时提交同一篇新论文,
断言它们全部成功且收敛到同一个 id —— 这条用例是删不掉的,删掉并发恢复代码它就红。

## 开发提示

停止 `mvn spring-boot:run` 后,若端口仍被占用(Windows 上 Ctrl+C 可能残留 Java 子进程):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/kill-port.ps1
```
