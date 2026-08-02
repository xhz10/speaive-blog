# Markdown 直接投递

除了在 `/studio` 在线写作和上传 Markdown，也可以通过 SSH、SFTP 或同步工具把文件放入 `SPEAIVE_DATA_DIR/inbox/`。导入成功后文章以草稿进入 PostgreSQL，源文件移到 `inbox/imported/`；失败文件移到 `inbox/rejected/`，旁边会生成原因文件。

`inbox` 只是导入箱，PostgreSQL 才是文章的唯一真源。不要再把文件放进旧的 `posts/` 或 `drafts/` 目录。

## 文件格式

文件扩展名必须是小写 `.md`，不接受 MDX。推荐格式：

```markdown
---
title: 第一篇随记
slug: first-note
description: 一段可选的文章摘要
publishedAt: 2026-08-02T08:00:00.000Z
tags:
  - 随记
  - 生活
cover: /media/2026/08/cover.webp
---

从这里开始写正文。
```

规则：

- 文件名用于缺省 slug，例如 `first-note.md`；frontmatter 中的 slug 可以覆盖文件名；
- 可以省略全部 frontmatter，标题取正文第一个一级标题，发布时间取导入时间；
- `description`、`tags` 和 `cover` 可选；日期建议使用带时区的 ISO 8601；
- slug 最长 100 个字符，可使用文字、数字和连字符，不能有空格、下划线、连续连字符或路径分隔符；
- Markdown 最大 1 MiB；原始 HTML 会经过过滤；
- 已存在相同 slug 时文件会进入 `rejected/`，不会覆盖网页中已有文章；
- 相同文件内容再次投递时会按 SHA-256 去重并移到 `imported/`。

封面和正文中的 `/media/...` 应先通过写作台上传。媒体必须同时存在数据库记录和服务器文件，直接复制图片到 `media/` 不会自动注册。

## 安全投递

不要把正在传输的内容直接写成最终 `.md`，否则扫描器可能看到半个文件。先使用隐藏临时名，完成后在同一文件系统原子改名：

```bash
scp first-note.md blog-server:/srv/speaive-blog/data/inbox/.first-note.md.uploading
ssh blog-server \
  'mv /srv/speaive-blog/data/inbox/.first-note.md.uploading /srv/speaive-blog/data/inbox/first-note.md'
```

扫描器忽略隐藏文件和临时后缀，默认每 30 秒检查一次，也会在服务启动后立即检查。导入后请在写作台确认正文、标签和封面，再手动发布。
