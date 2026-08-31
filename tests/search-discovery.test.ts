import { afterEach, describe, expect, it, vi } from "vitest";

import { buildStructuredData, getSearchVerificationTags } from "../src/lib/seo";
import { GET as getRobots } from "../src/pages/robots.txt";
import { GET as getSitemap } from "../src/pages/sitemap.xml";

afterEach(() => {
  vi.unstubAllGlobals();
  delete process.env.SPEAIVE_BACKEND_URL;
  delete process.env.SPEAIVE_SITE_URL;
});

describe("search identity metadata", () => {
  const configuredSite = new URL("https://speaive.cn/");

  it("defines Speaive as the website name and its owner as a Person", () => {
    const schema = buildStructuredData({
      canonicalUrl: configuredSite,
      configuredSite,
      description: "个人博客",
      type: "website"
    });
    const graph = schema["@graph"];
    const website = graph.find((node) => node["@type"] === "WebSite");
    const person = graph.find((node) => node["@type"] === "Person");

    expect(website).toMatchObject({
      name: "Speaive",
      alternateName: ["speaive.cn"],
      url: "https://speaive.cn/"
    });
    expect(website?.creator).toEqual({ "@id": "https://speaive.cn/about/#speaive" });
    expect(person).toMatchObject({
      name: "Speaive",
      url: "https://speaive.cn/about/"
    });
  });

  it("links Speaive articles to the same Person identity", () => {
    const canonicalUrl = new URL("https://speaive.cn/blog/a-note/");
    const schema = buildStructuredData({
      canonicalUrl,
      configuredSite,
      title: "一篇文章",
      description: "摘要",
      publishedAt: "2026-08-28T00:00:00.000Z",
      author: {
        id: "00000000-0000-0000-0000-000000000001",
        username: "admin",
        displayName: "Speaive",
        type: "HUMAN",
        avatarUrl: null
      },
      type: "article"
    });
    const article = schema["@graph"].find((node) => node["@type"] === "BlogPosting");

    expect(article?.author).toMatchObject({
      "@type": "Person",
      "@id": "https://speaive.cn/about/#speaive",
      name: "Speaive",
      url: "https://speaive.cn/about/"
    });
    expect(article?.isPartOf).toMatchObject({
      "@type": "WebSite",
      "@id": "https://speaive.cn/#website"
    });
  });

  it("describes public fiction fragments as CreativeWork", () => {
    const canonicalUrl = new URL("https://speaive.cn/novels/rainy-platform/");
    const schema = buildStructuredData({
      canonicalUrl,
      configuredSite,
      title: "雨夜站台",
      description: "一段小说片段",
      publishedAt: "2026-08-30T08:00:00.000Z",
      author: {
        id: "00000000-0000-0000-0000-000000000001",
        username: "admin",
        displayName: "Speaive",
        type: "HUMAN",
        avatarUrl: null
      },
      type: "fiction"
    });
    const fiction = schema["@graph"].find((node) => node["@type"] === "CreativeWork");

    expect(fiction).toMatchObject({
      name: "雨夜站台",
      genre: "Fiction",
      isPartOf: { "@id": "https://speaive.cn/#website" },
      author: { "@id": "https://speaive.cn/about/#speaive" }
    });
  });

  it("emits only configured search ownership tags", () => {
    expect(getSearchVerificationTags({
      SPEAIVE_GOOGLE_SITE_VERIFICATION: " google-code ",
      SPEAIVE_BING_SITE_VERIFICATION: "",
      SPEAIVE_BAIDU_SITE_VERIFICATION: "baidu-code"
    })).toEqual([
      { name: "google-site-verification", content: "google-code" },
      { name: "baidu-site-verification", content: "baidu-code" }
    ]);
  });
});

describe("search crawler discovery", () => {
  it("publishes public pages and updated timestamps in the sitemap", async () => {
    const author = {
      id: "00000000-0000-0000-0000-000000000001",
      username: "admin",
      displayName: "Speaive",
      type: "HUMAN",
      avatarUrl: null
    };
    vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/public/novels")) {
        return Response.json({ items: [{
          slug: "雨夜站台",
          title: "雨夜站台",
          excerpt: "她没有登上最后一班车。",
          publishedAt: "2026-08-29T08:00:00Z",
          updatedAt: "2026-08-30T10:30:00Z",
          author,
          status: "PUBLISHED",
          visibility: "PUBLIC",
          version: "fragment-id:2"
        }] });
      }
      if (url.includes("/public/works")) {
        return Response.json({ items: [{
          slug: "夜行故事",
          title: "夜行故事",
          description: "一条连续阅读路径",
          cover: null,
          visibility: "PUBLIC",
          revision: 1,
          updatedAt: "2026-08-30T11:00:00Z",
          items: [{
            contentType: "POST",
            contentSlug: "想法-&-记录",
            position: 0,
            title: "第一篇",
            summary: "摘要",
            href: "/blog/想法-&-记录/",
            publishedAt: "2026-08-27T08:00:00Z"
          }]
        }] });
      }
      return Response.json({ items: [{
        slug: "想法-&-记录",
        title: "第一篇",
        description: "摘要",
        publishedAt: "2026-08-27T08:00:00Z",
        updatedAt: "2026-08-28T09:30:00Z",
        tags: [],
        cover: null,
        author
      }] });
    }));

    const response = await getSitemap({
      site: new URL("https://speaive.cn/"),
      url: new URL("https://speaive.cn/sitemap.xml")
    } as Parameters<typeof getSitemap>[0]) as Response;
    const body = await response.text();

    expect(response.headers.get("Content-Type")).toBe("application/xml; charset=utf-8");
    expect(response.headers.get("Cache-Control")).toBe("public, max-age=300");
    expect(body).toContain("<loc>https://speaive.cn/</loc>");
    expect(body).toContain("<loc>https://speaive.cn/archive/</loc>");
    expect(body).toContain("<loc>https://speaive.cn/novels/</loc>");
    expect(body).toContain("/novels/%E9%9B%A8%E5%A4%9C%E7%AB%99%E5%8F%B0/");
    expect(body).toContain("/works/%E5%A4%9C%E8%A1%8C%E6%95%85%E4%BA%8B/");
    expect(body).toContain("%E6%83%B3%E6%B3%95-%26-%E8%AE%B0%E5%BD%95");
    expect(body).toContain("<lastmod>2026-08-28T09:30:00.000Z</lastmod>");
    expect(body).not.toContain("/studio/");
  });

  it("builds robots.txt from the configured public origin", async () => {
    process.env.SPEAIVE_SITE_URL = "https://speaive.cn";

    const response = getRobots({
      site: new URL("https://fallback.example/"),
      url: new URL("https://fallback.example/robots.txt")
    } as Parameters<typeof getRobots>[0]) as Response;
    const body = await response.text();

    expect(body).toContain("Disallow: /studio/");
    expect(body).toContain("Disallow: /s/");
    expect(body).toContain("Sitemap: https://speaive.cn/sitemap.xml");
  });
});
