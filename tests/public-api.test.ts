import { afterEach, describe, expect, it, vi } from "vitest";

import { getPublishedPost, listPublishedPosts } from "../src/lib/public-api";

afterEach(() => {
  vi.unstubAllGlobals();
  delete process.env.SPEAIVE_BACKEND_URL;
});

describe("public blog API", () => {
  it("maps published post summaries returned by Spring Boot", async () => {
    process.env.SPEAIVE_BACKEND_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(Response.json({
      items: [{
        slug: "first-note",
        title: "第一篇",
        description: "摘要",
        publishedAt: "2026-08-02T08:00:00Z",
        updatedAt: "2026-08-02T09:00:00Z",
        tags: ["随记"],
        cover: null,
        status: "PUBLISHED",
        version: "2"
      }],
      errors: []
    }));
    vi.stubGlobal("fetch", fetchMock);

    const posts = await listPublishedPosts();

    expect(fetchMock).toHaveBeenCalledWith(
      new URL("http://backend:8080/api/v1/public/posts"),
      expect.objectContaining({ cache: "no-store" })
    );
    expect(posts).toEqual([expect.objectContaining({
      slug: "first-note",
      publishedAt: new Date("2026-08-02T08:00:00Z"),
      updatedAt: new Date("2026-08-02T09:00:00Z")
    })]);
  });

  it("returns null only for a missing published article", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 404 })));

    await expect(getPublishedPost("not-published")).resolves.toBeNull();
  });

  it("does not hide backend failures as missing articles", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 503 })));

    await expect(getPublishedPost("temporarily-unavailable"))
      .rejects.toThrow("博客服务请求失败：503");
  });
});
