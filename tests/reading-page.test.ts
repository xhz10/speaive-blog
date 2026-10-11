import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../src/lib/public-api", () => ({
  getPublishedPost: vi.fn(), getPublishedNovelFragment: vi.fn(), listPublishedComments: vi.fn(),
  getPublishedDiscussionDigest: vi.fn(), getPublishedWorkNavigation: vi.fn()
}));
import * as api from "../src/lib/public-api";
import { loadArticlePage, loadNovelPage } from "../src/lib/reading-page";

const post = { slug: "note", title: "仍能读到的文章", body: "正文", html: "<p>正文</p>" } as api.PublishedPost;
const comments = [{ id: "comment-1", body: "已公开的讨论" }] as api.PublishedComment[];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getPublishedPost).mockResolvedValue(post);
  vi.mocked(api.listPublishedComments).mockResolvedValue(comments);
  vi.mocked(api.getPublishedDiscussionDigest).mockResolvedValue(null);
  vi.mocked(api.getPublishedWorkNavigation).mockResolvedValue(null);
});

describe("阅读页按内容分别降级", () => {
  it("正文不存在和正文服务失败有不同状态，不再伪装成同一个404", async () => {
    vi.mocked(api.getPublishedPost).mockResolvedValueOnce(null).mockRejectedValueOnce(new Error("503"));
    expect(await loadArticlePage("note")).toEqual({ state: "missing" });
    expect(await loadArticlePage("note")).toEqual({ state: "unavailable" });
    expect(api.listPublishedComments).not.toHaveBeenCalled();
    expect(api.getPublishedWorkNavigation).not.toHaveBeenCalled();
  });
  it("评论失败仍保留正文，并明确失败，不能显示为还没有评论", async () => {
    vi.mocked(api.listPublishedComments).mockRejectedValue(new Error("comments unavailable"));
    expect(await loadArticlePage("note")).toMatchObject({
      state: "ready", post, comments: [], commentsUnavailable: true, digestUnavailable: false
    });
  });
  it("讨论摘要失败不会丢掉正常评论", async () => {
    vi.mocked(api.getPublishedDiscussionDigest).mockRejectedValue(new Error("digest unavailable"));
    expect(await loadArticlePage("note")).toMatchObject({
      state: "ready", post, comments, commentsUnavailable: false, digestUnavailable: true
    });
  });
  it("作品集导航失败不阻断正文和讨论", async () => {
    vi.mocked(api.getPublishedWorkNavigation).mockRejectedValue(new Error("navigation unavailable"));
    expect(await loadArticlePage("note", "collection")).toMatchObject({
      state: "ready", post, comments, navigation: null, navigationUnavailable: true
    });
    expect(api.getPublishedWorkNavigation).toHaveBeenCalledWith("POST", "note", "collection");
  });
  it("附属内容并行请求，不让前一个慢请求阻挡后面两项", async () => {
    let finishComments!: (value: api.PublishedComment[]) => void;
    vi.mocked(api.listPublishedComments).mockReturnValue(new Promise(resolve => { finishComments = resolve; }));
    const loading = loadArticlePage("note");
    await vi.waitFor(() => expect(api.getPublishedWorkNavigation).toHaveBeenCalled());
    expect(api.getPublishedDiscussionDigest).toHaveBeenCalled();
    finishComments(comments);
    expect(await loading).toMatchObject({ state: "ready", comments });
  });
  it("小说片段正文可读时，导航失败也只影响导航", async () => {
    const fragment = { slug: "scene", html: "<p>故事</p>" } as api.PublishedNovelFragment;
    vi.mocked(api.getPublishedNovelFragment).mockResolvedValue(fragment);
    vi.mocked(api.getPublishedWorkNavigation).mockRejectedValue(new Error("503"));
    expect(await loadNovelPage("scene")).toMatchObject({ state: "ready", fragment, navigationUnavailable: true });
  });
  it("小说不存在与服务中断分别处理，不请求附属导航", async () => {
    vi.mocked(api.getPublishedNovelFragment).mockResolvedValueOnce(null).mockRejectedValueOnce(new Error("503"));
    expect(await loadNovelPage("scene")).toEqual({ state: "missing" });
    expect(await loadNovelPage("scene")).toEqual({ state: "unavailable" });
    expect(api.getPublishedWorkNavigation).not.toHaveBeenCalled();
  });
});
