# 开发日志

# 2026-09-10

## 本次目标
项目初始化:创建 PaperPulse 根目录,落地两份开发规范,搭建 backend / ai-service / frontend 三端骨架,本地 git 初始化并准备 GitHub 建仓。

## 完成内容
- 创建 D:\develop\PaperPulse 目录结构
- 两份开发规范写入 docs/standards/,并合并进根目录 CLAUDE.md(每次会话自动加载)
- backend:start.spring.io 生成 Spring Boot 4.1.1 骨架(webmvc/security/validation/mysql,Java 21 目标)
- ai-service:FastAPI 0.141 骨架 + /health 接口;venv 经 `py` 启动器创建,依赖走清华 PyPI 镜像
- frontend:Vite + Vue 3 骨架(npm create vite)
- 规范配套文件:README / CHANGELOG / .gitignore / .env.example / docker-compose.yml(mysql)/ docs(dev-log、requirements、design)/ experiments
- GitHub 公开仓创建并推送:https://github.com/Chestnut1002/PaperPulse(gh CLI,main 分支)

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| 全部 | 新增(项目初始化) |

## 技术方案
monorepo 三端:backend(Spring Boot 4.1,Java 21 目标,com.paperpulse)/ ai-service(FastAPI + venv)/ frontend(Vite + Vue 3)。MySQL 由 docker-compose 提供(待 REQ-001 接入)。开发规范由根目录 CLAUDE.md 自动加载。

## 遇到问题
1. `python` 命令指向 Windows 商店占位程序(WindowsApps),`python -m venv` 静默失败 —— 改用 `py` 启动器(D:\Python\python.exe)创建 venv
2. `gh auth login` 在工具环境中无法完成交互(无真实终端)—— 已请用户在自建终端执行设备码登录

## 解决方案
- venv:`py -m venv .venv`;pip 走清华镜像 `-i https://pypi.tuna.tsinghua.edu.cn/simple`
- gh 登录:用户终端手动执行设备码登录(账号 Chestnut1002,凭据存 Windows keyring);注意**工具沙箱会隔离 gh 凭据,后续所有 gh/git push 命令需在沙箱外(dangerouslyDisableSandbox)执行**

## 测试结果
- backend:`mvn compile` + `mvn test` 通过(exit 0,Spring 上下文正常启动;security 为默认生成密码,REQ-001 时替换)
- ai-service:`GET /health` → 200 `{"status":"ok","service":"ai-service","version":"0.1.0"}`
- frontend:`npm install` + `npm run build` 通过(1.44s)

## 下一步计划
- REQ-001 用户注册/登录(后端 JWT + MySQL)
- 路线图 W1:精读相关领域论文与 PaperAgent 源码

# 2026-09-11

## 本次目标
将用户指定的两份开发规范打包为 dev-standard agent skill(用户级 + 项目级各一份),并解决 GitHub 推送问题。

## 完成内容
- 创建用户级 skill:C:\Users\ASUS\.claude\skills\dev-standard\SKILL.md(全局可用)
- 创建项目级 skill:.claude/skills/dev-standard/SKILL.md(随仓库提交)
- 项目级 skill 提交并推送至 GitHub
- 合并远程网页端 README 修改(以远程版本为准)
- 部署 PaperAgent v2.1.4 至 D:\develop\paperagent\(W1 论文调研工具;Q&A 模块 + DeepSeek,key 由用户在 Web 设置页自填;每日推荐暂不启用)

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| .claude/skills/dev-standard/SKILL.md | 新增(开发规范合并执行版) |
| docs/dev-log.md | 追加本条目 |

## 技术方案
skill 将两份规范合并为 8 步执行流水线(会话检查 → 项目分析报告 → 代码修改计划 → 开发流程 → 测试报告 → Code Review → Git 提交说明 → 文档维护/AI 实验记录)。

## 遇到问题
1. github.com:443 被墙,HTTPS push 持续失败(连接超时/重置),但 api.github.com、codeload 可达
2. 远程出现网页端提交(157dfcf Update README.md),与本地 skill 提交分叉
3. PaperAgent Release 资产经 gh 直下时网络中断,两个 exe 被截断(大小不足官方一半,运行报 Exec format error)

## 解决方案
- 远程地址由 HTTPS 改为 SSH:`git remote set-url origin git@github.com:Chestnut1002/PaperPulse.git`(github.com:22 可达,已有 ed25519 密钥且已绑定账号)
- `git pull --rebase origin main` 线性合并后推送,无冲突
- 改用 api.github.com 资产直连接口(curl -L --retry + gh auth token)重下,并用官方 sha256 校验通过

## 测试结果
- SSH 认证:`Hi Chestnut1002! You've successfully authenticated`
- push 成功,main 与 origin/main 完全同步

## 下一步计划
- REQ-001 用户注册/登录(后端 JWT + MySQL)
- 路线图 W1:精读相关领域论文与 PaperAgent 源码

# 2026-09-12

## 本次目标
将三份项目规划文档纳入本地项目,并解决公开仓库的隐私边界问题。

## 完成内容
- 三份规划文档(主规划 / PaperAgent 结合方案 / 源码阅读指南)复制到本地 `docs-plans/` 目录
- `docs-plans/` 加入 .gitignore,不进入任何 Git 仓库
- 规划关键信息写入 Claude Code 持久记忆(项目目标、PaperAgent 借鉴点、文档位置)

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| .gitignore | 新增 docs-plans/ 忽略规则 |
| docs/dev-log.md | 追加本条目 |
| docs-plans/*(本地,不提交) | 新增三份规划文档副本 |

## 技术方案
用户决定:规划文档只存本机、不进 Git。原因是仓库公开,规划中含个人规划等敏感内容;最终发布时对外只保留代码与公开文档。三份文档保留桌面原件,本地副本放 docs-plans/ 供开发查阅。

## 遇到问题
仓库当前为公开状态(visibility: public),若直接提交敏感文档当天即暴露。

## 解决方案
docs-plans/ 走 .gitignore 本地保留;发布前无需历史清理,仓库历史天然干净。

## 测试结果
- `git status` 确认 docs-plans/ 未被跟踪

## 下一步计划
- REQ-001 用户注册/登录(后端 JWT + MySQL)
- 路线图 W1:精读相关领域论文与 PaperAgent 源码(按 docs-plans/ 中的阅读指南推进)

# 2026-09-16

## 本次目标
清除公开仓库中的个人申请相关信息(工作区 + 全部 Git 历史),删除新出现的 Codex 规范文件。

## 完成内容
- 工作区脱敏:CLAUDE.md 项目目标表述、docs/dev-log.md 三处、.gitignore 注释 → 中性技术描述
- Git 历史重写:git-filter-repo blob 回调逐文件替换,**8 个提交全部重写**后强制推送
- 删除 AGENTS.md 与 .agents/(CC Switch 同步生成的 Codex 版规范,含同样信息,用户决定删除)
- 全历史扫描验证:敏感词命中数为 0;GitHub 公开内容确认已脱敏

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| CLAUDE.md | 目标表述脱敏 |
| docs/dev-log.md | 相关措辞脱敏 |
| .gitignore | 注释脱敏 |
| AGENTS.md / .agents/ | 删除(未入库) |

## 技术方案
隐私边界:仓库公开,个人申请信息不得出现在任何入库内容中。工作区用 `scratch/sanitize.py` 正则脱敏(字符类兼容全角/半角);历史用 `git filter-repo --blob-callback`(回调内显式 UTF-8 解码 → 正则替换 → 编码)重写。重写后 origin 会被移除需重新添加,推送用 `--force`。

## 遇到问题
1. filter-repo 的 `--replace-text` 对中文模式**静默不匹配**,首次重写无效(靠全历史扫描发现)
2. filter-repo 默认移除 origin 远程地址

## 解决方案
- 改用 `--blob-callback`:回调内 `decode('utf-8')` → 正则替换 → `encode('utf-8')`,二进制 blob 原样返回
- 重写后 `git remote add origin ...` 恢复;`git push --force` 推送
- 用完即从 ai-service venv 卸载 git-filter-repo,保持环境干净

## 测试结果
- 全历史扫描(8 个提交 × 所有文件):敏感词命中 **0**
- GitHub API 读取公开 CLAUDE.md:已为脱敏版本
- 提交历史完整保留(8 个提交,线性)

## 下一步计划
- **改为"做中学"**:不专门排 Python 学习周,直接从项目第一个可运行模块动手,语法随用随学
- 第一个目标:ai-service 里跑通一个能调用 LLM 的最小脚本(DeepSeek key 入 .env)

# 2026-09-16(第二次)

## 本次目标
推进方式改为"直接做项目":由 AI 编写代码、按 REQ 逐个交付、每个功能点停下验收,不再穿插教学环节。本次完成 REQ-001 的功能点 F1——后端骨架就绪 + 数据库连通。

## 完成内容
- 补充后端缺失依赖:`spring-boot-starter-data-jpa`(此前 pom 只有 webmvc/security,**没有持久层**)、`jjwt` 0.12.6 三件套
- 配置分层:`application.yml`(可提交,值走环境变量)+ `application-local.yml`(本机私有,已 gitignore)
- 创建 MySQL 数据库 `paperpulse`(utf8mb4 / utf8mb4_unicode_ci)
- 定位并复用本机已有的 MySQL 8.0.46(root@localhost),**未启用 Docker**
- 验证后端启动并真实连上数据库

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| backend/pom.xml | 新增 data-jpa、jjwt-api/impl/jackson;新增 `jjwt.version` 属性 |
| backend/src/main/resources/application.yml | 新增数据源 / JPA / JWT / profile 配置 |
| backend/src/main/resources/application-local.yml | **新增(已忽略,不入库)**:本机数据库密码与 JWT 密钥 |
| .gitignore | 新增 `application-local.yml` / `.properties` 忽略规则 |

## 技术方案
配置分层采用 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`:默认激活 `local` profile,本机存在 `application-local.yml` 时用真实凭据;新克隆的仓库没有该文件,Spring 静默跳过并回落到 `application.yml` 的空默认值,不会启动失败。JPA 开发期用 `ddl-auto: update` 自动建表,上线前改为 `none` 并引入迁移脚本;`open-in-view` 显式关闭以规避该反模式。

## 遇到问题
1. **本机 MySQL root 密码未知**:项目 `.env.example` 中的 `paperpulse123` 及用户回忆的 4 个候选密码均失败
2. **YAML 八进制陷阱**:数据库密码 `***REMOVED-CREDENTIAL***` 在 YAML 中不加引号会被解析为**八进制整数 ***REMOVED-CREDENTIAL*****,导致 `Access denied for user 'root'@'localhost' (using password: YES)`。现象具有迷惑性——profile 已正确激活、配置文件确实加载,只有值被静默改写
3. 用户机器上的 `docker-compose.yml` 将 MySQL 映射到 3306,与本机已运行的 MySQL80 服务**端口冲突**

## 解决方案
1. 在用户本机其它 Java 项目(`D:\develop\JAVA\CODE\demo\demo1` 的 `application.properties`)中找到实际使用的 root 密码,验证通过后复用;未采取"重置 root 密码"这类侵入式方案
2. 密码值加引号:`password: "***REMOVED-CREDENTIAL***"`。**凡是以 0 开头的数字型字符串(密码、手机号、学号、编号)在 YAML 中一律加引号**
3. 记录待办:若将来改用 Docker 提供 MySQL,需将 compose 端口改为 `3307:3306` 以避免冲突

## 测试结果
- `mvn compile` → BUILD SUCCESS(Spring Boot 4.1.1 / Spring 7.0.9 / Hibernate 7.4.5,JDK 26 编译为 Java 21 字节码)
- `mvn spring-boot:run` → `Started BackendApplication in 2.295 seconds`,`Tomcat started on port 8080`
- 数据库连通证据:`HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@5ddb302` + `Database dialect: MySQLDialect`(Hibernate 已从数据库读到元数据)
- `GET /health` → 401,符合预期(Spring Security 默认全站锁定,尚无放行规则,待 F2 配置)
- `git check-ignore` 确认 `application-local.yml` 未入库

## 下一步计划
- **F2**:用户注册接口 `POST /api/auth/register`(BCrypt 加密 + 参数校验 + 统一错误响应)
- **F3**:登录签发 JWT `POST /api/auth/login`
- **F4**:JWT 鉴权过滤器 + 放行规则(解决当前全站 401)
