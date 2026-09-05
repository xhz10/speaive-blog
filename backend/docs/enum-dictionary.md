# 枚举中文速查

当前覆盖生产源码中的 **39 个枚举、140 个枚举项**，包括数据库 PO 枚举和两个类内部的私有枚举。代码中也有相同的中文 Javadoc，可直接在 IDE 中悬停查看。

枚举名保持英文是为了兼容 Java、JSON 与数据库；中文说明解释业务含义。名称相同的状态可能属于不同流程，例如 Agent 审核的 `PENDING`、评论审核的 `PENDING`、自动任务的 `PENDING` 不能混用。

## 分层索引

| 所属层 | 枚举 |
| --- | --- |
| `domain` | [DeviceType](#devicetype)、[AgentReviewStatus](#agentreviewstatus)、[AgentRunStatus](#agentrunstatus)、[AuthorStatus](#authorstatus)、[AuthorType](#authortype)、[CommunityCommentJobStatus](#communitycommentjobstatus)、[CommentStatus](#commentstatus)、[CreativeContentType](#creativecontenttype)、[CreativeVisibility](#creativevisibility)、[InspirationKind](#inspirationkind)、[InspirationStatus](#inspirationstatus)、[DomainErrorCode](#domainerrorcode)、[NovelFragmentRevisionEventType](#novelfragmentrevisioneventtype)、[NovelFragmentStatus](#novelfragmentstatus)、[NovelFragmentVisibility](#novelfragmentvisibility)、[PostRevisionEventType](#postrevisioneventtype)、[PostStatus](#poststatus)、[PostVisibility](#postvisibility) |
| `application` | [BlogErrorCode](#blogerrorcode)、[MediaReadScope](#mediareadscope)、[CommentQueryScope](#commentqueryscope)、[NovelFragmentQueryScope](#novelfragmentqueryscope)、[PostQueryScope](#postqueryscope)、[MarkdownImportOutcome](#markdownimportoutcome)、[Transition](#transition) |
| `infrastructure` | [ImageKind](#imagekind)、[AgentReviewStatusPo](#agentreviewstatuspo)、[AgentRunStatusPo](#agentrunstatuspo)、[AuthorStatusPo](#authorstatuspo)、[AuthorTypePo](#authortypepo)、[CommentStatusPo](#commentstatuspo)、[CommunityCommentJobStatusPo](#communitycommentjobstatuspo)、[NovelFragmentRevisionEventTypePo](#novelfragmentrevisioneventtypepo)、[NovelFragmentStatusPo](#novelfragmentstatuspo)、[NovelFragmentVisibilityPo](#novelfragmentvisibilitypo)、[PostStatusPo](#poststatuspo)、[PostVisibilityPo](#postvisibilitypo)、[RevisionEventTypePo](#revisioneventtypepo) |
| `start` | [ApiErrorCode](#apierrorcode) |

## BlogErrorCode

应用用例对外报告的错误分类；HTTP 层将其转换为状态码和统一错误体。

[打开源码](../speaive-blog-application/src/main/java/com/speaive/blog/application/error/BlogErrorCode.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `INVALID_REQUEST` | 请求不符合用例或领域规则，通常映射为 HTTP 400。 |
| `INVALID_FILE_NAME` | 上传或导入文件名不合法，映射为 HTTP 400。 |
| `INVALID_MARKDOWN` | Markdown 内容或元数据不能解析，映射为 HTTP 400。 |
| `INVALID_IMAGE` | 图片格式或内容校验失败，映射为 HTTP 400。 |
| `PATH_OUTSIDE_DATA_DIR` | 请求路径越出允许的数据目录，映射为 HTTP 400。 |
| `TOO_LARGE` | 内容或文件超过大小限制，映射为 HTTP 413。 |
| `NOT_FOUND` | 目标不存在或对当前读取范围不可见，映射为 HTTP 404。 |
| `SLUG_CONFLICT` | 内容 slug 已被活动记录占用，映射为 HTTP 409。 |
| `VERSION_CONFLICT` | 版本令牌过期或 CAS 未命中，映射为 HTTP 409。 |
| `GENERATION_CONFLICT` | 相同文章版本、角色与回复目标的生成占位冲突，映射为 HTTP 409。 |
| `AI_UNAVAILABLE` | AI 服务未启用或未配置，映射为 HTTP 503。 |
| `AI_GENERATION_FAILED` | 模型调用失败，映射为 HTTP 502。 |
| `STORAGE_ERROR` | 数据库或媒体存储无法完成操作，映射为 HTTP 500。 |

## MediaReadScope

媒体读取范围，由用例传递给存储适配器执行引用可见性检查。

[打开源码](../speaive-blog-application/src/main/java/com/speaive/blog/application/port/out/media/MediaReadScope.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PUBLIC` | 访客读取，只允许被当前公开文章引用的受管理媒体。 |
| `STUDIO` | 管理员读取，允许访问工作区中的受管理媒体。 |

## CommentQueryScope

评论仓储的读取范围；文章是否可读由外层用例先判断。

[打开源码](../speaive-blog-application/src/main/java/com/speaive/blog/application/port/out/persistence/CommentQueryScope.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `STUDIO` | 审核工作区范围，包含待审核、已公开和已隐藏评论。 |
| `PUBLISHED` | 公开评论范围，只返回 PUBLISHED 评论，不替代文章权限检查。 |

## NovelFragmentQueryScope

小说片段仓储的读取范围，由用例指定；它不是用户权限，调用前仍需完成身份校验。

[打开源码](../speaive-blog-application/src/main/java/com/speaive/blog/application/port/out/persistence/NovelFragmentQueryScope.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `STUDIO` | 站长工作区范围，包含草稿与私密小说片段。 |
| `PUBLISHED` | 公共阅读范围，只返回已发布且可见性为 PUBLIC 的小说片段。 |

## PostQueryScope

文章仓储的读取范围，由用例指定；它不是用户权限，调用前仍需完成身份校验。

[打开源码](../speaive-blog-application/src/main/java/com/speaive/blog/application/port/out/persistence/PostQueryScope.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `STUDIO` | 站长工作区范围，包含草稿与私密文章。 |
| `PUBLISHED` | 公共阅读范围，只返回已发布且可见性为 PUBLIC 的文章。 |

## MarkdownImportOutcome

Markdown 投递箱的一次导入结果，用于区分新写入与按内容哈希幂等命中。

[打开源码](../speaive-blog-application/src/main/java/com/speaive/blog/application/result/importing/MarkdownImportOutcome.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `IMPORTED` | 已导入：本次创建了草稿并记录导入台账。 |
| `ALREADY_IMPORTED` | 已处理过：相同内容哈希已有记录，本次不重复创建文章。 |

## Transition

文章应用服务内部的操作选择器；代表要执行的命令，不是持久化状态。

[打开源码](../speaive-blog-application/src/main/java/com/speaive/blog/application/service/PostApplicationService.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PUBLISH` | 调用文章聚合的发布行为。 |
| `UNPUBLISH` | 调用文章聚合的撤回为草稿行为。 |

## AgentReviewStatus

Agent 配置的审核结果；会员角色修改提示词等配置后需要重新审核。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/agent/AgentReviewStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PENDING` | 待审核：会员已提交，当前不能生成内容。 |
| `APPROVED` | 审核通过：仍需身份启用并满足文章权限才能运行；站长自建角色直接使用此状态。 |
| `REJECTED` | 审核拒绝：保存拒绝说明，角色不可运行，可修改后重新提交。 |

## AgentRunStatus

一次 AI 评论或回复调用的审计状态；成功生成不代表评论已经公开。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/agent/AgentRunStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `RUNNING` | 生成中：已经登记本次文章版本、Agent 和回复目标，等待模型返回。 |
| `SUCCEEDED` | 生成成功：已保存待审核评论，同时记录模型与可用的 token 用量。 |
| `FAILED` | 生成失败或超时占位已清理；保存错误摘要供排查。 |

## AuthorStatus

内容身份是否启用；禁用不会删除历史署名。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/author/AuthorStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `ACTIVE` | 启用身份；是否可以写作还需结合 AuthorType 判断。 |
| `DISABLED` | 停用身份；不再允许以该身份创建新内容。 |

## AuthorType

内容署名的身份类型；它与登录凭证及 HTTP 授权角色是不同概念。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/author/AuthorType.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `HUMAN` | 真人作者身份，可对应站长或会员；不表示必然拥有管理员权限。 |
| `AGENT` | AI 角色的署名身份，由 AgentProfile 管理提示词与运行资格。 |
| `SYSTEM` | 系统身份，保留给系统行为；具体业务入口另外决定是否允许该类型。 |

## CommunityCommentJobStatus

社区 Agent 自动评论队列状态；与单次模型调用的 AgentRunStatus 分开记录。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/automation/CommunityCommentJobStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PENDING` | 等待执行：尚未领取，或失败后等待 availableAt 到达再重试。 |
| `RUNNING` | 已领取执行中：领取过程递增尝试次数，并记录 claimedAt。 |
| `SUCCEEDED` | 任务完成：生成结果已进入待审核，不表示已发布。 |
| `SKIPPED` | 无需继续：目标不存在、版本变化、请求不符合条件或重复生成等原因使本次任务失效。 |
| `FAILED` | 最终失败：达到重试上限，保存最后一次错误说明。 |

## CommentStatus

评论与回复的审核状态；新生成内容统一先待审核。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/comment/CommentStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PENDING` | 待审核：站长可见，访客不可见；允许其他角色继续生成待审核回复。 |
| `PUBLISHED` | 已公开：回复还要求父评论已公开，文章本身也必须允许公开读取。 |
| `HIDDEN` | 已隐藏：不能继续回复或直接重新发布；隐藏用例会连同后续回复一起处理。 |

## CreativeContentType

跨类型创作工具中的内容类型，用于历史版本、作品集和分享定位。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/creative/CreativeContentType.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `POST` | 普通博客文章，公开阅读路径为 /blog/{slug}/。 |
| `NOVEL` | 小说片段，公开阅读路径为 /novels/{slug}/。 |

## CreativeVisibility

作品集的可见范围；公开作品集也只展示其中当前已公开的内容。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/creative/CreativeVisibility.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PUBLIC` | 公开作品集；私密或未发布的条目会被过滤，没有可见条目的作品集不会公开展示。 |
| `ADMIN_ONLY` | 私密作品集，仅在站长工作区读取。 |

## InspirationKind

灵感的内容分类，只描述记录的性质，不决定处理进度。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/creative/InspirationKind.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `IDEA` | 想法：待展开的主题、观点或构思。 |
| `SCENE` | 场景：环境、动作或事件片段。 |
| `DIALOGUE` | 对白：人物说话的句子或对话片段。 |
| `CHARACTER` | 人物：性格、关系或角色设定。 |
| `QUESTION` | 问题：需要继续追问或求证的疑问。 |

## InspirationStatus

灵感的处理进度；只有转化完成时才保留目标文章或小说片段的关联。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/creative/InspirationStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `INBOX` | 收件箱：刚记录，尚未进一步处理。 |
| `DEVELOPING` | 酝酿中：正在发展成更完整的内容。 |
| `CONVERTED` | 已转化：必须关联目标内容类型和 slug。 |
| `ARCHIVED` | 已归档：暂时不继续处理，记录仍保留。 |

## DomainErrorCode

领域对象拒绝违反业务规则的操作时使用的错误分类；不依赖 HTTP 状态码。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/error/DomainErrorCode.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `INVALID_SLUG` | 内容路径标识不合法。 |
| `INVALID_CONTENT` | 标题、正文、标签或摘要等内容不符合业务限制。 |
| `INVALID_AUTHOR` | 作者身份缺失、停用或不允许作为内容作者。 |
| `INVALID_ACCOUNT` | 会员或邀请信息不符合业务规则。 |
| `INVALID_AGENT` | 角色配置、归属、审核或运行资格不符合规则。 |
| `INVALID_COMMENT` | 评论正文、回复关系或评论状态不合法。 |
| `VERSION_CONFLICT` | 操作持有旧版本，不能覆盖较新的业务状态。 |
| `INVALID_STATE` | 其他状态组合或状态转换不符合不变量。 |

## NovelFragmentRevisionEventType

小说片段修订的操作原因；当前没有文章那样的归档操作。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/novel/NovelFragmentRevisionEventType.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `CREATE` | 通过写作台创建初始草稿快照。 |
| `UPDATE` | 修改内容或可见范围，保留原有发布状态。 |
| `PUBLISH` | 发布内容；可见范围仍由 visibility 决定。 |
| `UNPUBLISH` | 撤回为草稿，保留内容与历史。 |
| `RESTORE` | 把历史内容恢复成一个新修订，不回退版本号，也不自动改变当前发布状态。 |

## NovelFragmentStatus

小说片段的发布状态；与可见范围是两个独立维度，已发布不自动等于公开。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/novel/NovelFragmentStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `DRAFT` | 草稿：小说片段尚未发布，访客不可读取。 |
| `PUBLISHED` | 已发布：还需同时满足 PUBLIC 可见性，访客才能读取小说片段。 |

## NovelFragmentVisibility

小说片段的可见范围；控制谁可以读取，不代替发布状态。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/novel/NovelFragmentVisibility.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PUBLIC` | 公开范围：小说片段满足对应发布规则后可供访客读取。 |
| `ADMIN_ONLY` | 仅管理员：小说片段及受保护的关联内容只供站长查看，会员身份不等于管理员。 |

## PostRevisionEventType

文章修订的操作原因，用于历史快照审计；它不是文章状态，也不是消息总线上的领域事件。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/post/PostRevisionEventType.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `CREATE` | 通过写作台创建初始草稿快照。 |
| `IMPORT` | 从 Markdown 导入文章并记录初始快照。 |
| `UPDATE` | 修改内容或可见范围，保留原有发布状态。 |
| `PUBLISH` | 发布内容；可见范围仍由 visibility 决定。 |
| `UNPUBLISH` | 撤回为草稿，保留内容与历史。 |
| `ARCHIVE` | 记录归档快照并删除活动文章；历史保留，原 slug 可以重用。 |
| `RESTORE` | 把历史内容恢复成一个新修订，不回退版本号，也不自动改变当前发布状态。 |

## PostStatus

文章的发布状态；与可见范围是两个独立维度，已发布不自动等于公开。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/post/PostStatus.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `DRAFT` | 草稿：文章尚未发布，访客不可读取。 |
| `PUBLISHED` | 已发布：还需同时满足 PUBLIC 可见性，访客才能读取文章。 |

## PostVisibility

文章的可见范围；控制谁可以读取，不代替发布状态。

[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/post/PostVisibility.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PUBLIC` | 公开范围：文章满足对应发布规则后可供访客读取。 |
| `ADMIN_ONLY` | 仅管理员：文章及受保护的关联内容只供站长查看，会员身份不等于管理员。 |

## ImageKind

媒体适配器支持的图片编码类型；同时校验扩展名、MIME 和文件头，不能只相信上传名称。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/media/MediaFileStore.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `JPEG` | JPEG 图片，接受 .jpg 与 .jpeg，存储扩展名统一为 .jpg。 |
| `PNG` | PNG 图片，校验 PNG 文件头。 |
| `GIF` | GIF 图片，接受 GIF87a 与 GIF89a 文件头。 |
| `WEBP` | WebP 图片，校验 RIFF 容器与 WEBP 标记。 |
| `AVIF` | AVIF 图片，校验 ftyp 容器与 avif 或 avis 品牌标记。 |

## AgentReviewStatusPo

AgentReviewStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/AgentReviewStatusPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PENDING` | 待审核：会员已提交，当前不能生成内容。 |
| `APPROVED` | 审核通过：仍需身份启用并满足文章权限才能运行；站长自建角色直接使用此状态。 |
| `REJECTED` | 审核拒绝：保存拒绝说明，角色不可运行，可修改后重新提交。 |

## AgentRunStatusPo

AgentRunStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/AgentRunStatusPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `RUNNING` | 生成中：已经登记本次文章版本、Agent 和回复目标，等待模型返回。 |
| `SUCCEEDED` | 生成成功：已保存待审核评论，同时记录模型与可用的 token 用量。 |
| `FAILED` | 生成失败或超时占位已清理；保存错误摘要供排查。 |

## AuthorStatusPo

AuthorStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/AuthorStatusPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `ACTIVE` | 启用身份；是否可以写作还需结合 AuthorType 判断。 |
| `DISABLED` | 停用身份；不再允许以该身份创建新内容。 |

## AuthorTypePo

AuthorType 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/AuthorTypePo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `HUMAN` | 真人作者身份，可对应站长或会员；不表示必然拥有管理员权限。 |
| `AGENT` | AI 角色的署名身份，由 AgentProfile 管理提示词与运行资格。 |
| `SYSTEM` | 系统身份，保留给系统行为；具体业务入口另外决定是否允许该类型。 |

## CommentStatusPo

CommentStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/CommentStatusPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PENDING` | 待审核：站长可见，访客不可见；允许其他角色继续生成待审核回复。 |
| `PUBLISHED` | 已公开：回复还要求父评论已公开，文章本身也必须允许公开读取。 |
| `HIDDEN` | 已隐藏：不能继续回复或直接重新发布；隐藏用例会连同后续回复一起处理。 |

## CommunityCommentJobStatusPo

CommunityCommentJobStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/CommunityCommentJobStatusPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PENDING` | 等待执行：尚未领取，或失败后等待 availableAt 到达再重试。 |
| `RUNNING` | 已领取执行中：领取过程递增尝试次数，并记录 claimedAt。 |
| `SUCCEEDED` | 任务完成：生成结果已进入待审核，不表示已发布。 |
| `SKIPPED` | 无需继续：目标不存在、版本变化、请求不符合条件或重复生成等原因使本次任务失效。 |
| `FAILED` | 最终失败：达到重试上限，保存最后一次错误说明。 |

## NovelFragmentRevisionEventTypePo

NovelFragmentRevisionEventType 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/NovelFragmentRevisionEventTypePo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `CREATE` | 通过写作台创建初始草稿快照。 |
| `UPDATE` | 修改内容或可见范围，保留原有发布状态。 |
| `PUBLISH` | 发布内容；可见范围仍由 visibility 决定。 |
| `UNPUBLISH` | 撤回为草稿，保留内容与历史。 |
| `RESTORE` | 把历史内容恢复成一个新修订，不回退版本号，也不自动改变当前发布状态。 |

## NovelFragmentStatusPo

NovelFragmentStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/NovelFragmentStatusPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `DRAFT` | 草稿：小说片段尚未发布，访客不可读取。 |
| `PUBLISHED` | 已发布：还需同时满足 PUBLIC 可见性，访客才能读取小说片段。 |

## NovelFragmentVisibilityPo

NovelFragmentVisibility 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/NovelFragmentVisibilityPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PUBLIC` | 公开范围：小说片段满足对应发布规则后可供访客读取。 |
| `ADMIN_ONLY` | 仅管理员：小说片段及受保护的关联内容只供站长查看，会员身份不等于管理员。 |

## PostStatusPo

PostStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/PostStatusPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `DRAFT` | 草稿：文章尚未发布，访客不可读取。 |
| `PUBLISHED` | 已发布：还需同时满足 PUBLIC 可见性，访客才能读取文章。 |

## PostVisibilityPo

PostVisibility 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/PostVisibilityPo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `PUBLIC` | 公开范围：文章满足对应发布规则后可供访客读取。 |
| `ADMIN_ONLY` | 仅管理员：文章及受保护的关联内容只供站长查看，会员身份不等于管理员。 |

## RevisionEventTypePo

PostRevisionEventType 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。

[打开源码](../speaive-blog-infrastructure/src/main/java/com/speaive/blog/infrastructure/content/persistence/po/RevisionEventTypePo.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `CREATE` | 通过写作台创建初始草稿快照。 |
| `IMPORT` | 从 Markdown 导入文章并记录初始快照。 |
| `UPDATE` | 修改内容或可见范围，保留原有发布状态。 |
| `PUBLISH` | 发布内容；可见范围仍由 visibility 决定。 |
| `UNPUBLISH` | 撤回为草稿，保留内容与历史。 |
| `ARCHIVE` | 记录归档快照并删除活动文章；历史保留，原 slug 可以重用。 |
| `RESTORE` | 把历史内容恢复成一个新修订，不回退版本号，也不自动改变当前发布状态。 |

## ApiErrorCode

HTTP 入站层自身的错误分类，覆盖认证、授权、参数校验与限流；业务错误沿用 BlogErrorCode。

[打开源码](../speaive-blog-start/src/main/java/com/speaive/blog/interfaces/http/error/ApiErrorCode.java)

| 枚举项 | 中文含义 |
| --- | --- |
| `INVALID_REQUEST` | HTTP 参数、JSON 或表单校验失败。 |
| `INVALID_CREDENTIALS` | 登录用户名或密码不正确。 |
| `UNAUTHORIZED` | 当前请求缺少有效登录身份。 |
| `FORBIDDEN` | 已识别请求但不允许访问，或写请求未通过 CSRF 校验。 |
| `RATE_LIMITED` | 登录尝试等请求触发限流，需要稍后再试。 |
| `TOO_LARGE` | 上传请求超过 HTTP 接收大小上限。 |

## 当前仍以字符串表达的状态

文章记忆摘要与圆桌摘要的接口还使用 `MISSING`（未生成）、`CURRENT`（与当前版本一致）、`STALE`（文章版本或评论指纹已变化）。这些值目前不是 Java 枚举，不能把本字典误读为已经完成类型收敛。后续可以分别引入带业务语义的摘要状态类型，并在 Result 边界保留现有字符串。

跨文章与小说的 `ContentRevision` 还用字符串承接各自的修订事件、状态与可见性；仓储负责按来源类型读取，后续收敛时应保留两套生命周期的差异。

## DeviceType

访问设备分类，不用于认定访客真实身份。[打开源码](../speaive-blog-domain/src/main/java/com/speaive/blog/domain/analytics/DeviceType.java)

| 枚举项 | 中文解释 |
| --- | --- |
| `MOBILE` | 手机或浏览器明确报告的移动设备。 |
| `TABLET` | 平板；桌面模式可能被识别为电脑。 |
| `DESKTOP` | 桌面或笔记本电脑，不代表已知硬件型号。 |
| `BOT` | 明确声明的爬虫或自动程序，默认不计入阅读统计。 |
| `UNKNOWN` | 信息缺失或无法识别。 |
