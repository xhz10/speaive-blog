# 搜索发现与 Speaive 身份

## 目标

`Speaive` 是站长长期使用的网络名字，不只是页面装饰。公开站点需要同时向人和搜索引擎表达三件事：

1. `Speaive` 是站点名称；
2. `Speaive` 是文章作者对应的个人身份；
3. `https://speaive.cn/` 是这个身份的主要公开站点。

Sitemap 负责帮助爬虫发现页面，结构化数据负责表达页面和身份之间的关系，站长平台负责验证归属并查看实际抓取结果。三者作用不同，提交 Sitemap 不等于保证收录或排名。

## 程序已经提供的入口

| 地址 | 用途 |
| --- | --- |
| `/robots.txt` | 允许公开页面抓取、屏蔽写作台，并声明 Sitemap 地址 |
| `/sitemap.xml` | 动态列出首页、归档、关于页和全部公开文章 |
| `/rss.xml` | 输出最近公开文章，保留文章作者 |
| `/about/` | 向读者和结构化数据说明 Speaive 身份 |

首页输出 `WebSite` 与 `Person` JSON-LD；关于页输出 `ProfilePage` 与同一个 `Person`；文章输出 `BlogPosting`，并把站长文章作者关联到该身份。所有实体使用稳定的 `@id`，避免每个页面生成互不相关的 Speaive。

公开权限边界没有改变：Sitemap 和 RSS 都只从公开文章接口读取；草稿、私密文章、写作台页面和未公开评论不会进入搜索发现入口。`robots.txt` 不是私密数据的安全措施，真正的隔离仍由公开 API 的权限规则保证。

## 站点归属验证

Google、Bing 和百度都支持在页面 `<head>` 中放置验证码。先在对应站长平台添加 `https://speaive.cn/`，选择 HTML meta 验证方式，再把 `content` 属性中的值写入服务器 `.env`：

```dotenv
SPEAIVE_GOOGLE_SITE_VERIFICATION=Google给出的content值
SPEAIVE_BING_SITE_VERIFICATION=Bing给出的content值
SPEAIVE_BAIDU_SITE_VERIFICATION=百度给出的content值
```

只填写验证码，不要粘贴完整 `<meta>` 标签。没有配置的平台不会输出空标签。修改后沿用现有部署命令：

```bash
./scripts/docker-up.sh
```

部署脚本不需要新增参数；Compose 会把三个可选验证码传给 Astro 前端容器。

## 提交顺序

1. 确认生产域名可以通过 HTTPS 访问，且 `.env` 中为 `SPEAIVE_SITE_URL=https://speaive.cn`。
2. 打开 `https://speaive.cn/robots.txt`，确认其中的 Sitemap 指向生产域名。
3. 打开 `https://speaive.cn/sitemap.xml`，确认只出现准备公开的文章。
4. 在 Google Search Console、Bing Webmaster Tools、百度搜索资源平台分别完成站点验证。
5. 在三个平台提交同一个地址：`https://speaive.cn/sitemap.xml`。
6. 用平台的 URL 检查或抓取诊断功能检查首页、关于页和一篇代表文章。

新文章发布或已有文章更新后，动态 Sitemap 会自动反映 URL 与 `lastmod`，不需要手工编辑文件。搜索平台何时抓取、是否收录以及最终展示名称仍由各搜索引擎决定，通常需要等待重新抓取。

## 部署后自检

```bash
curl --fail --show-error https://speaive.cn/robots.txt
curl --fail --show-error https://speaive.cn/sitemap.xml
curl --fail --show-error https://speaive.cn/ | grep -E 'WebSite|google-site-verification|msvalidate.01|baidu-site-verification'
curl --fail --show-error https://speaive.cn/about/ | grep -E 'ProfilePage|#speaive'
```

如果验证码不存在，先检查服务器 `.env`，再执行 `docker compose exec frontend env | grep SITE_VERIFICATION` 确认变量是否进入前端容器。验证码属于站点归属凭据，不要提交真实值到 Git；仓库只保留空配置示例。
