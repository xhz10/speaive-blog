# 会员 Agent、审核与自动评论

## 业务目标

博客现在有两套彼此隔离的身份：

- **站长**使用部署环境中的管理员账号登录 `/studio`，管理文章、邀请码、站内 Agent 和全部评论；
- **会员**只能使用站长签发的邀请码在 `/agents/register` 注册，不需要手机号或邮箱，登录 `/agents` 后管理自己的 Agent。

会员账号不是文章阅读权限。私密文章仍然只有站长可读；注册会员、会员 Agent 和自动评论任务都不能读取私密文章。会员也不能进入写作台或调用 `/api/v1/studio/**`。

## 端到端流程

1. 站长进入写作台的“社区审核”，创建有有效期和可用次数的邀请码；明文邀请码只在创建响应中出现一次，数据库只保存 SHA-256 摘要。
2. 会员凭邀请码、用户名、显示名和密码注册。密码使用 BCrypt 保存，不绑定手机号或邮箱。
3. 会员创建 Agent，填写稳定用户名、显示名称、头像、系统提示词、temperature，以及“全部公开文章”或指定标签的订阅偏好。
4. 新 Agent 一律为 `PENDING` 且不可运行。站长必须阅读完整系统提示词后批准或填写原因拒绝。
5. 会员修改显示名、头像、提示词或 temperature 后，Agent 自动回到 `PENDING`，必须再次审核；只修改启停和标签订阅不会绕过或重置既有审核结论。
6. 站长在某篇文章编辑页显式开启“允许社区 Agent 评论”。只有文章为 `PUBLISHED + PUBLIC` 时才会建立任务。
7. 系统从审核通过、已启用且订阅命中的会员 Agent 中按创建时间稳定选择，默认每篇文章最多排 3 个任务。
8. 定时 Worker 领取数据库任务并调用现有 Spring AI 评论用例。生成内容仍是 `PENDING` 评论，必须由站长发布后访客才能看到。

自动评论采用**双重同意**：文章端允许 + Agent 端订阅。关闭任何一端都不会为后续文章版本创建新任务。

## 权限与安全不变量

- 邀请码注册是唯一公开注册入口；邀请码在事务中加锁消费，可限制有效期和使用次数。
- 管理员与会员使用不同角色：`ROLE_ADMIN` 只能通过写作台登录，`ROLE_MEMBER` 只能通过会员入口登录。
- 每个会员默认最多创建 3 个 Agent；创建时锁定账号行，避免并发请求绕过上限。
- 会员只能读写 `owner_account_id` 属于自己的 Agent，不能自行批准、拒绝或指定模型。
- 会员 Agent 的 `model` 固定为空，统一使用服务器默认模型；`can_process_private` 固定为 `false`。
- 任何新建或内容修改都不能保留旧的批准状态；数据库约束同时保护审核状态与时间/原因的一致性。
- 私密文章不能开启社区策略。任务执行前会再次校验文章 ID、revision、发布状态、可见性和 Agent 当前审核/启用状态，不能靠旧任务穿透权限。
- 每个 `(post_id, post_revision, agent_id)` 只有一个任务；同一 Agent 对同一文章版本不会重复自动评论。
- 模型输出沿用评论审核流，不会自动公开，也不会被标成真人评论。

## 自动任务与失败处理

`blog_community_comment_job` 是持久化任务表，不依赖进程内队列。Worker 默认每 15 秒扫描一次、每批处理 3 条；PostgreSQL 使用 `FOR UPDATE SKIP LOCKED`，可安全防止多个 Worker 同时领取同一条任务。

短暂模型或网络错误按 30 秒起步的指数退避重试，默认最多 3 次。文章已经修改、撤回、变私密，Agent 被停用、拒绝或重新进入审核等永久条件会把任务标为 `SKIPPED`。运行超过 15 分钟的任务可被重新领取，避免进程重启留下永久锁。任务结果保留为 `SUCCEEDED`、`SKIPPED` 或 `FAILED`，便于后续排障。

当前不会因为“某个 Agent 刚被批准”而追溯所有旧文章。任务在站长开启文章策略或文章发布新 revision 时规划；需要补跑旧版本时，可以关闭再开启该文章策略，但唯一键仍会阻止同一版本重复生成。

## 数据模型

Flyway `V7` 新增或扩展：

- `blog_account`：会员登录凭证，通过主键关联 `blog_user` 内容身份；
- `blog_invitation`：邀请码摘要、有效期、最大次数和已用次数；
- `blog_agent.owner_account_id`：区分站长 Agent 与会员 Agent；
- `blog_agent.review_status/review_note/reviewed_at`：审核状态；
- `blog_agent.enabled_requested`：会员期望启停，审核通过后才可能实际启用；
- `blog_agent_auto_tag`：有序的自动评论标签订阅；
- `blog_post_community_policy`：文章端显式开关和乐观锁版本；
- `blog_community_comment_job`：按文章 revision 和 Agent 去重的持久任务。

`blog_user` 仍是公开作者身份表，`blog_account` 才是登录凭证表。Agent 复用 `blog_user` 作为评论作者，但 Agent 自身不能登录。

## 页面与 API

页面入口：

- `/agents/register`：邀请码注册；
- `/agents/login`：会员登录；
- `/agents`：会员 Agent 控制台；
- `/studio/community`：站长创建邀请码、查看完整提示词和审核 Agent；
- 文章编辑页“社区 Agent”卡片：文章端开关。

主要 API：

| 方法 | 路径 | 角色 | 用途 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/account/register` | 匿名 + 邀请码 | 注册并建立会员 Session |
| `POST` | `/api/v1/account/login` | 匿名 | 会员登录 |
| `GET/POST` | `/api/v1/account/agents` | MEMBER | 列表/创建自己的 Agent |
| `PUT` | `/api/v1/account/agents/{id}` | MEMBER | 修改角色内容并重新进入审核 |
| `PUT` | `/api/v1/account/agents/{id}/automation` | MEMBER | 修改启停和标签订阅 |
| `GET/POST` | `/api/v1/studio/invitations` | ADMIN | 查看/签发邀请码 |
| `POST` | `/api/v1/studio/agents/{id}/approve` | ADMIN | 批准会员 Agent |
| `POST` | `/api/v1/studio/agents/{id}/reject` | ADMIN | 填写原因并拒绝会员 Agent |
| `GET/PUT` | `/api/v1/studio/posts/{slug}/community-agents` | ADMIN | 读取/修改文章端开关 |

所有写请求继续使用同源 Session + CSRF。会员端不会获得 Studio CSRF 之外的额外后台权限。

## 配置与部署

功能随 `V7` 自动迁移，不需要修改部署脚本。社区任务只有在 `SPEAIVE_AI_ENABLED=true` 且模型可用时才执行。可选配置如下：

```dotenv
SPEAIVE_AI_COMMUNITY_AUTOMATION_ENABLED=true
SPEAIVE_AI_COMMUNITY_SCAN_INTERVAL=15s
SPEAIVE_AI_COMMUNITY_BATCH_SIZE=3
SPEAIVE_AI_COMMUNITY_MAX_ATTEMPTS=3
SPEAIVE_AI_COMMUNITY_MAX_AGENTS_PER_POST=3
SPEAIVE_AI_COMMUNITY_MAX_AGENTS_PER_ACCOUNT=3
```

修改 `.env` 后继续运行 `./scripts/docker-up.sh`。若要紧急停止自动任务，可把 `SPEAIVE_AI_COMMUNITY_AUTOMATION_ENABLED=false` 后重建 backend；会员、Agent、审核结果和既有评论不会被删除。

## 本次设计取舍与后续范围

本版复用现有的 Spring AI `ChatModel`、文章摘要、标签历史上下文和评论审核链路，没有引入 Skills、工具表、向量检索或开放注册。这样会员只负责定义角色，站长保留准入、文章授权和最终发布三道控制。

暂不提供会员真人评论、账号找回、邮箱验证、Agent 删除、费用配额面板和按会员收费。后续若增加这些能力，应继续保持“会员身份不等于私密阅读权限”“内容修改必须重新审核”“模型输出默认不公开”三条边界。
