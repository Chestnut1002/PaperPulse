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
2. **YAML 八进制陷阱**:以 `0` 开头的数据库密码(如 `012345`)在 YAML 中不加引号会被解析为**八进制整数 `5349`**,导致 `Access denied for user 'root'@'localhost' (using password: YES)`。现象具有迷惑性——profile 已正确激活、配置文件确实加载,只有值被静默改写
3. 用户机器上的 `docker-compose.yml` 将 MySQL 映射到 3306,与本机已运行的 MySQL80 服务**端口冲突**

## 解决方案
1. 在用户本机其它 Java 项目(`D:\develop\JAVA\CODE\demo\demo1` 的 `application.properties`)中找到实际使用的 root 密码,验证通过后复用;未采取"重置 root 密码"这类侵入式方案
2. 密码值加引号:`password: "012345"`。**凡是以 0 开头的数字型字符串(密码、手机号、学号、编号)在 YAML 中一律加引号**
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

# 2026-09-16(第三次)

## 本次目标
完成 REQ-001 的功能点 F2:用户注册接口,含 BCrypt 加密、参数校验与统一错误响应。

## 完成内容
- 新增 `user` 包:`User` 实体、`UserRepository`、`UserService`、`AuthController`
- 新增 `user.dto`:`RegisterRequest`(校验注解)、`UserResponse`(出参,不含 password)
- 新增 `common` 包:`ApiException`(携带状态码的业务异常)、`GlobalExceptionHandler`(统一错误响应)
- 新增 `config.SecurityConfig`:BCryptPasswordEncoder + 放行 `/api/auth/**`
- 代码组织采用**按功能分包**(`user` / `common` / `config`)而非按层分包,便于后续 paper / recommendation 等模块横向扩展

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| backend/.../user/User.java | 新增 |
| backend/.../user/UserRepository.java | 新增 |
| backend/.../user/UserService.java | 新增 |
| backend/.../user/AuthController.java | 新增 |
| backend/.../user/dto/RegisterRequest.java | 新增 |
| backend/.../user/dto/UserResponse.java | 新增 |
| backend/.../common/ApiException.java | 新增 |
| backend/.../common/GlobalExceptionHandler.java | 新增 |
| backend/.../config/SecurityConfig.java | 新增 |

## 技术方案
1. **表名 `users`**:`USER` 是 MySQL 保留字,直接用作表名会报语法错误
2. **实体与出参分离**:`User` 持有 password 字段,若直接序列化实体会把密码哈希返回给客户端;因此定义 `UserResponse` 并只从它出参,从根上杜绝泄露
3. **BCrypt 随机盐**:同一密码每次哈希结果不同,攻击者无法通过比对密文推断"哪些用户用了同一个密码";strength 默认 10,单次约 50~100ms
4. **密码长度上限 72**:源于 BCrypt 只取前 72 **字节**(中文 1 字 = 3 字节),超长部分被静默忽略,不如直接拒绝
5. **错误响应统一形状**:`timestamp / status / error / message`,校验失败额外带 `fieldErrors`,前端只需一套处理逻辑;未预期异常兜底 500 并记日志
6. **关闭 CSRF / 表单登录 / Basic**:纯 REST + JWT 不使用 Cookie 会话;未认证统一返回 401 而非 302 跳转

## 遇到问题
1. F1 后台启动的 Java 子进程未被 `TaskStop` 一并结束,残留占用 8080,导致用户复跑时报 `Port 8080 was already in use` —— `spring-boot:run` 会 fork 独立 JVM,杀 Maven 外壳不等于杀应用
2. PowerShell 中 `curl` 是 `Invoke-WebRequest` 的别名,不识别 `-i` 参数,需改用 `curl.exe`
3. **同一问题在用户终端二次复现**:用户按 Ctrl+C 停止后,Java 子进程(及 Maven 启动器进程)双双残留,再次启动照样报端口占用
4. **PowerShell 脚本中文乱码导致语法错误**:新增的 `scripts/kill-port.ps1` 以无 BOM 的 UTF-8 保存,Windows PowerShell 5.1 按 GBK 读取,中文字节被解析成乱码并吃掉字符串收尾引号,报 `UnexpectedToken` 而无法运行

## 解决方案
1. 用 `netstat -ano | grep :8080` 定位 PID 后 `Stop-Process -Force`;后续验证实例统一改用 `BACKEND_PORT=8081` 起独立端口,不干扰用户正在运行的实例
2. 文档与提示中统一使用 `curl.exe`
3. **新增 `scripts/kill-port.ps1`**:按端口查找监听进程并强制结束,默认清理 8080(后端)与 8000(AI 服务),结束后复查端口是否真正释放。用法 `powershell -ExecutionPolicy Bypass -File scripts/kill-port.ps1`
4. 该脚本改存为 **UTF-8 with BOM**,并在脚本内显式设置 `[Console]::OutputEncoding = UTF8`。**注意:Windows PowerShell 5.1 只认 BOM,后续修改此文件必须保持 BOM,否则会再次语法报错**

## 测试结果
验证实例 `BACKEND_PORT=8081` 启动 2.839s,表由 Hibernate 自动创建。7 项用例全通过:
- `users` 表自动生成:字段类型、UNIQUE 约束(username/email)均正确
- 正常注册 → **201**,响应体含 id/username/email/createdAt,**无 password**
- 用户名重复 → **409** `用户名已被占用`
- 邮箱重复 → **409** `邮箱已被注册`
- 三项参数同时非法 → **400**,`fieldErrors` 一次性返回全部 3 条
- 落库密码为 BCrypt 密文 `$2a$10$...`;同一明文密码的两个账号**密文不同**(随机盐生效)
- 明文泄露检查:`SELECT COUNT(*) ... LIKE '%secret123%'` → **0**

## 下一步计划
- **F3**:登录签发 JWT `POST /api/auth/login`(BCrypt 校验 + 签发 token)
- **F4**:`JwtAuthenticationFilter` + `GET /api/users/me`,完成鉴权闭环
- **待确认**:单元/集成测试是否随功能点同步补齐(当前未写,建议接口稳定后统一补)

# 2026-09-16(第四次)

## 本次目标
完成 REQ-001 的功能点 F3:登录接口,校验凭据并签发 JWT。

## 完成内容
- 新增 `security` 包:`JwtProperties`(配置绑定)、`JwtService`(签发/校验)
- 新增 `user.dto.LoginRequest` / `LoginResponse`
- `UserService` 增加 `authenticate()` 与 `getById()`
- `AuthController` 增加 `POST /api/auth/login`
- `BackendApplication` 增加 `@ConfigurationPropertiesScan`

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| backend/.../security/JwtProperties.java | 新增 |
| backend/.../security/JwtService.java | 新增 |
| backend/.../user/dto/LoginRequest.java | 新增 |
| backend/.../user/dto/LoginResponse.java | 新增 |
| backend/.../user/UserService.java | 增加 authenticate / getById |
| backend/.../user/AuthController.java | 增加 login |
| backend/.../BackendApplication.java | 增加 @ConfigurationPropertiesScan |
| scripts/kill-port.ps1 | 修复逗号分隔端口参数被拼接的 bug |

## 技术方案
1. **JWT 三段结构与签名**:前两段(头部、载荷)只是 Base64 编码,任何人都能解开,服务端真正依赖的是第三段 HMAC 签名。因此 token 内不放敏感信息 —— 它保证"未被篡改",不保证"不可见"
2. **算法自动选择**:密钥为 64 字节(512 位),jjwt 的 `Keys.hmacShaKeyFor` 自动选用其支持的最强算法 HS512(而非 HS256)。密钥过短时 jjwt 会在启动期直接抛异常,把弱密钥问题拦在上线之前
3. **防用户名枚举(信息层面)**:用户名不存在与密码错误返回**完全相同**的 401 与提示文案,避免攻击者据此确认某个用户名是否已注册
4. **防用户名枚举(时序层面)**:用户不存在时若直接返回,会因跳过 BCrypt 而快上百倍,攻击者可据响应耗时枚举用户名。方案是在该分支**照样跑一次 BCrypt 比对**(启动时预生成一个 dummy 哈希,避免每次登录重复计算)
5. **登录接口不做长度/格式校验**:老用户密码规则可能与现行注册规则不同;且提示"密码长度不符"等于泄露密码策略

## 遇到问题
1. `kill-port.ps1` 新增的 `-Ports` 参数声明为 `int[]`,从 bash 传 `8081,8080` 时逗号被吃掉,拼成 `80818080` 导致 `Get-NetTCPConnection` 参数转换异常

## 解决方案
1. 参数改声明为 `string[]`,内部展开逗号、按端口范围(1~65535)校验后再转 `int`,兼容 `-Ports 8080,8000` / `-Ports "8080,8000"` / 多参数三种写法

## 测试结果
验证实例 `BACKEND_PORT=8081` 启动 2.821s,5 项用例全通过:
- 正确凭据登录 → **200**,返回 `token` / `tokenType=Bearer` / `expiresIn=86400` / `user`(不含密码)
- 密码错误 → **401** `用户名或密码错误`
- 用户名不存在 → **401** `用户名或密码错误`(与上条**逐字一致**)
- JWT 结构解析:头部 `{"alg":"HS512"}`、载荷 `{"sub":"2","username":"alice","iat":...,"exp":...}`;用密钥独立复算签名 → **完全一致**;篡改载荷后签名 → **不再匹配**
- 时序侧信道:两种失败场景中位耗时 515ms vs 586ms,差异 12.1%(噪声范围内),无法据此区分

## 下一步计划
- **F4**:`JwtAuthenticationFilter` 解析 `Authorization: Bearer <token>` + `GET /api/users/me`,完成鉴权闭环(解决目前除 `/api/auth/**` 外全站 401 的问题)

---

# 2026-09-16(收尾总结)

> 本节是当日收尾,供下次开工时快速恢复上下文。**建议下次从「四、下次开工怎么开始」读起。**

## 一、今天最大的变化:推进方式转换

项目此前处于"零功能"状态(三端都只有脚手架)。今天把推进方式确定为:

- **由 AI 编写代码,用户负责运行与验收**,不再穿插教学环节
- **按 REQ 逐个交付**,每个功能点完成后停下等用户验收
- 每个功能点的技术方案、遇到的问题、解决方案全部记入本日志 —— 供将来回看时查阅

## 二、今天实际完成的东西

**完成了 REQ-001 的 F1 / F2 / F3 三个功能点**(REQ-001 共拆为 6 个功能点,F4~F6 未做)。

| 功能点 | 内容 | 验收状态 |
| ---- | ---- | ---- |
| F1 | 后端骨架就绪 + 数据库连通 | ✅ 用户已验证 |
| F2 | 用户注册 `POST /api/auth/register` | ✅ 用户已验证(自行注册 test1 成功) |
| F3 | 登录签发 JWT `POST /api/auth/login` | ⚠️ **AI 自测通过,用户尚未验收** |

**代码产出**(backend,共 14 个 Java 文件):

```
com.paperpulse
├── BackendApplication.java        增加 @ConfigurationPropertiesScan
├── config/SecurityConfig.java     BCrypt Bean + 安全策略(无状态、关 CSRF/表单登录、401)
├── common/
│   ├── ApiException.java          携带 HTTP 状态码的业务异常
│   └── GlobalExceptionHandler.java 统一错误响应
├── security/
│   ├── JwtProperties.java         绑定 jwt.* 配置
│   └── JwtService.java            签发 / 校验 JWT(HS512)
└── user/
    ├── User.java                  实体,表名 users
    ├── UserRepository.java
    ├── UserService.java           注册 / 认证
    ├── AuthController.java        /api/auth/register、/api/auth/login
    └── dto/                       RegisterRequest、UserResponse、LoginRequest、LoginResponse
```

**其他产出**:
- 建库 `paperpulse`(utf8mb4 / utf8mb4_unicode_ci),`users` 表由 Hibernate 自动生成
- `scripts/kill-port.ps1` 开发辅助脚本
- `backend/pom.xml` 补齐 `spring-boot-starter-data-jpa` 与 `jjwt` 0.12.6
- 配置分层:`application.yml`(可提交)+ `application-local.yml`(本机私有)
- 文档:`docs/requirements.md` 补进度拆分、`CHANGELOG.md` 补 v0.2.0、`README.md` 补进度与 API 表

**Git 提交**(4 个):
```
d892996 feat: add JWT login endpoint
4a80783 chore: add kill-port helper to clean up orphaned dev server processes
f0c6397 feat: add user registration with BCrypt and unified error handling
ac29dc5 chore: add JPA and JWT dependencies with datasource configuration
```

## 三、今天踩的坑(按价值排序)

**1. YAML 八进制陷阱 —— 以 `0` 开头的密码被当成八进制**
不加引号的 `012345` 被 YAML 当作八进制整数解析成 `5349`。现象极具迷惑性:profile 正确激活、配置文件确实加载、报错却是 `Access denied`。
**结论:凡以 0 开头的数字型字符串(密码、手机号、学号、编号),YAML 里一律加引号。**

**2. Windows 上 `Ctrl+C` 杀不掉开发服务**
`mvn spring-boot:run` 会 fork 出独立 JVM,`Ctrl+C` 只结束 Maven 外壳,Java 子进程残留继续占用 8080,导致下次启动报 `Port 8080 was already in use`。今天因此卡了两次。
**结论:用 `scripts/kill-port.ps1` 清理;验证新代码时用 `BACKEND_PORT=8081` 起独立端口,不干扰正在运行的实例。**

**3. PowerShell 5.1 按 GBK 读取无 BOM 的 `.ps1`**
UTF-8 中文字节变乱码、吃掉字符串收尾引号,脚本直接 `UnexpectedToken` 无法运行。
**结论:`scripts/kill-port.ps1` 必须保持 UTF-8 with BOM,修改时别弄丢。**

**4. PowerShell 与 bash 的几处不兼容**
`curl` 是 `Invoke-WebRequest` 别名(要用 `curl.exe`);引号包裹的路径当命令执行必须加调用运算符 `&`;JSON 请求体建议直接用 `Invoke-RestMethod` 免去转义。

**5. 本机 MySQL 与 docker-compose 端口冲突**
`docker-compose.yml` 把 MySQL 映射到 3306,与本机已在运行的 MySQL 80 服务冲突。今天选择**直接复用本机 MySQL**,未启用 Docker。

**6. 检索数据源已换成 Semantic Scholar + Crossref 降级**
本机网络不可达 `export.arxiv.org`(arXiv 官方 API),且 Semantic Scholar 免 key 共享池会持续 429。已实现"先试 Semantic Scholar,失败自动降级 Crossref"的链路。**这是 REQ-002 的前置成果,别重复踩。**

## 四、下次开工怎么开始

**第 0 步:确认 F3 验收**
用户尚未验收 F3。启动后执行:

```powershell
# 启动(项目根目录)
mvn -f backend/pom.xml spring-boot:run

# 登录(用 F2 建的账号,密码 secret123)
Invoke-RestMethod -Uri http://localhost:8080/api/auth/login -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"alice","password":"secret123"}'
```
预期返回 `token` / `tokenType=Bearer` / `expiresIn=86400` / `user`。
再故意输错密码,预期 401 `用户名或密码错误`。

**第 1 步:做 F4**
- 新增 `security/JwtAuthenticationFilter`:从 `Authorization: Bearer <token>` 取出 token,调 `JwtService.parseUserId()` 校验,写入 `SecurityContext`
- `SecurityConfig` 中挂载该过滤器,放行规则保持 `/api/auth/**` 开放
- 新增 `GET /api/users/me`:返回当前登录用户 `UserResponse`
- 验收标准:不带 token → 401;带上登录拿到的 token → 200 且返回对应用户

**环境备忘**
- MySQL:本机 8.0.46,root 密码见 `backend/src/main/resources/application-local.yml`(不入库)
- 数据库已有测试账号:`chestnut` / `alice`(密码均为 `secret123`)、`test1`
- 工具链:JDK 26 编译为 Java 21 字节码、Maven 3.9.16(走阿里云镜像)、Spring Boot 4.1.1 / Hibernate 7.4.5
- ai-service 有可用的 DeepSeek 调用与论文检索脚本(`ai-service/scripts/`,**尚未纳入 Git**),是 REQ-002 的起点

**待确认事项**:单元/集成测试暂定"接口稳定后统一补"(节奏 A),用户尚未最终确认。

---

# 2026-09-16(第五次:F4 鉴权闭环 + 自主验收)

## 本次目标

补上 F3 留下的缺口:F3 能签发 token,但**没有任何接口消费它**,所以"登录成功"只证明 token 能生成,
没证明它有用。F4 做 JWT 鉴权过滤器 + `GET /api/users/me`,形成完整闭环。

按用户要求,**验收由 AI 自主完成** —— 跑真实 MySQL + 真实 HTTP,不用 mock,
只把结果呈现给用户。

## 完成内容

- F4:`JwtAuthenticationFilter` 从 `Authorization: Bearer <token>` 解析并认证
- F4:`GET /api/users/me` 返回当前登录用户
- `RestAuthenticationEntryPoint`:让 401 的响应格式与全局错误格式统一
- **修复一个真 bug**:四类客户端错误(路径不存在 / 畸形 JSON / 方法不支持 / Content-Type 不支持)
  原本全部返回 **500**
- 25 项验收用例全部通过(19 项 F3+F4 + 6 项错误处理回归)
- 补注:用户名大小写不敏感是 MySQL 排序规则决定的,不是 Java 代码

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `security/JwtAuthenticationFilter.java` | 新增。解析 Bearer token,把 userId 写入 SecurityContext |
| `security/RestAuthenticationEntryPoint.java` | 新增。401 输出统一 JSON |
| `user/UserController.java` | 新增。`GET /api/users/me` |
| `config/SecurityConfig.java` | 挂载过滤器;换用自定义入口 |
| `common/GlobalExceptionHandler.java` | 改为继承 `ResponseEntityExceptionHandler`;**本次最重要的修复** |
| `user/User.java` | 补注大小写不敏感的成因 |

## 技术方案

### 1. 过滤器只认证,不拦截

`JwtAuthenticationFilter` 在 token 缺失或无效时**不写响应、不抛异常**,只放行,
让后面的 `AuthorizationFilter` 按放行规则决定拦不拦。若在过滤器里直接写 401,
401 就有了两个出口、两种响应格式。

同理,过滤器**不查数据库**,只把 userId 放进 `SecurityContext`;需要用户实体时由业务层再查。
否则每个受保护请求都白跑一次 SQL,哪怕接口根本用不到用户行。

`/api/users/me` 则**刻意每次回查数据库**,而不是直接信任 token 里的 username ——
token 一旦签发就无法撤回,回查意味着用户被删除后其 token 立即失效。**签名有效 ≠ 用户仍然存在。**

### 2. 过滤器不能用 `@Component` 声明

Spring Boot 会把容器里所有 `Filter` 类型的 Bean **额外注册到 Servlet 容器**,
导致它在安全链之外再跑一遍。用 `new JwtAuthenticationFilter(jwtService)` 传进安全链,只让它被持有一份。

### 3. 统一错误的正确做法是继承 `ResponseEntityExceptionHandler`

`@ExceptionHandler(Exception.class)` 兜底看起来安全,实际上会**抢在 Spring MVC 自己的异常解析器之前**
把所有内置异常都吞成 500。Spring 已经把这些异常逐个映射好了,且它们最终都汇聚到
`handleExceptionInternal` 一个方法 —— 继承父类 + 覆盖那一个漏斗,比逐个枚举异常类型既更简单也更完整。

## 遇到问题

### 问题一(真 bug):四类客户端错误全部返回 500

验收时 F4-10 用例(带合法 token 访问不存在的路径)返回 **500**,预期是 404。顺着查发现同一个根因还影响:

| 场景 | 修复前 | 应为 |
| ---- | ---- | ---- |
| 路径不存在 | 500 | 404 |
| 请求体不是合法 JSON | 500 | 400 |
| 用 GET 调只接受 POST 的接口 | 500 | 405 |
| Content-Type 不支持 | 500 | 415 |

两个危害:一是**把客户端的错报成服务端的错**,前端无法区分"我请求错了"和"服务挂了";
二是每来一个 404 就打一整条堆栈(日志里实测刷了 5 条),日志监控会被误报淹没。

**解决方案**:`GlobalExceptionHandler` 改为继承 `ResponseEntityExceptionHandler`,
覆盖 `handleExceptionInternal` 统一响应格式,并覆盖 `handleMethodArgumentNotValid` 补上 `fieldErrors`。

> 注意:覆盖而不是另加 `@ExceptionHandler(MethodArgumentNotValidException.class)` ——
> 父类已有同名处理方法,再声明一个会让 Spring 启动时报 `Ambiguous @ExceptionHandler method mapped`。

**修复后**:25 项用例全过,整轮日志 **0 条 ERROR、0 条堆栈**。

> **Code Review 时又抓到一处自己埋的坑**:覆盖 `handleExceptionInternal` 时把 `headers` 参数丢掉了。
> 父类在 405 时会往 headers 里塞 `Allow`(列出该路径支持的方法),而 RFC 9110 §15.5.6 要求 405 响应
> **必须**带这个头。第一版测试只断言状态码,完全察觉不到 —— 补上 `Allow` 断言后才暴露。
> **教训:断言不能只看状态码。**

### 问题二(认知纠正):用户名是大小写不敏感的

验收用例 F3-6 原本预期"用 `Alice` 登录 `alice` 的账号应返回 401",实测返回 **200**。

根因不是代码,是数据库:MySQL 列排序规则为 `utf8mb4_unicode_ci`,`_ci` = case insensitive,
`WHERE username = 'Alice'` 会命中 `'alice'`。

进一步验证语义是否**自洽** —— 补测"注册 `ALICE`":返回 409 用户名已被占用。
说明注册查重与登录校验用的是同一套规则,不会出现两个只差大小写的账号(那样后注册的账号永远登不进去)。
**结论:这是正确行为,原预期写错了**。已在 `User.java` 补注释,提醒日后若改排序规则行为会静默反转。

### 问题三(预期落空):`/error` 放行原来是多余的

原以为需要 `.requestMatchers("/error").permitAll()`:异常转发到 `/error` 时会再走一遍安全链,
那次分发里没有认证信息,不放行的话真实错误会被改写成 401。

实测:去掉这行后 25 项用例**依然全过**,且直接访问 `/error` 从 500 变成 401(更安全)。
原因是 `GlobalExceptionHandler` 在 DispatcherServlet 内部就把错误处理掉了,**根本不会触发 ERROR 分发** ——
这行放行守的是一条走不到的路。**已删除**(不是"先留着以防万一":留着反而让匿名用户能直接访问 `/error` 拿到 500)。

### 问题四:Jackson 3 的包名变了

`RestAuthenticationEntryPoint` 注入 `ObjectMapper` 时编译报错 `com.fasterxml.jackson.databind` 无法解析。

查依赖树发现:Spring Boot 4 已迁到 **Jackson 3**,坐标 `tools.jackson.core`、包名 `tools.jackson.databind`;
工程里那个 `com.fasterxml.jackson.core:jackson-databind` 是 **jjwt 拖进来的 Jackson 2**,且是 `runtime` 作用域
—— **运行期在、编译期看不见**。照着老包名写必然编译不过。

## 测试结果

### 环境

真实 MySQL 8.0.46 + 真实 HTTP,独立实例跑在 **8081**(不干扰用户 8080 上的实例)。
JWT 的签名复算与伪造用 Python 标准库 `hmac/hashlib` 独立实现,**不借助 jjwt** ——
否则等于用同一套实现验证自己,证明不了什么。

### F3(签发 token)

| 编号 | 用例 | 实测 | 结果 |
| ---- | ---- | ---- | ---- |
| F3-1 | 正确凭据登录 | 200,tokenType=Bearer,expiresIn=86400 | PASS |
| F3-2 | 密码错误 | 401 + 用户名或密码错误 | PASS |
| F3-3 | 用户名不存在 | 401 + **同一句文案**(防枚举) | PASS |
| F3-4 | 用户名/密码为空 | 400 + fieldErrors | PASS |
| F3-5 | 用户名含 `' OR '1'='1` | 401(而非 500,证明是参数化查询) | PASS |
| F3-6 | 用 `Alice` 登录 `alice` | 200(大小写不敏感,见问题二) | PASS |
| F3-10 | 注册 `ALICE`(已存在 `alice`) | 409 用户名已被占用(与 F3-6 自洽) | PASS |
| F3-7 | 解出 token 的 header/payload | alg=HS512,sub=2,有效期=86400s | PASS |
| F3-8 | **用密钥独立复算 HMAC 签名** | 复算一致;篡改后不一致 | PASS |
| F3-9 | 计时侧信道 | 存在 82ms vs 不存在 88ms(差 7.1%) | PASS |

### F4(消费 token)

| 编号 | 用例 | 实测 | 结果 |
| ---- | ---- | ---- | ---- |
| F4-1 | 不带 Authorization | 401 + 统一 JSON 格式 | PASS |
| F4-2 | `Bearer abc` | 401 | PASS |
| F4-3 | 合法 token 改掉签名最后一位 | 401 | PASS |
| F4-4 | 自签已过期的 token | 401 | PASS |
| F4-5 | 真实登录拿到的 token | 200,id=2,响应无 password 字段 | PASS |
| F4-6 | 漏掉 `Bearer ` 前缀 | 401 | PASS |
| F4-7 | scheme 全小写 `bearer` | 200(RFC 7235 规定大小写不敏感) | PASS |
| F4-8 | 自签合法但 `sub=999999` 的 token | 401(签名有效≠用户存在) | PASS |
| F4-9 | 回归:登录口不带 token 仍可访问 | 200 | PASS |

### E(错误处理回归 —— 修复前这五项全是 500)

| 编号 | 用例 | 修复前 | 实测 | 结果 |
| ---- | ---- | ---- | ---- | ---- |
| E-1 | 已登录访问不存在的路径 | 500 | 404 | PASS |
| E-2 | 匿名访问不存在的路径 | — | 401(不暴露路径是否存在) | PASS |
| E-3 | 请求体不是合法 JSON | 500 | 400 | PASS |
| E-4 | GET 调只接受 POST 的接口 | 500 | 405 + `Allow: POST` | PASS |
| E-5 | Content-Type 不支持 | 500 | 415 | PASS |
| E-6 | 直接访问 `/error` | 500 | 401 | PASS |

**合计 25 项:25 PASS / 0 FAIL。**

## 下一步计划

REQ-001 的**认证部分已闭环**,剩下 F5(兴趣标签)、F6(收藏 / 阅读历史 / 论文评分),
做完这三项 REQ-001 才算整体完成。

技术债(记在这里别忘了):
- 单元/集成测试仍未落地。`pom.xml` 里已有 `spring-boot-starter-*-test` 三个测试 starter,
  JUnit + MockMvc 可用,但目前只有一个空的上下文加载测试。本次验收是**外部脚本**,没进仓库,
  下次改代码时不会自动回归 —— 这是目前最大的一笔债。
- `AccessDeniedHandler`(403)尚未配置。现在没有角色概念,不触发;等引入权限时再补。
- 无 CORS 配置,前端接入时需要补。

---

# 工作总结(截至 2026-09-16)

## 今天做完了什么

**REQ-001 用户系统:6 个功能点完成 4 个,认证部分完整闭环。**

| 功能点 | 内容 | 状态 |
| ---- | ---- | ---- |
| F1 | 后端骨架 + 数据库连通 | Done |
| F2 | 用户注册(BCrypt + 参数校验 + 统一错误响应) | Done |
| F3 | 用户登录(签发 JWT) | Done,已验收 |
| F4 | 鉴权过滤器 + `GET /api/users/me` | Done,已验收 |
| F5 | 兴趣标签 | 待做 |
| F6 | 收藏 / 阅读历史 / 论文评分 | 待做 |

现在这条链路是通的:**注册 → 登录拿 token → 带 token 访问受保护接口**。
不是"能编译",是 25 条用例逐条跑过、跑在真实数据库和真实 HTTP 上。

## 最有价值的产出不是新代码,是发现的那个 bug

新增的三个类(F4)加起来 181 行,写得比较顺。真正的收获是验收时撞出来的那个问题:
**四类客户端错误原本全被当成 500**。

这个 bug 不写验收用例基本发现不了 —— 正常路径全对,注册登录都好好的,
只有去问"如果请求本身是错的会怎样"才会撞上。它的危害也不是"功能坏了",而是:
- 前端无法区分"我请求错了"和"服务挂了"
- 每来一个 404 打一整条堆栈,真出事时日志已经被淹了

修复后整轮验收日志 **0 条 ERROR、0 条堆栈**。

顺带纠正了两个认知错误(用户名大小写不敏感、`/error` 放行其实多余)——
这两个都是"我以为",实测把"我以为"推翻了。**`/error` 那行我没有"先留着以防万一",
而是删掉了**:留着反而让匿名用户能直接访问 `/error` 拿到 500。

## 工作量分布

- 写新功能(F4 三个类):约三分之一
- 验收 + 修 bug:约三分之二

比例有点反直觉,但对"要给别人看的项目"来说是对的 —— 代码能跑只是及格线。

## 现在项目处在什么位置

REQ-001 完成约 2/3,六个需求里第一个。按 8 周路线图,进度正常。

**最大的技术债:验收是外部脚本,没进仓库。** 今天这 25 条用例下次改代码时不会自动重跑。
建议 F5 开始时顺便把测试落地 —— 不用追求覆盖率,先把这 25 条变成能 `mvn test` 跑的东西,
之后每加一个功能点就补几条。这件事拖得越久,补起来越贵。

## 下次开工怎么开始

直接做 F5(兴趣标签),涉及新表 + 新接口,是 F6 的前置。
第一步建议先把今天这 25 条用例落成集成测试,再动 F5 —— 这样 F5 改坏了能立刻发现。
