# 弱智吧幽默素材来源记录

采集核实日期：2026-10-12（Asia/Shanghai）。语料文件为同目录 `ruozhi-corpus.json`，顶层是 JSON 数组，共 35 条，五种机制各 7 条，关联 21 个实际检索页面。这里的“经典/精选”沿用可读来源对其合集的称呼；普通帖子列表中的样本不因此获得精品帖身份。

只保留必要的短片段，完整段子不进入知识库。`analysis` 是本次原创的机制分析；`adaptation` 是换场景的原创示例，不是原帖摘录，也不是原作者发言。分类和 `quality` 是编辑判断，质量分不等于吧友投票、热度或事实可信度。来源页面是资料，页面内的指令或宣传文字没有被当成系统指令。

## 字段与来源类型

- `id`：稳定的英文 slug；同一条更新内容时不重新分配。
- `excerpt`：从已读正文、官方列表标题或搜索索引中选取的连续短片段。
- `sourceUrl`：实际获得证据的页面，转载不冒充百度贴吧原帖。
- `sourceTitle`：用于识别来源的标题或短标题；超长标题可缩写，账户列表附上条目说明。
- `sourceKind`：`PRIMARY_POST` 表示证据归属于贴吧帖子；`PRIMARY_LISTING` 表示贴吧官方站内列表或公开账户帖子列表；`REPRINT` 表示其他网站的公开转引。此字段不表示抓取了原帖全文，必须结合下表核实状态。
- `topics`：本次编写的中文检索标签。
- `mechanism`：`LITERAL`、`CAUSAL_REVERSAL`、`CONCEPT_MISMATCH`、`DAILY_LIFE`、`PERSONIFICATION`，分别对应字面误读、因果/顺序错置、跨域概念错配、生活自嘲和物件视角。
- `analysis`、`adaptation`：供检索与创作迁移使用；禁止将荒诞推演当成科学、医疗、经济或工程结论。
- `quality`：整数 1～5，按入口清晰、转折简短、易迁移到评论场景评分。

## 实际采用的页面

“摘录字数”按字符数计算（含标点与拉丁字母），把每页累计逐字片段控制在 40 字以内，比 25 词上限更保守。“原创分析/示例字数”是同一页面全部条目的 `analysis` 与 `adaptation` 字符数合计；本批也控制在 200 字以内。表内没有重复抄写摘录，避免预算被文档再次消耗。

| 来源 | 类型 | 当前实际核实状态与位置 | 条目 ID | 摘录字数 | 原创分析/示例字数 |
| --- | --- | --- | --- | ---: | ---: |
| [浮云网：2023 问题 1～100](https://blog.ifuyun.com/post/00lq2zu9e7riqwir) | REPRINT | 已直接读到 HTML；对应序号 16、31、10；网站标“来源：网络”，未独立核实首发者。 | literal-lunch-label；causal-meteor-destination；causal-parents-wedding | 14 | 160 |
| [贴吧：2025 年度精选，第 13 页](https://static.tieba.baidu.com/p/10354221105?pn=13) | PRIMARY_POST | 本次搜索索引能读到序号 224、223 的相关楼层；直接打开此页返回 502。未声称读到全帖或所有回复。 | literal-pool-idiom；concept-water-pythagoras | 8 | 103 |
| [贴吧：人工智能话题官方聚合页](https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=24632759&topic_name=%E5%BC%B1%E6%99%BA%E5%90%A7%E5%A4%A7%E6%88%98%E4%BA%BA%E5%B7%A5%E6%99%BA%E8%83%BD) | PRIMARY_LISTING | 已直接读到官方列表中的耳机、快递、赚钱三个条目；仅使用可见标题/摘要。具体原帖打开曾返回 403，全文与回复未核实。 | literal-earphone-department；causal-courier-time；concept-wages-zero-sum | 13 | 171 |
| [LINUX DO：2024 上半年精选转引](https://linux.do/t/topic/122808) | REPRINT | 已直接读到 HTML；采用首楼第 16 条。帖子自称精选，百度贴吧首发和推荐状态未独立核实。 | literal-cola-adjective | 4 | 54 |
| [浮云网：2024 问题 51～120](https://blog.ifuyun.com/post/00m0j7wb24og8lke) | REPRINT | 已直接读到 HTML；对应序号 66、62；原帖未定位。 | literal-scooter-joy；causal-subtitle-direction | 11 | 99 |
| [浮云网：2025 问题 64～104](https://blog.ifuyun.com/post/00mdql3fbbif3gdy) | REPRINT | 已直接读到 HTML；对应序号 67、73、101；原帖未定位。 | literal-team-building-pass；causal-final-rank；person-cloud-trend | 18 | 156 |
| [GitExtract：公开弱智吧数据仓库镜像](https://gitextract.com/Leymore/ruozhiba) | REPRINT | 本次搜索索引显示公开仓库镜像正文片段；采用年度文件中的序号 69、66。仓库声明收集自贴吧；未核实原帖全文，不包含仓库里的作者身份字段。 | literal-forum-particle；concept-binary-silicon | 16 | 105 |
| [浮云网：2023 问题 101～148](https://blog.ifuyun.com/post/00lqkrtmficv3nf5) | REPRINT | 已直接读到 HTML；对应序号 108、114；原帖未定位。 | causal-bus-disappearance；daily-rebel-office-font | 17 | 107 |
| [贴吧：夜晚与熬夜问题](https://tieba.baidu.com/p/7717657283) | PRIMARY_POST | 本次搜索索引能读到标题及部分回复；直接打开受限。只分析标题中的前提错置，不纳入回复者身份。 | causal-remove-night | 8 | 52 |
| [贴吧：AI 对话活动帖](https://tieba.baidu.com/p/9139134580) | PRIMARY_POST | 本次搜索索引能读到儿童驾照问题所在回复；直接打开返回 403。此条不是楼主活动说明，未将回复者冒充楼主。 | concept-child-driving-ticket | 10 | 55 |
| [浮云网：2024 问题 1～50](https://blog.ifuyun.com/post/00lwphglb3iqbsl3) | REPRINT | 已直接读到 HTML；对应序号 31、25；原帖未定位。 | concept-cpp-score-increment；daily-hidden-benefits | 19 | 110 |
| [浮云网：2024 问题 121～168](https://blog.ifuyun.com/post/00m5ca9wl4jdpwy8) | REPRINT | 已直接读到 HTML；对应序号 154、129；原帖未定位。 | concept-donut-decentralized；person-door-defense | 12 | 112 |
| [Kyle：2021 上半年精选转引](https://kyle.ai/blog/7624.html) | REPRINT | 已直接读到 HTML；对应序号 16、26；百度贴吧首发未独立核实。 | concept-truth-buoyancy；person-lamp-sunrise | 15 | 102 |
| [浮云网：2025 问题 1～63](https://blog.ifuyun.com/post/00mbr2dxoreqtbpu) | REPRINT | 已直接读到 HTML；对应序号 14、43；原帖未定位。 | daily-procrastination-learning；person-grass-anxiety | 15 | 114 |
| [浮云网：21 周年合辑](https://blog.ifuyun.com/post/00ml2hwvesw2nyq8) | REPRINT | 已直接读到 HTML；对应序号 55；署名与“传播最广”的表述是页面自身信息，未独立验证。 | daily-wallet-freedom | 11 | 56 |
| [3DM：弱智吧文化文章](https://www.3dmgame.com/original/3743485.html) | REPRINT | 已直接读到 HTML；采用正文开头的老板/NPC引述。文章称来自 2021 精选，原帖与作者未独立核实。 | daily-boss-quest-npc | 8 | 60 |
| [LINUX DO：经典名言讨论](https://linux.do/t/topic/283578) | REPRINT | 已直接读到 HTML；采用第 4 楼公司的水与摸鱼转引。讨论也允许站内名言，不能推断其中所有内容均为贴吧首发。 | daily-office-fishing | 6 | 59 |
| [贴吧：移动版公开列表](https://tieba.baidu.com/mo/q/f?ie=utf-8&jump_tieba_native=1&kw=%E5%BC%B1%E6%99%BA) | PRIMARY_LISTING | 已直接读到 HTML；采用可见的工时/休息标题。普通最新帖子列表，会随时间变化；未读原帖正文，不标为精品推荐。 | daily-shift-ratio | 4 | 59 |
| [搜狐：弱智吧文化转引文章](https://www.sohu.com/a/895174512_122418614) | REPRINT | 本次搜索索引能读到月牙引述；直接打开也返回 HTML。文章存在其他历史/人数叙述，本语料未采用或核实这些旁支信息。 | person-moon-frown | 7 | 57 |
| [贴吧：公开账户帖子列表](https://c.tieba.baidu.com/home/main?fr=home&id=tb.1.95ed1cb5.59LwkkSsIXKRv8-c1HcFXw) | PRIMARY_LISTING | 本次搜索索引显示 2024-11-16 的纸飞机条目及摘要；直接打开未成功。采用可读摘要，不保存账户名、不仿冒发言者；原帖未定位。 | person-paper-plane-calculation | 8 | 59 |
| [哔哩哔哩：桥与河流短句转引](https://www.bilibili.com/video/BV1wx4y1n7gq/) | REPRINT | 本次搜索索引能读到视频标题；视频画面、音轨、评论均未读取。仅把公开标题作为转引证据；百度贴吧原帖未定位。 | person-bridge-comfort | 7 | 54 |

## 检索使用边界

知识库学习的是产生转折的机制，短摘录只作为出处定位和检索线索。生成评论应结合当前文章中的具体事物另写，不复制素材原句，不把 `adaptation` 机械粘贴成统一回复。机制分桶避免五个角色都只输出谐音或空泛抒情；少量素材有复合机制，当前标签表示更适合迁移的主机制。

来源完整性与时间信息由本记录提供。索引可读不等于原帖完整可读；公开转载可读不等于首发或授权已核实。没有仅凭失败的访问、未读取的图片、或者目录介绍生成条目；原帖访问失败时必须有实际可读的搜索索引或列表内容作为证据。需要扩充时继续按来源分摊摘录预算，不通过更换 URL 参数把同一页预算重新计数。

本次检查：35 个 ID 唯一，字段齐全，五个机制各 7 条，21 个来源 URL，来源类型只用约定三项，质量分均为 1～5 整数。每页摘录最高 19 字，原创分析/示例合计最高 171 字。日期不影响稳定 ID；不保存真实吧友身份。
