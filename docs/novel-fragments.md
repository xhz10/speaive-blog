# 小说片段模块

## 业务理解

这个模块不是“给文章再加一个小说标签”，而是一块独立的小说灵感工作区。它首先服务于低成本捕捉：人物、场景、对话或一个尚未完成的开头都可以单独保存，不要求先决定书名、卷、章和完整大纲。

第一版把每一份内容建模为独立的“小说片段”。现在已经可以用通用作品集把文章与小说片段混合编排并手动排序；以后需要严格的卷、章、人物设定等长篇结构时，仍可继续叠加，不必搬迁正文。

小说片段与普通文章使用相同的管理员身份、Session、Markdown 渲染和部署方式，但使用独立的数据表、后端用例、API 和页面：

- 普通文章继续负责博客、标签、评论和 AI 摘要；
- 小说片段只负责灵感写作、私藏、公开书架和沉浸阅读；
- 第一版不为小说接入标签、封面、AI 评论、会员投稿或图片上传，避免界面和调用成本提前膨胀。

## 状态与权限

状态和可见性仍是两个正交维度：

| 状态 | 可见性 | 游客 | 管理员 |
| --- | --- | --- | --- |
| `DRAFT` | `ADMIN_ONLY` | 不可见，详情返回 404 | 可编辑、自动保存、预览 |
| `DRAFT` | `PUBLIC` | 不可见，草稿永不公开 | 可编辑、自动保存、预览 |
| `PUBLISHED` | `ADMIN_ONLY` | 不可见，详情返回 404 | 可编辑、在受保护阅读页查看 |
| `PUBLISHED` | `PUBLIC` | 出现在 `/novels/`、详情页和 Sitemap | 可编辑、可撤回 |

新建片段默认 `DRAFT + ADMIN_ONLY`。必须同时完成“发布”和“选择所有访客”两个明确动作，内容才会离开写作台。公开查询在 SQL 层固定限制：

```text
status = PUBLISHED AND visibility = PUBLIC
```

公开接口对不存在、草稿和仅管理员片段统一返回 404，不暴露私密 slug 是否存在。邀请码会员没有 Studio 权限，也不能读取私密小说片段。

## 写作体验：降低操作成本

入口位于 `/studio/novels/`，与文章写作台并列，而不是藏在文章筛选里。

- 点击“捕捉灵感”后只需要标题和正文；简介可以留空，slug 会随标题生成；
- 草稿编辑停止 1.5 秒后自动保存，状态栏明确显示正在记录、已保存、失败或版本冲突；
- 首次保存前同步写入当前标签页的 `localStorage`，刷新后可恢复尚未进入数据库的输入；
- Markdown 编辑器复用 CodeMirror、常用格式工具、快捷键和 350 毫秒防抖的实时预览；
- 编辑区采用小说字体和更舒展的行距，右侧直接模拟公开阅读效果；
- 发布公开片段前会二次确认；私密定稿也可以发布，但提示它仍只有管理员可见；
- 两个页面同时编辑时使用 `id + revision` 的 CAS 版本令牌，旧页面得到 `409 VERSION_CONFLICT`，不会静默覆盖新内容。

已发布片段不再后台自动改动：编辑后需要点击“更新发布”，避免正在输入时把半句话同步给访客。撤回会把片段改回草稿并立刻从公开查询中消失。

## 展示体验：降低阅读成本

公开书架 `/novels/` 与博客归档采用不同的信息表达：深色开场、编号片段列表和简短故事钩子，让访客一眼知道这里是虚构写作而不是博客分类。

详情页 `/novels/{slug}/` 使用约 720px 的窄阅读宽度、衬线中文字体、较大行距、首字下沉和克制的元数据。移动端缩小标题和左右留白，不缩短正文内容。管理员预览 `/studio/novels/read/{slug}/` 复用同一阅读系统并加 `noindex`，所以私密内容不会进入搜索页面。

只有公开片段会写入动态 Sitemap；小说详情输出 `CreativeWork` 结构化数据，并继续关联站点的 Speaive 作者身份。公开作品集可以给片段提供上一篇、下一篇的连续阅读入口。私密片段不会进入 Sitemap、公开列表或搜索结构化数据。

## 数据与 API

Flyway `V8__Add_novel_fragments.sql` 新增：

- `blog_novel_fragment`：当前片段、作者、状态、可见性和 revision；
- `blog_novel_fragment_revision`：`CREATE / UPDATE / PUBLISH / UNPUBLISH / RESTORE` 的完整快照。

Flyway `V9__Add_creative_workspace.sql` 让片段可以加入作品集、恢复历史版本和生成临时分享链接；这些表只保存片段引用，不复制正文。

正文仍以 Markdown 存储，HTML 在读取时统一渲染和过滤，不把派生 HTML 写进数据库。

公开 API：

```text
GET /api/v1/public/novels
GET /api/v1/public/novels/{slug}
```

管理员 API：

```text
GET  /api/v1/studio/novels
GET  /api/v1/studio/novels/{slug}
POST /api/v1/studio/novels
PUT  /api/v1/studio/novels/{slug}
POST /api/v1/studio/novels/{slug}/publish
POST /api/v1/studio/novels/{slug}/unpublish
```

Studio 写操作沿用现有 Session 与 CSRF。作者由服务端固定为管理员，客户端不能伪造。

## 实现过程与验收

实现遵循现有模块化单体边界：小说领域聚合维护状态、权限、时间和版本规则；application 编排用例并定义持久化端口；infrastructure 保存 PostgreSQL 快照；start 只做 HTTP DTO、认证入口和 Bean 装配；Astro 负责 Studio 与公开页面。

验收覆盖：

- 新片段默认私密草稿；
- 私密片段即使发布也不进入公开列表，公开详情返回 404；
- 改为公开后访客可读，撤回后立即不可读；
- 版本冲突不能覆盖新内容，每次写操作形成连续修订事件；
- Markdown HTML 继续经过安全过滤；
- 从历史迁移链升级到 V8，外键类型和旧数据兼容；
- 从 V8 升级到 V9 后可恢复修订、加入作品集和创建临时分享；
- Astro 类型检查、Vitest、生产 SSR 构建、Java 单元测试、架构守卫和 PostgreSQL Testcontainers 集成测试；
- 桌面与移动视口检查公开书架、阅读页和写作台关键布局。

## 部署与后续演进

部署脚本不需要修改。备份后在 `main` 拉取代码并继续执行：

```bash
./scripts/docker-up.sh
```

镜像重建时，Spring Boot 启动会自动把数据库迁移到 V9。小说正文、修订、作品集引用和分享授权都位于 PostgreSQL，已包含在现有数据库 dump 中。

未来只有出现明确需求时再增加：专用卷章层级、小说设定库、导出或 AI 辅助。评论和会员共创也应单独设计权限与成本边界，不能默认复用文章的自动评论流程。
