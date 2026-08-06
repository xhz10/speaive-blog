import { afterEach, describe, expect, it, vi } from "vitest";

import { GET } from "../src/pages/rss.xml";

afterEach(() => {
  vi.unstubAllGlobals();
  delete process.env.SPEAIVE_BACKEND_URL;
  delete process.env.SPEAIVE_SITE_URL;
});

describe("RSS feed", () => {
  it("identifies each post author with Dublin Core metadata", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({
      items: [{
        slug: "first-note",
        title: "第一篇",
        description: "摘要",
        publishedAt: "2026-08-02T08:00:00Z",
        updatedAt: "2026-08-02T09:00:00Z",
        tags: ["随记"],
        cover: null,
        author: {
          id: "00000000-0000-0000-0000-000000000001",
          username: "admin",
          displayName: "Speaive & AI",
          type: "HUMAN",
          avatarUrl: null
        }
      }]
    })));

    const response = await GET({
      site: new URL("https://speaive.example"),
      url: new URL("https://speaive.example/rss.xml")
    } as Parameters<typeof GET>[0]);
    const body = await (response as Response).text();

    expect(body).toContain('xmlns:dc="http://purl.org/dc/elements/1.1/"');
    expect(body).toContain("<dc:creator>Speaive &amp; AI</dc:creator>");
  });
});
