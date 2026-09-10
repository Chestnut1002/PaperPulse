# PaperPulse 📄

科研论文研究助手 —— 个性化论文推荐 + Agent 文献检索 + 论文精读问答。

> 面向真实科研场景的论文助手项目。
> 方向:推荐系统 / LLM / Agent 检索 / 可解释 AI(XAI)。
> README 将在 W10-11 完善为英文版。

## 结构

- `backend/` — Java Spring Boot 用户系统(注册登录 JWT、收藏、行为记录)
- `ai-service/` — Python FastAPI(检索 Agent、精读问答、个性化推荐、可解释理由)
- `frontend/` — Vue 3 + Vite 前端
- `docs/` — 开发规范、开发日志、需求管理、架构设计
- `experiments/` — AI 实验记录

## 快速开始

1. 数据库:`docker compose up -d mysql`
2. 后端:`cd backend && mvn spring-boot:run`
3. AI 服务:`cd ai-service && .venv/Scripts/python -m uvicorn app.main:app --port 8000`
4. 前端:`cd frontend && npm install && npm run dev`

环境变量参照 `.env.example`。
