# 需求管理(REQ)

| 需求编号 | 需求描述 | 优先级 | 状态 |
| ---- | ---- | ---- | ---- |
| REQ-001 | 用户注册/登录(JWT),兴趣标签、收藏、阅读历史、论文评分 | High | Done |
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
| F4 | JWT 鉴权过滤器 + `GET /api/users/me`(鉴权闭环) | ✅ Done | 2026-09-16 |
| F5 | 兴趣标签(受控词表 + 1–5 权重 + 全量替换) | ✅ Done | 2026-09-16 |
| F6 | 收藏 / 阅读历史 / 论文评分 | ✅ Done | 2026-09-16 |

> F3 + F4 已于 2026-09-16 用 25 条用例在真实 MySQL + 真实 HTTP 上验收通过(F2 一并回归)。
> 用例明细见 `docs/dev-log.md` 的「2026-09-16(第五次)」一节。
> **这 25 条用例已落成集成测试并入仓库**(见「2026-09-16(第七次)」),跑 `cd backend && mvn test` 即可回归。
> 后续每完成一个功能点,同步补几条用例。
>
> F5 于同日完成并补了 15 条用例(见「2026-09-16(第八次)」),设计文档 `docs/design/F5-兴趣标签.md`。
> F6 于同日完成并补了 25 条用例(见「2026-09-16(第九次)」),设计文档 `docs/design/F6-收藏-阅读历史-评分.md`。
> 当前测试总数 **66 条**,`mvn test` 全绿(F6-25 覆盖并发首次提交同一篇论文)。

**REQ-001 到此六个功能点全部完成。** 下一步:前端(登录 / 兴趣选择器 / 收藏与历史),随后 REQ-002。

## REQ-002~006 拆分

尚未开始,待 REQ-001 完成后按优先级依次拆分。
REQ-002 的前置调研已完成:因本机网络不可达 `export.arxiv.org`,检索数据源改为
Semantic Scholar 为主、Crossref 为降级备选(详见 docs/dev-log.md)。
