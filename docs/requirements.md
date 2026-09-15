# 需求管理(REQ)

| 需求编号 | 需求描述 | 优先级 | 状态 |
| ---- | ---- | ---- | ---- |
| REQ-001 | 用户注册/登录(JWT),兴趣标签、收藏、阅读历史、论文评分 | High | Doing |
| REQ-002 | 检索 Agent:自然语言 → arXiv / Semantic Scholar 检索,返回带来源文献列表 | High | TODO |
| REQ-003 | 论文精读问答:锚点+动态截断 / RAG,回答带引用 | High | TODO |
| REQ-004 | 个性化论文推荐:行为反馈闭环 + 探索位(diversity_ratio) | High | TODO |
| REQ-005 | 可解释推荐理由(每条推荐附"为什么推荐") | Medium | TODO |
| REQ-006 | 离线评测:embedding / RecBole SASRec / LLM 打分对比(Recall@K、NDCG@K) | Medium | TODO |

> 状态取值:TODO / Doing / Done。
> 需求背景与详细设计见项目规划文档与 docs/design/。

---

## REQ-001 拆分与进度

| 编号 | 功能点 | 状态 | 完成日期 |
| ---- | ---- | ---- | ---- |
| F1 | 后端骨架就绪 + 数据库连通(JPA 依赖、配置分层、建库) | ✅ Done | 2026-09-16 |
| F2 | 用户注册 `POST /api/auth/register`(BCrypt + 参数校验 + 统一错误响应) | ✅ Done | 2026-09-16 |
| F3 | 用户登录 `POST /api/auth/login`(签发 JWT) | ✅ Done | 2026-09-16 |
| F4 | JWT 鉴权过滤器 + `GET /api/users/me`(鉴权闭环) | ⏳ TODO | — |
| F5 | 兴趣标签 | ⏳ TODO | — |
| F6 | 收藏 / 阅读历史 / 论文评分 | ⏳ TODO | — |

## REQ-002~006 拆分

尚未开始,待 REQ-001 完成后按优先级依次拆分。
REQ-002 的前置调研已完成:因本机网络不可达 `export.arxiv.org`,检索数据源改为
Semantic Scholar 为主、Crossref 为降级备选(详见 docs/dev-log.md)。
