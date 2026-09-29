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
- 精读相关领域论文与 PaperAgent 源码

# 2026-09-11

## 本次目标
将用户指定的两份开发规范打包为 dev-standard agent skill(用户级 + 项目级各一份),并解决 GitHub 推送问题。

## 完成内容
- 创建用户级 skill:C:\Users\ASUS\.claude\skills\dev-standard\SKILL.md(全局可用)
- 创建项目级 skill:.claude/skills/dev-standard/SKILL.md(随仓库提交)
- 项目级 skill 提交并推送至 GitHub
- 合并远程网页端 README 修改(以远程版本为准)
- 部署 PaperAgent v2.1.4 至 D:\develop\paperagent\(论文调研工具;Q&A 模块 + DeepSeek,key 由用户在 Web 设置页自填;每日推荐暂不启用)

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
- 精读相关领域论文与 PaperAgent 源码

# 2026-09-12

## 本次目标
将本地参考文档纳入本机目录,并明确公开仓库的内容边界。

## 完成内容
- 两份 PaperAgent 参考文档(结合方案 / 源码阅读指南)复制到本地 `docs-plans/` 目录
- `docs-plans/` 加入 .gitignore,不进入任何 Git 仓库
- 规划关键信息写入 Claude Code 持久记忆(项目目标、PaperAgent 借鉴点、文档位置)

## 修改文件
| 文件 | 修改 |
| ---- | ---- |
| .gitignore | 新增 docs-plans/ 忽略规则 |
| docs/dev-log.md | 追加本条目 |
| docs-plans/*(本地,不提交) | 新增本地参考文档副本 |

## 技术方案
用户决定:本地参考文档只存本机、不进 Git,仓库对外只保留代码与公开文档。本地副本放 docs-plans/ 供开发查阅。

## 遇到问题
仓库当前为公开状态(visibility: public),本地参考文档不应混入提交。

## 解决方案
docs-plans/ 走 .gitignore 本地保留;发布前无需历史清理,仓库历史天然干净。

## 测试结果
- `git status` 确认 docs-plans/ 未被跟踪

## 下一步计划
- REQ-001 用户注册/登录(后端 JWT + MySQL)
- 精读相关领域论文与 PaperAgent 源码(按 docs-plans/ 中的阅读指南推进)

# 2026-09-16

## 本次目标
清理公开仓库中的本机私有信息(工作区 + 全部 Git 历史),删除新出现的 Codex 规范文件。

## 完成内容
- 工作区脱敏:CLAUDE.md 项目定位表述、docs/dev-log.md 三处、.gitignore 注释 → 中性技术描述
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
入库边界:仓库公开,本机私有信息与真实凭据不得出现在任何入库内容中。工作区用 `scratch/sanitize.py` 正则脱敏(字符类兼容全角/半角);历史用 `git filter-repo --blob-callback`(回调内显式 UTF-8 解码 → 正则替换 → 编码)重写。重写后 origin 会被移除需重新添加,推送用 `--force`。

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
- 第一个目标:ai-service 里跑通一个能调用 LLM 的最小脚本(DeepSeek key 入 .env)

# 2026-09-16(第二次)

## 本次目标
推进方式:由 AI 编写代码、按 REQ 逐个交付、每个功能点停下验收。本次完成 REQ-001 的功能点 F1——后端骨架就绪 + 数据库连通。

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

- **由 AI 编写代码,用户负责运行与验收**
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

REQ-001 完成约 2/3,六个需求里第一个。

**最大的技术债:验收是外部脚本,没进仓库。** 今天这 25 条用例下次改代码时不会自动重跑。
建议 F5 开始时顺便把测试落地 —— 不用追求覆盖率,先把这 25 条变成能 `mvn test` 跑的东西,
之后每加一个功能点就补几条。这件事拖得越久,补起来越贵。

## 下次开工怎么开始

直接做 F5(兴趣标签),涉及新表 + 新接口,是 F6 的前置。
第一步建议先把今天这 25 条用例落成集成测试,再动 F5 —— 这样 F5 改坏了能立刻发现。

---

# 2026-09-16(第六次:凭据泄露排查与处置)

## 本次目标

收尾扫描时把「本机数据库密码」也加进了敏感词检查,发现它在 `docs/dev-log.md` 里
以明文形式存在(**为说明 YAML 八进制陷阱而引用了真实密码**),且已随 5 个提交推送到公开仓库。

## 处置

1. **清理当前版本**:把真实密码换成虚构示例 `012345`,教学效果不变(它同样会被 YAML 当成八进制)
2. **重写全部历史**:用 `git filter-repo --replace-text` 清除所有提交中的该字符串,强推
3. **轮换凭据**:修改本机 MySQL root 密码,并同步本机其它项目中的引用
4. 备份留在仓库外(`git bundle`),以防重写出错

## 两个值得记住的结论

**一、`0.0.0.0` 监听 ≠ 谁都能连。** 初判时只看端口监听在 `0.0.0.0:3306` 就下结论"网络可达、风险高",
漏查了账号表。实际 MySQL 只有 `root@localhost`,**没有 `root@%`** —— 远程主机即便拿到正确密码,
账号匹配的 host 也对不上,照样被拒。**评估可利用性要同时看"端口"和"账号"两件事。**

**二、文档里的"示例"很容易变成真凭据。** 写技术笔记时为了具体,顺手引用了真实值 ——
这是很自然的动作,但技术文档是要进仓库的。**凡是举例,一律编造一个。**
`grep` 敏感词清单里除了业务词,也应该包含本机凭据本身。

**三、推送过的秘密要按已泄露处理。** 重写历史只能让仓库表面干净:GitHub 可能仍保留旧对象,
更不用说缓存与抓取。**真正的补救是轮换,不是抹除。**

---

# 2026-09-16(第七次:验收用例落地为集成测试)

## 本次目标

上次留下的最大一笔技术债:25 条验收用例是**外部 Python 脚本**,没进仓库,改代码后不会自动回归。
本次把它们落成 `mvn test` 能跑的集成测试,给下一步的 F5 铺一张回归网。

## 完成内容

- 新增 3 个测试类,共 **25 条用例**(与上次验收一一对应)+ 1 条既有的上下文加载测试
- 测试起**真实 Spring 容器 + 真实 Tomcat(随机端口)**,用真实 HTTP 请求打过去,连真实 MySQL
- 新增 `TestDatabaseGuard`:容器启动**前**核对库名,防止测试误连开发库
- 新增测试库 `paperpulse_test`,与开发库完全隔离
- 修掉一处**我自己写的、具有随机性的测试用例**(详见「问题一」)
- `README.md` 补「测试」章节;`pom.xml` **零改动**

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `test/resources/application-test.yml` | 新增。测试 profile:独立库 + `create-drop` |
| `test/support/ApiClient.java` | 新增。基于 JDK `HttpClient` 的测试客户端 |
| `test/support/TokenForger.java` | 新增。独立签 JWT,并可翻转片段里的一个 bit |
| `test/support/TestDatabaseGuard.java` | 新增。库名护栏 |
| `test/support/AbstractIntegrationTest.java` | 新增。基类:随机端口、真实 HTTP、清表 |
| `test/auth/AuthApiIntegrationTest.java` | 新增。F3,10 条 |
| `test/auth/AuthorizationApiIntegrationTest.java` | 新增。F4,9 条 |
| `test/common/ErrorHandlingIntegrationTest.java` | 新增。E,6 条 |
| `README.md` | 补「测试」章节 |
| `CHANGELOG.md` | 补测试条目 |

## 技术方案

### 1. 三个绕不开的选型

**HTTP 测试客户端用 JDK 自带的 `HttpClient`。** Spring 的 `TestRestTemplate` 在 Spring Boot 4 中
**已被移除**(本地 m2 仓库里 3.2.0 还有、4.1.1 已经没有了)。用它反而更稳妥:不随 Spring 版本变动,
而且默认就满足测试需要 —— 不跟随重定向(3xx 原样暴露)、不对 4xx/5xx 抛异常(断言 401/404 时不会先炸)。

**数据库用真实 MySQL,不用 H2。** 这条最关键:用户名**大小写不敏感**来自 MySQL 列的排序规则
`utf8mb4_unicode_ci`,H2 上会得出**相反**结论。用 H2 等于把一个与生产不符的行为固化进测试,
还可能在将来诱导人去"修"本来正确的代码。测试库地址在 `application-test.yml` 里被整体替换掉,
所以它是硬编码的,环境变量 `DB_URL` 改不动它。

**用 `@SpringBootTest(RANDOM_PORT)` 而不是 MockMvc。** MockMvc 不经过 Servlet 容器,而这次要覆盖的
东西有一部分恰恰是容器层面的 —— 错误转发到 `/error` 时的再分发、认证入口点写响应体的时机。
随机端口也顺便避开了与本机 8080 实例撞车。

### 2. 独立复算签名

`TokenForger` 用 `javax.crypto.Mac` 自己走一遍 HMAC-SHA512,**刻意不用 jjwt**。
应用代码就是 jjwt 验签的,测试若也用它来造 token,等于用被验证的实现去验证它自己:
jjwt 理解错规范时两边会一起错,测试照样通过。

### 3. 那道护栏为什么必须跑在容器启动之前

测试配置用 `ddl-auto: create-drop`,它在**容器启动时**就会删表重建。
如果等 `@BeforeAll` / `@BeforeEach` 再核对库名,开发库的表早就没了 —— 那时护栏只能报告事故,不能阻止事故。
所以护栏写成 `ApplicationContextInitializer`:此时配置已加载完(环境变量也在内),但还没有任何 Bean 被创建。

## 遇到问题

### 问题一(真问题):一条用例在掷骰子

`F4-3`(篡改签名应返回 401)第一轮通过、第二轮**失败**(返回 200)、第三轮又通过。

根因不在被测代码,在我写的测试。我原本的做法是"把签名最后一个字符换成 `A` 或 `B`"。但:

> HS512 签名是 **64 字节**,Base64URL 编码后是 **86 个字符 = 516 位**,而真实数据只有 **512 位**
> —— 最后一个字符的**低 4 位是填充,不参与解码**。

`A` 是索引 0(`000000`),`B` 是索引 1(`000001`),高 2 位都是 `00`。把末位在两者间替换,
**解码出来的字节一模一样**,签名依然有效,服务端返回 200 完全正确。于是这条用例是否真的篡改到了数据,
全看签名末位碰巧是什么字符。

顺带说明:**上次验收时 F4-3 返回 401 是运气**,那个手法本身就不可靠。

**解决方案**:新增 `TokenForger.flipBit()`,解码出字节、翻转一个 bit、再编码回去,
并加一句 `assertThat(tampered).isNotEqualTo(token)` 自保。改动必定落在真实数据上。

### 问题二(认知纠正):`DB_URL` 对测试根本不起作用

为验证护栏,我先用 `DB_URL` 指向一个探测库跑测试 —— 结果测试**照常通过**,护栏没响,
探测库也没被创建。一度以为护栏失效。

实际原因是:`DB_URL` 只是 `application.yml` 里占位符 `${DB_URL:...}` 的名字。
测试 profile 会把整个 `spring.datasource.url` 属性**替换掉**,那个占位符根本不会求值。
所以 `DB_URL` 对测试毫无影响。

**这其实是好消息**:测试库地址不可能被 `DB_URL` 悄悄改掉。真正的覆盖向量是优先级高于配置文件的
`SPRING_DATASOURCE_URL` 环境变量、`-Dspring.datasource.url=` 命令行参数。改用前者重测,护栏正确中止,
且**探测库自始至终没有被创建** —— 证明它确实拦在了建立连接之前。

（护栏里那句报错文案原本把责任归给 `DB_URL`,已改正。）

### 问题三:中文测试名在控制台是乱码

Surefire 输出里 `@DisplayName` 的中文显示为 `F3 ��¼ǩ�� JWT`。原因是 Windows 控制台默认 GBK,
而 JVM 以 UTF-8 输出。**不影响测试结果**,只是看日志时碍眼;测试报告文件(如 `target/surefire-reports`)
里的中文是正常的。

## 测试结果

```
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
```

| 测试类 | 用例数 | 耗时 |
| ---- | ---- | ---- |
| `AuthApiIntegrationTest`(F3) | 10 | 7.99 s(含容器启动) |
| `AuthorizationApiIntegrationTest`(F4) | 9 | 0.94 s |
| `ErrorHandlingIntegrationTest`(E) | 6 | 0.25 s |
| `BackendApplicationTests`(既有) | 1 | 0.61 s |

**连跑三轮,均为 26/26 通过,退出码 0** —— 修掉问题一之后不再有随机性。

验证过的几件事:
- 日志中 `Default catalog/schema: paperpulse_test` —— 确实连的是测试库
- `Tomcat started on port 63197` —— 确实是真实服务器、随机端口
- 三个测试类只启动一次容器(上下文缓存命中),F3 之后 F4/E 各不到 1 秒
- 护栏用探测库验证有效,探测库未被创建
- 跑完后开发库 `paperpulse` 的 3 条用户数据**完好无损**

## 下一步计划

测试网已经铺好,可以开始 **F5(兴趣标签)** 了 —— 涉及新表 + 新接口,是 F6 的前置。
新功能一落地就补几条用例,别让这张网漏下去。

遗留(仍不急):
- `AccessDeniedHandler`(403)未配 —— 现在没有角色概念,不触发
- 无 CORS 配置 —— 前端接入时补

---

# 2026-09-16(第八次:F5 兴趣标签)

## 本次目标

REQ-001 的第五个功能点。F1–F4 解决"用户是谁",F5 开始解决"用户想要什么" ——
这是 REQ-004 个性化推荐的**冷启动信号源**:新用户没有任何行为数据,唯一能拿到的偏好输入
就是他勾选的兴趣。

顺带验证一件事:上次刚铺好的集成测试网,能不能真的接住一个新功能。

## 完成内容

- 新增 `com.paperpulse.interest` 包:34 个标签的词表 + 用户兴趣的读写
- 新增 3 个接口:`GET /api/interests`、`GET|PUT /api/users/me/interests`
- 新增数据表 `user_interest`,含 `(user_id, tag_key)` 唯一约束
- 新增 **15 条集成测试**,全部通过
- 设计文档 `docs/design/F5-兴趣标签.md`
- 全量回归 **41/41 通过**(F3=10,F4=9,E=6,F5=15,上下文=1),连跑三轮稳定
- **`pom.xml` 零改动** —— 没有引入任何新依赖

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `main/interest/InterestTag.java` | 新增。词表枚举,34 项 6 分类,含 S2 映射 |
| `main/interest/UserInterest.java` | 新增。实体 + 权重区间常量 |
| `main/interest/UserInterestRepository.java` | 新增。含"先删后插"用的 JPQL 批量删除 |
| `main/interest/InterestService.java` | 新增。词表 / 读取 / 全量替换 |
| `main/interest/InterestController.java` | 新增。三个接口 |
| `main/interest/dto/*.java` | 新增。3 个 DTO |
| `test/interest/InterestApiIntegrationTest.java` | 新增。F5,15 条 |
| `test/support/AbstractIntegrationTest.java` | 改。清表时一并删 `user_interest` |
| `docs/design/F5-兴趣标签.md` | 新增。设计文档 |
| `docs/requirements.md` | F5 → ✅ Done |
| `README.md` | API 表补 3 个接口;测试类表补 F5 |
| `CHANGELOG.md` | 补 F5 条目 |

## 技术方案

### 1. 词表:受控枚举,34 项分 6 类

标签没有做成自由输入,也没有直接照搬 Semantic Scholar 的 `fieldsOfStudy` ——
后者只有 23 个大类,「Computer Science」是**一个**值。全站都是 CS 论文的项目里,
用户如果只能选这一项,推荐模块从兴趣标签学到的信息量是 **0 bit**。

所以词表是**细粒度研究领域**(推荐系统 / 序列推荐 / 冷启动推荐 / 图神经网络 …),
每个标签**另外携带**它对应的 S2 过滤值与检索词:

```java
RECOMMENDER_SYSTEM("推荐系统", "信息检索与推荐", "Computer Science", "recommender system"),
```

**细粒度用于建模,粗粒度用于检索**,两者不是二选一。

词表定义在代码里而不是数据库表:受控词表的变更应当经过代码评审。用户表里存的是
枚举的 `key()`(小写枚举名),不存展示名 —— 以后改中文展示名不影响已有数据。

### 2. PUT 全量替换

标签最多 10 个,全量重写的开销可以忽略,换来的是语义极其简单:**请求体就是最终状态**。

如果拆成 POST / DELETE / PATCH 三条接口,客户端就要自己维护"本地和服务端是否一致" ——
重试会重复添加、并发编辑会互相覆盖、断线重连要重新同步。这些问题在全量替换模型下根本不存在。

### 3. 校验先于删数据

`replaceFor` 里,词表校验和查重都在删除 **之前**完成。否则一次拼写错误会让请求在删完旧数据
之后才失败 —— 事务虽然会回滚,但**错误响应本身应该保证"什么都没发生"**。
回滚是最后一道防线,不是第一道。用例 F5-11 专门盯这一点。

## 遇到问题

### 问题一(真问题,也是本次最有价值的发现):Hibernate 插入先于删除

换用 Spring Data 派生的 `deleteByUserId` 后,**F5-5 / F5-6 / F5-7 三条用例当场失败**:

```
Duplicate entry '3-recommender_system' for key 'user_interest.uk_user_interest_user_tag'
Duplicate entry '2-information_retrieval' for key 'user_interest.uk_user_interest_user_tag'
Duplicate entry '11-nlp'           for key 'user_interest.uk_user_interest_user_tag'
```

根因:派生删除走的是"查出实体 → 逐个标记删除",而 Hibernate 刷盘时 **INSERT 先于 DELETE 执行**。
于是"标签没变、只是权重变了"这种最常见的场景会**先插新行、再删旧行**,迎面撞上唯一约束。

这在只考虑"增删标签"时完全看不出来 —— 必须先删掉再插入同一个 key 才会触发。

**解决方案**:改用 `@Modifying + @Query` 的 JPQL 批量删除,它是当场执行的,不存在次序问题。

**这个坑值得记两点:**

1. 它是**实测**出来的,不是推理出来的。一开始我就怀疑有这个次序问题,但没有靠推理下结论 ——
   临时把实现换成派生版本跑了一遍,拿到确切的报错信息才改回来。
2. 它反过来证明那三条用例是**有意义的**。如果只测"新增标签"和"清空",派生版本能通过**全部**用例,
   问题会一直潜伏到某天有人改了权重。

证据(含原始报错)已写进 `UserInterestRepository#deleteAllByUserId` 的注释,而不是只留在这份日志里。

### 问题二(自查):排序时拿 key 反推枚举名

写排序比较器时,我一度用 `InterestTag.valueOf(key.toUpperCase())`。

两个毛病:一是 `toUpperCase()` **不带 Locale** —— 土耳其语环境下 `i` 会变成 `İ`,直接抛异常;
二是它把"key 就是小写枚举名"这条实现细节复制到了第二处,将来 key 规则一改,这里会静默错位。

改成走已有的 `InterestTag.findByKey(key).map(InterestTag::ordinal).orElse(Integer.MAX_VALUE)`,
并给查不到的情况兜一个最大值而不是抛异常 —— **排序不该成为第二个失败点**。

### 问题三(小):DTO 里的 weight 用 `Integer` 而不是 `int`

原先用 `int`,缺 `weight` 字段时会被反序列化成 `0`,再被 `@Min(1)` 拦下,报"weight 必须大于等于 1"。
用户明明**没填**,却被告知**填错了数字** —— 报错信息指向了错误的原因。

改成 `Integer` + `@NotNull`,缺字段时报的是"weight 不能为空"。用例 F5-10 覆盖。

### 问题四(文档错误,靠冒烟测试抓出来):标签数写成了 33,实际是 34

集成测试跑完全绿之后,我另外起了一个真实实例(8081,连开发库 `paperpulse`)做冒烟测试,
把词表的结构打出来看了一眼:

```
分类数: 6 | 标签总数: 34
```

而我在 README / CHANGELOG / 设计文档 / 本日志里都写的是 **33**。

**为什么测试抓不到:**F5-1 的断言是 `assertThat(countTags(categories)).isEqualTo(InterestTag.values().length)`
—— 它拿**枚举自己**当基准,枚举有几个就认几个。这条断言防的是"漏序列化了某一部分",
**天然防不住写在文档里的数字**。

**教训**:凡是"文档里的数字"和"代码里的常量"并存的地方,两者之间没有约束关系。
要么让文档引用代码(但 Markdown 做不到),要么接受这一点、并在改动时**用真实输出核对**,
而不是凭印象写。这次核对的手段就是冒烟测试 —— 这也是它除了"验证能跑通"之外的第二个作用。

六处已全部改正(README 未写数量,不在其中)。

## 测试结果

```
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
```

| 测试类 | 用例数 | 本次状态 |
| ---- | ---- | ---- |
| `AuthApiIntegrationTest`(F3) | 10 | ✅ |
| `AuthorizationApiIntegrationTest`(F4) | 9 | ✅ |
| `ErrorHandlingIntegrationTest`(E) | 6 | ✅ |
| `InterestApiIntegrationTest`(F5) | 15 | ✅ 新增 |
| `BackendApplicationTests` | 1 | ✅ |

**连跑三轮,均为 41/41 通过,退出码 0。**

F5 用例按风险分组:

| 分组 | 用例 | 针对的风险 |
| ---- | ---- | ---- |
| 词表 | F5-1 / F5-2 | 词表完整性、未登录 401 |
| 替换语义 | F5-3 ~ F5-8 | 少传的标签要真的消失、空数组要真的清空 |
| **写入次序** | **F5-6 / F5-7** | **改权重时标签没变,先插后删会撞唯一约束** |
| 校验 | F5-9 ~ F5-13 | 权重越界、缺 weight、未知标签、重复标签、超上限 |
| 边界 | F5-14 | **恰好** 10 个必须被允许 |
| 隔离 | F5-15 | 用户之间互不影响 |

已验证:测试仍只连 `paperpulse_test`,跑完后开发库 `paperpulse` 的用户数据完好;
整个 F5 测试类耗时 2.7 s(容器复用,不用重启)。

### 另外做了一次冒烟测试(集成测试覆盖不到的一条路径)

测试 profile 用 `ddl-auto: create-drop`,而开发 profile 用 `update` ——
**"新表能不能被 `update` 正确建出来"这件事,集成测试永远测不到**,因为它的表是删了重建的。

所以在 8081 起了一个真实实例(连开发库 `paperpulse`)走了一遍完整链路:

| 步骤 | 结果 |
| ---- | ---- |
| 注册 + 登录 | 201 / 200 |
| `GET /api/interests` | 6 分类、34 标签、权重 1–5、上限 10 —— **新表自动建出,无需手工迁移** |
| `PUT` 写入两个标签 | 200,返回按词表顺序排好的列表 |
| `PUT` 改其中一个权重 | 200 —— **真实实例上同样没触发"先插后删"** |
| `GET` 读回 | 与写入一致 |

顺带更正了文档里写错的标签数量(见「问题四」)。跑完用 `scripts/kill-port.ps1 -Ports 8081` 回收,`netstat` 复查无残留。

## 上一节遗留问题的状态

- `AccessDeniedHandler`(403)未配 —— **仍然未配**,现在依然没有角色概念,不触发
- 无 CORS 配置 —— **仍然未配**,前端接入时补

## 下一步计划

**F6:收藏 / 阅读历史 / 论文评分**,做完 REQ-001 就整体完成。

F6 与 F5 有一个共同点:都是"用户 × 论文"的关联数据。但 F6 会多出一个 F5 没有的问题 ——
**论文本身不在本地库里**(要等 REQ-002 检索回来才有)。这需要在动手前先定下来:
是先把论文信息落到本地表(冗余存储,后续离线可用),还是只存外部 ID(轻量,但每次都要回源)。
这是 F6 的第一个设计决策,不急着写代码。

---

# 2026-09-16(第九次:F6 收藏/阅读历史/评分,REQ-001 完成)

## 本次目标

REQ-001 的最后一个功能点。前面五个功能点建的是"用户是谁"和"用户想要什么",
F6 处理"用户做了什么" —— 这是 REQ-004 推荐算法的**行为数据底座**。

本次开工前先定了三件事(前两件由用户拍板):

| 决定 | 结论 |
| ---- | ---- |
| 论文元数据怎么存 | **落本地库** —— 只存外部 ID 的话 S2 一挂收藏夹就打不开,离线评测也没法复现 |
| 评分形式 | **1–5 星** —— 与 F5 兴趣权重同一套刻度 |
| 前端什么时候做 | **等 REQ-001 全部完成再统一做** —— 前端只写一遍,不因接口变动返工 |

## 完成内容

- 新增 `com.paperpulse.paper` 包:论文语料(与用户无关)
- 新增 `com.paperpulse.library` 包:收藏 / 阅读历史 / 评分
- 新增 **4 张表**:`paper`、`paper_favorite`、`paper_read_history`、`paper_rating`,各带唯一约束
- 新增 **10 个接口**
- 新增 **25 条集成测试**
- 设计文档 `docs/design/F6-收藏-阅读历史-评分.md`
- 全量回归 **66/66 通过**(F3=10,F4=9,E=6,F5=15,F6=25,上下文=1),连跑三轮稳定
- **`pom.xml` 零改动** —— 仍然没有引入任何新依赖
- 顺手修掉 F5 遗留的一个 Locale 缺陷(见「问题二」)

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `main/paper/Paper.java` | 新增。论文实体,元数据只增不减 |
| `main/paper/PaperSource.java` | 新增。来源枚举 |
| `main/paper/StringListConverter.java` | 新增。作者列表 ⇄ JSON 文本列 |
| `main/paper/PaperRepository.java` | 新增 |
| `main/paper/PaperService.java` | 新增。幂等 upsert + 并发冲突恢复 |
| `main/paper/PaperController.java` | 新增。`POST /api/papers` |
| `main/paper/dto/PaperInput.java`<br>`main/paper/dto/PaperResponse.java` | 新增 |
| `main/library/PaperFavorite.java`<br>`main/library/PaperReadHistory.java`<br>`main/library/PaperRating.java` | 新增。三个实体 |
| `main/library/*Repository.java` | 新增。三个仓储 |
| `main/library/LibraryService.java` | 新增。三类行为的业务逻辑 |
| `main/library/LibraryController.java` | 新增。9 个接口 |
| `main/library/dto/*.java` | 新增。3 个响应 + 1 个请求 |
| `main/interest/InterestTag.java` | **改**。`key()` 补上 `Locale.ROOT` |
| `test/support/ApiClient.java` | 改。补带鉴权的 POST / DELETE |
| `test/support/AbstractIntegrationTest.java` | 改。清表时一并删 4 张新表 |
| `test/library/LibraryApiIntegrationTest.java` | 新增。F6,25 条 |
| `docs/design/F6-收藏-阅读历史-评分.md` | 新增。设计文档 |
| `docs/requirements.md` | F6 → ✅ Done;**REQ-001 → ✅ Done** |
| `README.md` | API 表补 10 个接口;测试类表补 F6 |
| `CHANGELOG.md` | 补 F6 条目 |

## 技术方案

### 1. 论文语料与用户行为分成两个包

`Paper` **不属于任何用户** —— 它是公共语料,REQ-002 检索回来之后也会往这里写。
收藏/历史/评分则是用户私有的行为数据。两者的生命周期不同,所以分成 `paper` 与 `library` 两个包,
而不是塞进一个"论文相关"的大包。

### 2. 状态 vs 事件 —— 本次最重要的一个区分

| | 收藏 | 评分 | 阅读 |
| --- | --- | --- | --- |
| 语义 | 状态 | 状态 | 可重复的行为 |
| 重复操作 | 幂等,返回已有的 | 覆盖分数 | **计数加一**,时间前移 |

**写反了不会报错,只会慢慢攒出脏数据。** 把收藏当事件记,用户取消再收藏就有两条;
把阅读当状态记,读十次只算一次。所以三者写法不同,并且各有用例盯着。

### 3. 幂等的边界

同样是"重复操作",两个接口的响应**刻意不同**:

- 重复收藏 → **200**(它已经处于你要的状态了)
- 清空空历史 → **204**(结果状态一致)
- 取消没收藏过的 → **404**(根本没有这回事)

分界线是"结果状态是否与意图一致"。这不是吹毛求疵:前端拿着过期的界面点删除,
回 200 会让它以为删掉了,用户下次刷新发现又冒出来,却不知道哪一步出的问题。

### 4. 并发首次提交同一篇论文

F6 里唯一一处"写错会直接报 500"的地方。

```
请求 A: SELECT → 没有 → INSERT ✓
请求 B: SELECT → 没有 → INSERT ✗ Duplicate entry
```

数据源不在事务里 —— 论文来自外部检索,多用户同时收藏一篇热门论文是**正常使用**,不是异常。

处理:插入放在**独立的 `REQUIRES_NEW` 事务**里,失败就重查。两个细节缺一不可:

1. **必须独立事务。** 同一事务里刷盘失败后 Hibernate 会话已不可用,紧接着的查询会跟着失败。
2. **不能用 `@Transactional` 注解。** 私有方法上的注解不生效,同类自调用也绕过代理,
   而且**不会有任何报错**。用 `TransactionTemplate` 显式划定边界没有这个陷阱。

### 5. 路径里不出现用户 id

所有接口挂在 `/api/users/me` 下,用户一律取自 token。写成 `/api/users/{userId}/...` 的话,
每个方法都要校验"路径上的 id 是不是你自己",漏掉一处就是越权。
**让这种错误写不出来,比写对更重要。**

## 遇到问题

### 问题一(真问题):并发冲突恢复是必须的,不是防御性代码

原本我把并发处理当作"写得严谨一点",打算先写上、后面再说。实测之后发现它比预想的重要得多。

**实测方法**:八个线程用 `CyclicBarrier` 对齐后同时提交同一篇新论文。

**结果**:日志里出现 **7 条** `Duplicate entry 'SEMANTIC_SCHOLAR-CONCURRENT-001'
for key 'paper.uk_paper_source_external_id'` —— 八个线程里有七个真的撞上了,
全部靠恢复代码拿到同一个 id 并成功返回。

**反向验证**:把恢复代码换成直接 `throw ex` 后重跑,**只有 F6-25 这一条失败**,
报 `ExecutionException: org.springframework.dao.DataIntegrityViolationException:
Duplicate entry ...`。

八分之七的碰撞率远高于直觉。原因不难想:八个线程被 `CyclicBarrier` 对齐到同一毫秒,
而"SELECT 到 INSERT"之间的窗口相对这个时间尺度并不窄。

**顺带一个反面决定**:阅读次数 `readCount` 的更新是"读出来 +1 再写回",并发时会丢一次计数,
**这个没有处理**。理由是严重程度不同 —— 阅读次数是弱信号,少算一次用户看不到任何异常;
论文冲突会让用户看到 500。同样的问题,不同的处理,是有意的取舍而不是遗漏。
两处注释里都写明了这一点。

### 问题二(F5 遗留缺陷):`InterestTag.key()` 漏了 Locale

写 `PaperSource.key()` 时我按 F5 日志里自己写下的教训加了 `Locale.ROOT`,
顺手搜了一下全代码库,发现 **`InterestTag.key()` 本身就是漏的那个** ——
F5 的日志里我写了"`toUpperCase()` 不带 Locale 是个坑",却只改了排序比较器,没改 `key()`。

后果比排序严重得多:

```java
INFORMATION_RETRIEVAL.name().toLowerCase()   // 土耳其语环境下
  → "ınformation_retrieval"                  // 无点的 ı
```

它和库里存的 `information_retrieval` 对不上,读取时被 `findByKey` 判为"词表里没有这个标签"
而**静默过滤掉**。用户看不到任何报错,只是已保存的兴趣凭空少了几项 —— 而且
`INFORMATION_RETRIEVAL`、`REINFORCEMENT_LEARNING`、`MULTIMODAL` 这些名字里都带 `I`。

已改为 `Locale.ROOT`。**这个 bug 只在土耳其语区域的 JVM 上出现**,本机永远复现不了,
所以它不是"测试没覆盖到",而是"测试不可能覆盖到" —— 只能靠写代码时的一致性来防。

### 问题三(自查):`instanceof` 链

`LibraryService` 里三个列表方法都要"从一批行为行里取出 paperId 再批量查论文",
我最初写了一个共用的私有方法,里面用 `instanceof` 逐个判断实体类型:

```java
if (row instanceof PaperFavorite favorite) { return favorite.getPaperId(); }
if (row instanceof PaperReadHistory entry)  { return entry.getPaperId(); }
return ((PaperRating) row).getPaperId();
```

写的时候就别扭。改成每个调用方自己抽 id:

```java
List<Long> ids = rows.stream().map(PaperFavorite::getPaperId).toList();
```

三个调用方各多一行,换来的是一个不需要类型判断的版本 —— 而且**新增第四类行为时,
编译器会提醒你**,而不是让那个强制转换在运行时炸掉。

## 测试结果

```
Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
```

| 测试类 | 用例数 | 本次状态 |
| ---- | ---- | ---- |
| `AuthApiIntegrationTest`(F3) | 10 | ✅ |
| `AuthorizationApiIntegrationTest`(F4) | 9 | ✅ |
| `ErrorHandlingIntegrationTest`(E) | 6 | ✅ |
| `InterestApiIntegrationTest`(F5) | 15 | ✅ |
| `LibraryApiIntegrationTest`(F6) | 25 | ✅ 新增 |
| `BackendApplicationTests` | 1 | ✅ |

**连跑三轮,均为 66/66 通过,退出码 0。**

### 反向验证(证明用例不是陪跑的)

沿用 F5 的做法:故意把实现改错,看是否**恰好**只有对应的用例失败。

| 改坏的地方 | 结果 | 结论 |
| ---- | ---- | ---- |
| `applyMetadata` 改成无条件覆盖 | 只有 **F6-6** 失败 | 而 F6-5(检查"新值确实被更新")仍然通过 —— 能区分"用真实数据更新"和"用空值抹掉" |
| 并发恢复换成直接重抛 | 只有 **F6-25** 失败 | 恢复代码是承重的 |

两处都已恢复,恢复后重跑三轮全绿。

### 冒烟测试(集成测试覆盖不到的路径)

测试 profile 用 `create-drop`,开发 profile 用 `update` —— "新表能不能被 `update`
正确建出来"只有真实实例能验证。在 8081 起了一个实例连开发库 `paperpulse`:

| 步骤 | 结果 |
| ---- | ---- |
| 提交论文 | 200,拿到本地 id = 1 |
| 重复提交同一篇 | 同一个 id,**复用而非新建** |
| 收藏 / 读两次 / 打分 | 200 / 200 / readCount=2 / 200 |
| 读回三个列表 | 各 1 条,论文标题与两位作者正确内嵌 |
| 取消收藏 / 再取消 | 204 / **404** |

随后直接查库确认(`mysql` 客户端在 `C:\Program Files\MySQL\MySQL Server 8.0\bin\`,
不在 PATH 里):

```
paper / paper_favorite / paper_rating / paper_read_history / user_interest / users

paper.uk_paper_source_external_id       = (source,external_id)
paper_favorite.uk_paper_favorite_user_paper = (user_id,paper_id)
paper_rating.uk_paper_rating_user_paper     = (user_id,paper_id)
paper_read_history.uk_paper_read_user_paper = (user_id,paper_id)
```

**4 张新表全部由 `ddl-auto: update` 自动建出,唯一约束齐全,无需手工迁移。**
开发库原有用户数据完好。跑完用 `scripts/kill-port.ps1 -Ports 8081` 回收,`netstat` 复查无残留。

## 上一节遗留问题的状态

- `AccessDeniedHandler`(403)未配 —— **仍然未配**,现在依然没有角色概念,不触发
- 无 CORS 配置 —— **仍然未配**,前端接入时补(现在前端要开始接入了,这条该提上日程)

## 下一步计划

**REQ-001 到此六个功能点全部完成。** 后端已经有了:用户、鉴权、兴趣标签、行为数据。

接下来有两个方向,按依赖关系应该这样排序:

1. **前端**(用户已决定"等 REQ-001 做完再统一做") —— 登录页、兴趣选择器、收藏管理页。
   现在接口稳定了,前端只写一遍。**顺带必须补 CORS 配置**,否则前端连不上。
2. **REQ-002 检索 Agent**(Python 侧) —— 让论文真正从 Semantic Scholar 流进来。

推荐先做前端:它是 REQ-001 的收尾,也是第一次能把已完成的东西**演示出来**;
而且 REQ-002 做完后又会有新的界面需求,分开做比堆在一起容易。

**F6 留给 REQ-002 的一件事**:论文目前由客户端提交元数据,后端不校验它是否真的来自所声称的来源。
REQ-002 落地后,论文应当由后端自己从 S2 拉取写入,`POST /api/papers` 收窄为只读 ——
那样这条路径就没有"客户端可抢占 externalId"的问题了。

# 2026-09-29

## 本次目标

REQ-001 后端六个功能点已完成、接口稳定,但只能通过 curl 触及。本次开始前端,
第一个增量 FE-1 建立工程骨架并打通登录闭环 —— 它是所有后续页面的地基,
决定路由结构、请求层与状态管理怎么做。

## 完成内容

- **工程骨架**:Vue Router 路由 + 全局登录守卫、axios 请求层(token 注入 / 统一错误归一化 / 401 处置)、
  登录态 store(localStorage 持久化)、登录后外壳 `AppLayout`
- **登录 / 注册页**:表单校验规则与后端约束一致;后端 `fieldErrors` 回填到对应表单项;注册成功后自动登录
- **首页**:挂载时回查 `GET /api/users/me`,顺带验证手中 token 是否仍然可用
- **联调方案**:Vite dev / preview 代理 `/api` 到后端,不写 CORS 配置(理由见技术方案)
- **Element Plus 按需引入**:首屏 JS 从 1009 KB 降到最大单块 79 KB
- **前端测试设施**:引入 Vitest + jsdom,14 条单测
- **脚手架清理**:删除 `HelloWorld.vue`、示例图片与图标(纯占位物,无功能)

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `frontend/src/api/client.js` | 新增:axios 实例、拦截器、`ApiError` 归一化 |
| `frontend/src/api/auth.js` | 新增:注册 / 登录 / 当前用户 |
| `frontend/src/stores/auth.js` | 新增:登录态与持久化 |
| `frontend/src/router/index.js` | 新增:路由表与登录守卫 |
| `frontend/src/layouts/AppLayout.vue` | 新增:登录后外壳 |
| `frontend/src/views/LoginView.vue` 等三个视图 | 新增:登录 / 注册 / 首页 |
| `frontend/src/api/client.spec.js`、`stores/auth.spec.js` | 新增:单元测试 |
| `frontend/vite.config.js` | 代理、Element Plus 按需引入、Vitest 配置 |
| `frontend/src/main.js`、`App.vue`、`style.css`、`index.html` | 重写:去掉脚手架示例 |
| `frontend/package.json` | 依赖与 `test` 脚本 |
| `docs/design/F7-前端骨架与登录.md` | 新增:设计文档 |
| `frontend/src/components/`、`src/assets/`、`public/icons.svg` | 删除:脚手架占位 |

## 技术方案

### 1. 开发期用代理,不写 CORS

上一节说"做前端时必须补 CORS",那是假设前端直连 8080。改用 Vite 代理后,
浏览器视角下前后端同源,**dev 与 preview 都不需要 CORS**。

后端 CORS 留到部署拓扑确定时再加:那时才知道前端是被 nginx 反代到同域,还是独立域名。
现在写只能填 `localhost` 猜一个,是一份**无法验证**的配置。

### 2. 401 要区分"会话过期"和"密码错误"

登录接口凭据错误也返回 401。若拦截器对所有 401 一律登出跳转,就混淆了两种语义。
所以 `/auth/**` 的 401 排除在外,只把消息交给登录页展示。

### 3. 分层与依赖方向

`views → api / stores`,`api/client → stores + router`,下层不反向依赖视图。
路由的页面组件一律懒加载,顺带避免了 `router → view → api/client → router` 的模块循环。

## 遇到问题

### 问题一(真问题):vue-router 装成了 5.x

预期 4.x,`npm install vue-router` 装到的是 **5.3.1**。5.x 有哪些破坏性变更没有把握,
不能想当然。查了随包发布的类型定义:

```ts
type NavigationGuardReturn = void | Error | boolean | RouteLocationRaw;
```

守卫返回 `RouteLocationRaw` 仍然有效,`createRouter` / `createWebHistory` / `useRouter` / `useRoute`
四个关键 API 都在 —— 本次用到的部分兼容。**结论来自查证而不是假设**;
若直接假定 4.x 的行为,风险要到运行时才暴露。

### 问题二(自查):首屏 JS 1 MB

Element Plus 全量引入(`app.use(ElementPlus)` + 全量 CSS)让首屏 JS 到 **1009 KB(gzip 326 KB)**,
构建器直接给出 chunk 体积告警。

改用 `unplugin-auto-import` + `unplugin-vue-components` 按需引入后:

| 指标 | 全量引入 | 按需引入 |
| ---- | ---- | ---- |
| 最大单块 JS | 1009 KB(gzip 326 KB) | 79 KB(gzip 31 KB) |
| 构建告警 | 有 | 无 |

代价是 `ElMessage` 这类 API 不再有显式 import(由插件注入),代码里看不到引入来源 ——
所以 `vite.config.js` 里写明了两个插件的作用,并用 `dts: false` 避免生成无用的类型声明文件。

**验证方式**:不能只看体积数字。`v-loading` 是靠指令解析器引入的,静默失效的话体积照样小、功能却没了。
所以另外确认了 `el-loading-mask` 样式确实进了产物包。

## 测试结果

### 单元测试(14 条)

```
cd frontend && npm test
Test Files  2 passed (2)
Tests  14 passed (14)
```

覆盖:token 注入(有 / 无)、错误体归一化(校验失败 / 网络不可达 / 无 message)、
401 清登录态、**登录接口的 401 不触发登出**、非 401 不动登录态、
登录态持久化与恢复、存储损坏时按未登录处理。

### 端到端冒烟(22 项)

后端起在 **8081**(不占用 8080),前端 dev server 起在 5173,
所有请求都打到 **5173**,验证「浏览器 → Vite 代理 → 后端 → MySQL」整条链路。

```
python scratch/frontend_smoke.py
共 22 项,通过 22,失败 0
```

覆盖:静态页可取、注册 201、重复用户名 409、非法参数 400 且带 `fieldErrors`、
登录 200(带 token / tokenType / expiresIn)、错误密码 401、带 token 取用户 200、
无 token / 篡改 token / 非 Bearer 认证头均 401 且为统一错误格式、
兴趣词表与收藏列表经代理可达。

### 反向验证(证明用例是承重的)

沿用 F5 / F6 的做法:把 `onResponseError` 里的 `/auth/**` 例外去掉再跑 ——

```
× 登录接口的 401(凭据错误)不触发登出
Tests  1 failed | 13 passed (14)
```

**恰好只有那一条失败**。恢复后重跑 14/14 全绿。

### 后端回归

后端本次**零改动**,仍跑一遍确认:

```
Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 下一步计划

- **FE-2 兴趣标签选择器**:34 标签 / 6 分类、1–5 权重、PUT 全量替换,挂在 `AppLayout` 下
- **FE-3 收藏 / 阅读历史 / 评分管理**:需要先有论文数据 —— 检索属 REQ-002,
  在那之前要给一个临时的论文录入入口
- **验收方式**:`cd frontend && npm run dev`(默认代理到 8080);
  后端在 8081 时用 `BACKEND_ORIGIN=http://localhost:8081 npm run dev`

**已知取舍(不是遗漏)**:

1. 登录 / 注册两页的卡片样式有约 35 行重复。两处重复尚在可接受范围(第三次出现时再抽 `AuthLayout`)。
2. token 存 localStorage 有 XSS 读取面。本项目没有把用户输入当 HTML 渲染的地方,当前可接受;
   要收紧可换 httpOnly Cookie + CSRF 防护。
3. 页面观感无法由脚本验证 —— 接口链路与编译产物都验过了,**视觉效果需要打开浏览器确认**。

# 2026-09-29(第二次)

## 本次目标

用户要求「页面简洁风、以蓝白为基调、可稍作装饰」。据此定下全站视觉规范并落地为设计令牌,
然后在其上实现第二个前端增量 FE-2:兴趣标签选择器。

## 完成内容

- **视觉规范落地**:设计令牌集中在 `styles/theme.css`,含墨色三级 + 单一强调色 `#2563EB`、
  四级字号、三级字重、4px 基数间距、权重梯 w1–w5;Element Plus 通过 `--el-*` 变量整体换色
- **登录 / 注册页改版**:抽出 `AuthShell` 消除两页重复的卡片外壳;品牌脉冲线标记;
  **接口级错误改用表单内提示条,不再用 `ElMessage` 浮层**(浮层容易被漏看)
- **顶栏外壳改版**:自绘导航(不用 `el-menu`,其默认悬停底色与克制观感冲突最重),
  导航项由路由表推导;头像取用户名首字
- **首页改版**:账号信息 + 右侧兴趣标签卡片(真实数据,迷你权重刻度)
- **兴趣标签选择器(FE-2)**:6 分类 / 34 标签 / 权重 1–5 / 上限 10;
  点标签本体切换、点第 N 格设权重、← → 微调;右栏「已选强度」按权重降序;
  未保存改动离开时确认
- **前端测试大幅补齐**:43 条(原 14 条),新增的挂载测试真实渲染视图并驱动交互

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `frontend/src/styles/theme.css` | 新增:设计令牌 + Element Plus 覆盖与组件补丁 |
| `frontend/src/styles/components.css` | 新增:跨视图复用的类 |
| `frontend/src/composables/useInterestSelection.js` | 新增:选择逻辑(不依赖 DOM) |
| `frontend/src/api/interests.js` | 新增:词表 / 我的兴趣 / 全量替换 |
| `frontend/src/views/InterestView.vue` | 新增:选择器页面 |
| `frontend/src/components/{BrandMark,WeightTicks,AuthShell}.vue` | 新增:复用组件 |
| `frontend/src/test/flush.js` | 新增:测试用的异步等待助手 |
| `frontend/src/views/LoginView.vue`、`RegisterView.vue`、`HomeView.vue` | 重写:按视觉规范改版 |
| `frontend/src/layouts/AppLayout.vue` | 重写:顶栏外壳 |
| `frontend/src/router/index.js` | 加 `/interests` 路由与由路由表推导的导航项 |
| `frontend/src/main.js`、`style.css` | 样式引入顺序;基础样式改为用令牌 |
| `frontend/vite.config.js` | Vitest 内联 element-plus(否则 Node 加载 .css 报错) |
| `docs/design/F8-兴趣标签选择器与视觉规范.md` | 新增:设计文档 |

## 技术方案

### 1. 视觉方向怎么定的

四个独立设计方向(极简留白 / 柔和层次 / 学术纸感 / 现代工具感)各自产出一份可在浏览器打开的
静态预览,再由四个视角(简洁克制 / 配色与对比度 / 可用性 / 与组件库共存)评审,最后合成定稿:
以「极简留白」为骨架,吸收另外三处 —— 权重五档文字色恒定、占位符升级到 5.1:1、标签页右栏名单。

主色定 `#2563EB` 而不是 Element Plus 默认的 `#409EFF`:后者白底只有 3.0:1,做正文链接不达标。
对比度一律以最不利底色 `#F6F8FB` 核算。预览文件留在本机 `scratch/ui/`。

### 2. 权重的三重编码

不允许"只靠颜色"表达权重 —— 色觉障碍下读不出强度。所以:文字色五档恒定(不随权重变淡)、
底色五档递深、刻度点亮格数 = 权重。亮格用深一档的蓝,保证与未亮格在五档底色上的对比 ≥3:1。

## 遇到问题

### 问题一(真问题):样式覆盖被组件库盖掉

Element Plus 的样式是按需引入的,落在各路由的**异步 chunk** 里,加载顺序晚于 `theme.css`。
同特异性时后者胜,所以直接写 `:root` 的覆盖会被组件库默认值盖掉 —— 而且**构建、测试全绿,
只有肉眼看页面才发现颜色没生效**。

处理:覆盖写成 `:root:root`、组件补丁写成 `:root .el-xxx`,把特异性提到 (0,2,0),与加载顺序解耦。

**验证方式**:构建产物里同时存在 `--el-color-primary:#2563eb`(我的)与 `#409eff`(EP 的) ——
确认了这条不是多虑,靠的确实是特异性而不是顺序。

### 问题二(真问题):Element Plus 把错误提示防抖了 100ms

`form-item` 内部是 `refDebounced(validateState, 100)`,错误提示要 100ms 后才渲染。
手动使用时完全察觉不到,但断言 DOM 的测试会扑空 —— 两条测试挂了,报"找不到 `.el-form-item__error`"。

处理:加 `waitFor(poll, timeout)` 轮询助手,而不是靠"多 await 几次 nextTick"这种碰运气的写法。
**这个值是查 `node_modules` 里的实现找到的,不是猜的。**

### 问题三(设计稿的逻辑漏洞):清空后无法保存

设计原稿写的是"选中数为 0 时禁用保存按钮"。这会让**用户把标签全清空后再也保存不了**,
页面永远卡在未保存状态。

改为按"是否有未保存的改动"判断,并把 0 项时的按钮文案改成「清空并保存」,
让"这次会清空"在点之前就看得见。

### 问题四(自查):设计评审的打分汇总不可靠

四个评审 agent 给方案打分时,填的方案名不一致(有的带文件路径后缀,有的带括号注释),
按名字求和后票被拆散,`ranked` 里的分数是碎的。

好在合成环节没有盲信那个排名,而是按设计质量重组了方案,最终结果没问题。
**教训**:让 agent 返回用于聚合的标识时,应该先给它们一个受控的取值集合,而不是自由文本。

### 问题五(自查):测试里的定时器没清理

上限提示用 `setTimeout` 1.8s 后复原,但组件卸载时没有 `clearTimeout`。
页面离开后定时器仍会去改已经不需要的状态。已补 `onUnmounted` 清理。

## 测试结果

### 单元与挂载测试(43 条)

```
cd frontend && npm test
Test Files  6 passed (6)
Tests  43 passed (43)
```

本次新增 29 条,其中**新增的挂载测试是关键**:用 jsdom 真实挂载视图并驱动交互 ——
构建通过不等于页面能跑,模板错误、指令解析、组件属性这些只有挂载才暴露。覆盖:

- `useInterestSelection`:上限、权重区间、取消、清空、载荷形状(12 条)
- `InterestView` 挂载:按词表渲染分类、回显已保存权重、点击选中 / 取消、点第 N 格设权重、
  达上限点不动并提示、保存按钮的启用条件、保存提交完整载荷并用返回值重绘、
  清空后按钮变「清空并保存」、加载失败不白屏(10 条)
- `LoginView` 挂载:表单渲染、提交成功写登录态并跳转、
  **凭据错误显示错误条而非浮层**、字段级错误贴到对应输入框(4 条)
- `AppLayout` 挂载:品牌与头像、导航项由路由表推导且高亮当前页、退出清空登录态并跳转(3 条)

### 端到端冒烟(54 项)

```
python scratch/frontend_smoke.py    # 22 项,全通过
python scratch/interest_smoke.py    # 32 项,全通过
```

后者是本次新增,专测兴趣接口契约:词表结构(6 分类 / 34 标签 / 上限 10 / 权重区间)、
全量替换与排序、幂等性、以及**全部边界与异常** —— 超 10 个标签、重复标签、未知标签、
权重越界、漏传权重、漏传字段、未登录、以及**非法请求后原数据完好**。

### 构建

```
npm run build → ✓ built in 354ms,无告警
```

按需引入后最大单块 87 KB(gzip 34 KB)。

## 下一步计划

- **FE-3 收藏 / 阅读历史 / 评分管理**:复用现有的卡片、标签、请求层与错误处理;
  需要先有论文数据 —— 检索属 REQ-002,在那之前要给一个临时的论文录入入口
- **验收方式**:`cd frontend && npm run dev`(默认代理到 8080);
  后端在 8081 时用 `BACKEND_ORIGIN=http://localhost:8081 npm run dev`

**已知取舍(不是遗漏)**:

1. 登录 / 注册两页的卡片外壳已抽成 `AuthShell`;其余样式复用通过 `styles/components.css` 的全局类实现,
   而不是每个组件各写一份。
2. 没有做深色模式。令牌已按"墨色 / 面色 / 线色 / 权重梯"分离,换值即可,不必改组件。
3. 34 个标签平铺约 700px 高,靠右栏名单解决"查看"。分类折叠留到实际使用中确有需要时再做。
4. 视觉效果依旧只能由人确认 —— 挂载测试能证明"渲染出来了、点击有反应",
   但**好不好看、间距是否舒服,必须打开浏览器看**。

# 2026-09-29(第三次)

## 本次目标

开始 REQ-002 检索 Agent。用户需求是「自然语言 → 检索 → 返回带来源的文献列表」,
本次完成它的后端两半:Python 侧的 Agent 与检索(P1),Java 侧的接口与落库(P2)。

## 完成内容

- **Python 侧**:DeepSeek 客户端、查询拆解 Agent、多源检索(含降级链)、`POST /search` 接口;
  检索逻辑从调试脚本收进服务,脚本改为调用它(避免两份实现)
- **Java 侧**:`POST /api/papers/search` —— 调 ai-service、把结果落库、返回带本地 id 的论文
- **堵掉了 F6 留下的技术债**:论文元数据现在由**后端自己拉取并写入**,
  客户端再也无法抢占 `(source, externalId)`
- **补齐 ai-service 的测试设施**:引入 pytest,29 条用例
- **AI 实验记录** `experiments/experiment-001.md`:提示词 v1 → v2 的实测对比

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `ai-service/app/config.py`、`llm.py`、`schemas.py`、`agent.py`、`sources.py` | 新增:配置、LLM 客户端、契约、查询拆解、多源检索 |
| `ai-service/app/main.py` | 新增 `POST /search` |
| `ai-service/scripts/search_papers.py` | 改为调用 `app.sources`,不再自带一份实现 |
| `ai-service/tests/`、`conftest.py`、`requirements-dev.txt` | 新增:测试设施与 29 条用例 |
| `backend/.../search/`(`SearchController` / `SearchService` / `AiSearchClient` / dto) | 新增:检索接口与落库编排 |
| `backend/.../common/ApiException.java` | 新增 `badGateway`(502)与 `serviceUnavailable`(503) |
| `backend/.../resources/application.yml` | 新增 `app.ai-service.base-url` |
| `backend/src/test/.../support/StubAiService.java` | 新增:测试用的真实 HTTP 桩服务 |
| `backend/src/test/.../search/SearchApiIntegrationTest.java` | 新增:13 条集成测试 |
| `experiments/experiment-001.md` | 填写:提示词实验记录 |
| `.env.example`、`ai-service/requirements.txt` | 补 `SEMANTIC_SCHOLAR_API_KEY`、`python-dotenv` |

## 技术方案

### 1. 论文由后端落库,而不是回传给前端再提交

这是本次最重要的一条。F6 时论文元数据由客户端提供,后端不校验它是否真来自所声称的来源 ——
一个登录用户理论上可以抢占某个 `(source, externalId)` 并写入错误的标题。当时的判断是
"等 REQ-002 落地后收窄",现在兑现了:

```
浏览器 → Java(鉴权) → Python(检索) → Java 落库 → 返回带本地 id 的论文
```

元数据必须来自真正抓取它的那一方。前端拿到 id 后可以直接收藏 / 评分,不必再走一次元数据提交。

### 2. 不上 LangChain

要的是"自然语言 → 结构化检索参数",这是一次 JSON 输出调用:一个提示词 + `response_format: json_object` 就够。
引 LangChain 只增加依赖和一层抽象,换不来实际能力。

### 3. 先不做流式

检索是一次性动作(实测约 10 秒),不像对话需要逐字输出。跨两个服务做 SSE 转发复杂度明显上升,
而收益要等 REQ-003 的多轮问答才显现 —— 那时再做。

### 4. Agent 只产出检索参数,不产出论文

把边界划在这里,模型就没有编造文献的机会:它说的论文一篇都不作数,列表里的每一条都来自真实数据库返回。

### 5. 上游故障用 502 / 503,不用 500

502 = 上游返回了错误;503 = 上游连不上。用 500 会让"依赖的服务挂了"和"我们的代码有 bug"
在日志与监控里混在一起 —— 这两者的排查方向完全不同。

## 遇到问题

### 问题一(真问题):模型返回的关键词是数组,`str()` 之后变成垃圾

提示词写的是"给 3~8 个词",模型把它理解成"返回一个词表",`keywords` 是数组。
而代码里是 `str(data.get("keywords"))` —— Python 对列表做 `str()` 得到的是
`"['contrastive learning', 'recommender systems', ...]"`,**带着方括号和引号的字面量**。

这个串会被原样送进检索接口。**危险之处在于它不报错**:接口照样 200,只是搜不到东西。
不把返回结果打印出来看,这个 bug 可以一直藏着。

修法有两层:提示词明确要求"一个字符串",代码里也加兜底(是数组就拼成查询串)。
**兜底不能省 —— 提示词是请求,不是保证。**

### 问题二:堆砌同义词反而稀释检索

模型热心地补了一堆同义词(LLM、denoising diffusion probabilistic models、score-based……)。
学术检索接口做的是**相关性排序**而不是布尔匹配,查询串越长越杂,每篇的相关度打分越被拉平,
前排反而更泛。提示词补上"不要堆砌同义词"后,输入"帮我看看扩散模型"从 7 个词的词汤收敛成
`diffusion models generative deep learning`。

### 问题三:"2024 年以后"被理解成 2025 起

字面上"以后 = 之后",说得通,但**做文献检索时把边界年排除掉会漏结果**。
提示词写明"'X 年以后'理解为 X 年及以后"并说明理由,现在稳定给出 2024。

### 问题四(安全问题,测试抓到的):合法 token + 不存在用户 = 放行

写集成测试时断言"伪造 token 应返回 401",结果返回了 **200**。

查下来不是伪造的问题:`TokenForger` 用正确密钥签名,所以这个 token **签名有效**,
只是 `sub` 指向一个不存在的用户。JwtAuthenticationFilter 刻意不查库(性能考量,
且很多接口不需要用户实体),于是它被当成合法请求放行了。

F4 的用例注释里写着这条原则 ——「**token 完全合法但用户不存在 —— 签名有效 ≠ 应该放行**」,
并且 `GET /api/users/me` 是回查数据库的。问题在于:我的检索接口**不需要用户实体**,
就没有回查,于是成了这条保证的缺口。

修法:检索前先 `userService.getById(userId)`,查不到抛 401 —— 与 `/api/users/me` 同一个落点。
检索虽然用不到用户实体,但"用户被删除后其 token 立刻失效"这条保证要么对所有接口成立,要么等于没有。

**顺带发现的同类缺口**:`GET /api/interests`(标签词表)同样不接触用户实体,因此有一样的缺口。
本次没有一并改(保持改动聚焦),已记在下一步里。

## 测试结果

### Python 单元测试(29 条)

```
cd ai-service && .venv/Scripts/python.exe -m pytest tests/ -q
29 passed
```

覆盖查询拆解的规整(数组形状、两位数年份、起止颠倒、越界年份、缺失关键词)、
年份区间过滤、去重、摘要清洗、空值归一。全部不联网。

### Java 集成测试(13 条)

```
mvn test -Dtest=SearchApiIntegrationTest
Tests run: 13, Failures: 0, Errors: 0
```

ai-service 用一个**真实的 HTTP 桩服务**替代(JDK 自带的 `HttpServer`,不引 WireMock)——
被测路径上除了"对面的服务是谁",HTTP 调用、JSON 反序列化、状态码映射、落库全是真的。
覆盖:落库与本地 id、拆解结果透传、**检索到的论文可直接收藏**、重复检索不重复入库、
元数据不全的条目被跳过、空结果不算错误、请求体形状与默认条数、参数校验、
未登录与伪造 token、上游报错映射 502、连不上映射 503。

### 全量回归(79 条)

```
mvn test
Tests run: 79, Failures: 0, Errors: 0
```

原有 66 条全部通过,无回归。

### 真实端到端冒烟(19 项)

```
python scratch/search_smoke.py
共 19 项,通过 19,失败 0
```

**这条链路是真的**:真的调 DeepSeek 拆解、真的检索外部数据库、真的落库。
实测一次检索约 10.2 秒(大模型拆解 + 外部接口往返)。

```
输入:找 2024 年以后对比学习在推荐系统里的应用
拆解:contrastive learning recommender systems(2024 至今)
命中:Crossref
结果:[2] GenGCL: Generative Graph Contrastive Learning…(2026)
      [3] Disentangling Context from Auxiliary Information…(2026)
      [4] Multiview graph dual-attention deep learning…(2025)
```

拿到 id 后直接收藏成功 → 证实"客户端不必提交元数据"这条路径通了。

## 下一步计划

- **P3 前端检索页**:输入框 + 结果卡片 + 收藏 / 评分入口,挂在 `AppLayout` 下
- **`GET /api/interests` 的同类缺口**:它也不回查用户,合法 token + 已删除用户会被放行
- **FE-3 收藏 / 历史 / 评分管理**:现在有论文来源了(检索结果),不必再做临时录入入口
- **Semantic Scholar 的 429**:实测匿名共享池持续限流,降级链兜到了 Crossref,
  但 Crossref 的元数据明显更差(preprint 多、摘要缺失率高)。已加上 `SEMANTIC_SCHOLAR_API_KEY`
  支持,申请到 key 就能走独立配额并对比两源质量

**已知取舍(不是遗漏)**:

1. Agent 只做"拆解一次 → 搜一次",没有多轮检索(先搜、看结果、再补搜)。留待评估必要性。
2. 检索没有缓存。同一个词重复搜会重复调大模型与外部接口 —— 但论文落库是幂等的,
   所以代价只是那 10 秒。等有性能诉求再加。
3. 检索质量还没有量化。目前只验证了"拆解结果对、链路通",没有测召回质量 ——
   这要等 REQ-006 用 Recall@K 来量。

# 2026-09-29(第四次)

## 本次目标

完成 REQ-002 的最后一块 S4:前端检索页。用户明确要求**把 Agent 的拆解理由显示出来**。

## 完成内容

- **检索页** `SearchView.vue`:一句话输入框 + 例子引导 + 结果列表 + 收藏按钮
- **显示 Agent 的理解**:检索词(等宽体,词边界一眼可辨)、时间范围、拆解理由、命中的数据源
- **检索中的等待提示**:一次检索实测约 10 秒,不能只让按钮转圈 —— 明确写出"正在拆解查询并检索文献,约需 10 秒"
- **收藏入口**:进入页面时批量取一次收藏状态,按钮据此显示"收藏 / 已收藏";取不到状态不影响检索
- **查询同步到地址栏**:刷新不丢结果,链接也能直接分享
- 路由加 `/search`,顶栏导航自动多出「检索」(导航项由路由表推导)
- 测试从 43 条增至 **56 条**

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `frontend/src/views/SearchView.vue` | 新增:检索页 |
| `frontend/src/views/SearchView.spec.js` | 新增:12 条挂载测试 |
| `frontend/src/api/papers.js`、`api/library.js` | 新增:检索、收藏相关接口 |
| `frontend/src/router/index.js` | 加 `/search` 路由 |
| `frontend/src/layouts/AppLayout.spec.js` | 重构:导航断言改为从路由表推导 |
| `scratch/search_smoke.py` | 修正幂等断言(见问题一) |

## 遇到问题

### 问题一(真问题,但是别人的):同一篇论文在两个数据源下各存了一行

冒烟测试里我断言"重复检索返回的本地 id 完全一致",失败。查库后发现不是因为重复入库:

```
id=4  CROSSREF          10.1016/j.eswa.2025.128378  Multiview graph dual-attention…
id=6  SEMANTIC_SCHOLAR  061d1eff03ce6cc48147eccd…   Multiview graph dual-attention…
```

**同一篇论文,两个来源各一行** —— 这是 F6 的既定设计(唯一约束是 `(来源, 外部 ID)`,
理由是不同来源的 ID 体系不同、可能撞号)。这次 S2 恰好恢复了,同一篇论文就分别从两个源进来了。

**我的断言写错了**,而不是代码错了:外部检索接口每次返回的集合与顺序都可能变,
"两次结果完全相同"根本不是该保证的性质。真正该验的是幂等 ——
同一篇论文(由来源 + 外部 ID 唯一确定)只有一个本地 id。已改成按 `(source, externalId)` 比对。

**但这暴露了一个真实的产品问题(已记入下一步)**:从用户视角看,同一篇论文会因为
命中不同数据源而变成两个不同的条目。后果是收藏 / 评分 / 阅读历史不跨源统一 ——
今天收藏了 Crossref 那一行,明天 S2 恢复后再搜到同一篇,S2 那一行显示的是"未收藏"。
S2 可用性在实测中确实时好时坏,所以这不是理论问题。

### 问题二(自查):脆弱测试 —— 断言写死的清单

加了「检索」导航项后,`AppLayout` 的三条测试全红,因为它把导航项写死成
`['首页', '兴趣标签']`。而导航项本来就来自路由表(`navItems`),写死等于把同一份知识
维护了两遍,配置一变测试就红 —— 这不是测试该有的样子。

改成从真实的 `navItems` 推导期望值,并断言"有且只有一个导航项处于高亮态"。
以后再加页面,这个测试不用改。

### 问题三(测试自身的错):mock 掉了默认参数

两条测试断言 `searchPapers` 被调用时带了 `limit = 5`,失败。原因是视图调用时只传了一个参数,
`limit = 5` 是 api 模块里的**默认参数**,而 mock 替换掉了整个模块,默认值不会被应用。
断言应当只覆盖视图的职责(传了什么),默认值是 api 模块自己的事。

## 测试结果

### 前端(56 条)

```
cd frontend && npm test
Test Files  7 passed (7)
Tests  56 passed (56)
```

新增 12 条检索页挂载测试:例子引导、过短输入不发请求、**显示 Agent 拆解**、
结果列表(标题 / 作者 / 期刊 / 年份 / 来源、作者缺失时的文案)、收藏与取消收藏、
进入时回显已收藏、取不到收藏状态不影响检索、无结果的可操作提示、
失败时清掉上次结果、地址栏带 `q` 自动检索、检索后写回地址栏。

### 端到端(20 项,经 Vite 代理)

```
python scratch/search_smoke.py http://localhost:5173
共 20 项,通过 20,失败 0
```

链路:浏览器 → Vite 代理 → Java 后端 → Python ai-service → DeepSeek + 外部检索。

### 构建

```
npm run build → ✓ built in 408ms,无告警
```

## 下一步计划

- **跨数据源的同篇论文去重(建议优先)**:目前 `(来源, 外部 ID)` 是唯一键,
  同一篇论文命中两个源就是两条记录,收藏 / 评分 / 阅读历史因此不跨源统一。
  可行的方向是把 DOI 提出来做跨源身份(DOI 存在时按 DOI upsert,不存在再退回 `(来源, 外部 ID)`),
  需要给 `paper` 加一列 + 唯一索引,并调整 `PaperService.resolve`。**这是数据模型改动,要先定方案再动。**
- **FE-3 收藏 / 历史 / 评分管理页**:检索已经能产出论文,不必再做临时录入入口
- **`GET /api/interests` 的鉴权缺口**:它同样不回查用户(第二次记录)
- 用户已申请 Semantic Scholar API key,通过后填进 `ai-service/.env` 即可提高结果质量

**已知取舍(不是遗漏)**:

1. 检索页只做了收藏,没有评分与"标记已读" —— 那些属于 FE-3。
2. 检索结果不分页。`limit` 上限 20,目前够用;真要翻页得先想清楚"再搜一次"和"翻页"的区别。

# 2026-09-29(第五次)

## 本次目标

修「同一篇论文因命中不同数据源而被存成两行」的问题,做法是引入 DOI 作为跨源身份。

## 完成内容

- `paper` 表加 `doi` 列 + 唯一索引(可空,MySQL 允许多行 NULL,所以没有 DOI 的论文互不冲突)
- `PaperService.resolve` 的查找顺序改为:**有 DOI 优先按 DOI 查**,查不到再退回 `(来源, 外部 ID)`
- DOI 规范化(`Doi.normalize` / `normalize_doi`):小写、剥 `https://doi.org/`、`doi:` 前缀、
  只接受 `10.xxx/yyy` 的形状 —— **形状不对就返回 null 而不是原样返回**,
  兜底一个假身份会把两篇不同的论文合并成一篇,那比多存一行糟糕得多
- S2 侧改为请求 `externalIds` 字段并从中取 DOI(DOI 不在顶层,取错地方这个功能就悄悄失效)
- 把「条目 → 论文」的映射从检索函数里抽出来(`semantic_scholar_to_paper` / `crossref_to_paper`),
  原来它藏在 HTTP 调用里没法单测,而 DOI 从哪来正是最容易错的地方
- `applyMetadata` 会把后拿到的 DOI 补到已有行上,之后就能跨源认人

## 修改文件

| 文件 | 修改 |
| ---- | ---- |
| `backend/.../paper/Doi.java` | 新增:DOI 规范化 |
| `backend/.../paper/Paper.java` | 加 `doi` 字段 + 唯一约束 + 回填 |
| `backend/.../paper/PaperRepository.java` | 加 `findByDoi` |
| `backend/.../paper/PaperService.java` | 查找顺序:DOI 优先 |
| `backend/.../paper/dto/{PaperInput,PaperResponse}.java`、`search/dto/AiSearchResponse.java` | 契约加 `doi` |
| `ai-service/app/sources.py` | S2 取 `externalIds.DOI`;抽出两个映射函数;`normalize_doi` |
| `ai-service/app/schemas.py` | Paper 加 `doi` |
| `backend/.../paper/DoiApiIntegrationTest.java` | 新增:8 条跨源身份测试 |
| `backend/.../search/SearchApiIntegrationTest.java` | 加 1 条"换源后复用同一行"的检索路径用例 |
| `ai-service/tests/test_sources.py` | 加 14 条(规范化 + 两个映射函数) |
| `scratch/search_smoke.py` | 断言改为按不变量,而非"两次结果有重叠" |

## 遇到问题

### 问题一(重要,方案本身的局限):arXiv 预印本与正式发表版是两个 DOI

修完之后实测,发现原来那对重复(同一个标题在 Crossref 与 S2 下各一行)**并没有被合并**:

```
id=4  CROSSREF          10.1016/j.eswa.2025.128378   Multiview graph dual-attention…
id=6  SEMANTIC_SCHOLAR  10.48550/arxiv.2502.19271    Multiview graph dual-attention…
```

DOI 不一样:Crossref 给的是**期刊 DOI**,S2 给的是 **arXiv 预印本的 DOI**
(`10.48550` 是 arXiv 的 DOI 前缀)。它们是**两条不同的记录** —— 预印本和正式发表版,
只是标题相同。

**所以本次的修复没有解决这个具体症状。** 它解决的是另一种情形:两个源引用的是**同一条**
已发表记录(DOI 相同)时,现在会正确合并。这种情况同样常见,而且它是任何更聪明匹配的前提。

**我在出方案时应该先查一下 S2 到底给出什么 DOI**,而不是假定"sources 都会给同一个 DOI"。
当时库里还没有 doi 列,但直接看一次 S2 的原始响应就能发现 —— 这是我漏掉的一步。

**要不要进一步合并预印本与正式版?** 那需要按标题+年份+作者做模糊匹配,
而**同名论文真实存在**,错误合并会静默地把两篇不同的论文变成一篇 ——
这是不可逆的数据损坏,比多存一行严重得多。建议当成一个独立的、需要单独评估的方案,不要在检索里顺手做。

### 问题二(测试设计):断言"两次结果有重叠"是靠不住的

冒烟测试原来断言"重复检索返回同样的 id"。实测发现:同一句话连搜三次,
第一次走 Crossref、第二三次走 Semantic Scholar,**两次结果可以完全不重叠** ——
降级链换源时,论文集合整批都不同。而且即便不换源,外部接口每次返回的集合与顺序也可能变。

改成断言**不变量**:同一个 `(来源, 外部 ID)` 不会对应两个本地 id、同一个 DOI 不会对应两个本地 id。
重叠篇数只作为信息打印出来,不再作为通过条件。**测试应该断言保证成立的性质,而不是碰巧成立的现象。**

### 问题三(环境):后端重启失败,端口被残留进程占着

`TaskStop` 只结束了 Maven 外壳,**Java 子进程继续占着 8081**,新实例启动即失败
(这也是之前记录过的 Windows 坑)。用 `scripts/kill-port.ps1` 清掉后正常。
注意:此时旧进程还在服务,冒烟测试照样能跑通 —— **如果不看启动日志,会误以为新代码已生效**。

## 测试结果

### Python(43 条,新增 14)

覆盖 DOI 规范化(大小写、各种前缀、形状不对返回 null)、两个来源的条目映射
(S2 的 DOI 从 `externalIds` 取、Crossref 的 DOI 同时是身份)、缺字段时的兜底。

### 后端(88 条,新增 9)

```
mvn test
Tests run: 88, Failures: 0, Errors: 0
```

F6 的 25 条、检索的 13 条全部无回归。新增:

- **跨源合并**:同一 DOI 从两个来源进来只存一行、元数据互补而不是互相覆盖
- **规范化**:`https://doi.org/10.1000/ABC` 与 `10.1000/abc` 认成同一篇
- **边界**:没有 DOI 时行为完全不变;不同来源的同名论文**不会**被错误合并(宁可多存一行);
  形状不对的 DOI 当作没有
- **回填**:已有行后来拿到 DOI 会补上,之后即可跨源认人
- **检索路径**:第一次走 Crossref、第二次走 S2,同一 DOI 复用同一行

### 端到端(21 项)

```
python scratch/search_smoke.py
共 21 项,通过 21,失败 0
```

数据库确认 `doi` 列已建、已有行被回填、没有同一身份对应两个 id。

## 下一步计划

- **预印本 / 正式版的重复**:需要单独方案(模糊匹配 + 明确的合并策略),风险高,不建议顺手做
- **FE-3 收藏 / 历史 / 评分管理页**
- `GET /api/interests` 的鉴权缺口(仍未修)
- 开发库里还留着十几个测试账号与论文,需要时清一次
