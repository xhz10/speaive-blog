import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { memberReturnPath } from "../src/lib/writing/auth-navigation";

beforeEach(() => vi.resetModules());
afterEach(() => vi.unstubAllGlobals());

describe("会员登录返回路径", () => {
  it.each(["/writing/new/", "/writing/?filter=ARCHIVED&page=2", "/agents/?page=2"])("保留真实的会员工作路径 %s", path => {
    expect(memberReturnPath(path)).toBe(path);
  });
  it.each(["https://evil.test", "//evil.test", "/writing/../../studio/", "/writing/%2e%2e/studio/", "/agents/login/?next=/agents/login/", "/agents/register/", "/writing\\evil", "/writing/\n/evil", "/writing-other/", null])("拒绝外站、越界路径和登录循环 %s", path => {
    expect(memberReturnPath(path)).toBe("/writing/");
  });
});

describe("写作中断恢复", () => {
  it("站长断网时提示中文，恢复网络后重新获取令牌并保存", async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce(Response.json({ headerName: "X-XSRF-TOKEN", token: "test" }))
      .mockResolvedValueOnce(Response.json({ slug: "p", body: "仍保留的正文" }));
    vi.stubGlobal("fetch", fetchMock);
    const { studioRequest } = await import("../src/lib/studio/client");
    const init = { method: "PUT", body: JSON.stringify({ body: "仍保留的正文" }) };
    await expect(studioRequest("/studio/posts/p", init)).rejects.toMatchObject({
      code: "NETWORK_ERROR", message: "暂时无法连接服务器，请检查网络后重试。"
    });
    await expect(studioRequest("/studio/posts/p", init)).resolves.toMatchObject({ body: "仍保留的正文" });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][1].body).toBe(init.body);
  });
  it("站长保存请求本身断网时也提示中文", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: "X-XSRF-TOKEN", token: "test" }))
      .mockRejectedValueOnce(new TypeError("Failed to fetch")));
    const { studioRequest } = await import("../src/lib/studio/client");
    await expect(studioRequest("/studio/posts/p", { method: "PUT" })).rejects.toMatchObject({ code: "NETWORK_ERROR" });
  });
  it("登录过期只返回错误，不导航离开当前输入", async () => {
    const assign = vi.fn(); vi.stubGlobal("window", { location: { assign } });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ code: "UNAUTHORIZED", message: "未登录" }, { status: 401 })));
    const { writingRequest } = await import("../src/lib/writing/client");
    await expect(writingRequest("/settings")).rejects.toMatchObject({ status: 401, code: "SESSION_EXPIRED" });
    expect(assign).not.toHaveBeenCalled();
  });
  it("版本冲突保留原错误和版本，不重试覆盖", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: "X-XSRF-TOKEN", token: "test" }))
      .mockResolvedValueOnce(Response.json({ code: "VERSION_CONFLICT", message: "文章已更新" }, { status: 409 }));
    vi.stubGlobal("fetch", fetchMock);
    const { writingRequest, jsonRequest } = await import("../src/lib/writing/client");
    await expect(writingRequest("/posts/p", jsonRequest("PUT", { version: "p:1" }, "alice")))
      .rejects.toMatchObject({ code: "VERSION_CONFLICT" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const init = fetchMock.mock.calls[1][1] as RequestInit;
    expect(new Headers(init.headers).get("X-Writing-Username")).toBe("alice");
    expect(JSON.parse(init.body as string).version).toBe("p:1");
  });
  it("其他窗口换了账号后，拒绝继续提交原作者输入", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ username: "bobby" })));
    const { ensureWritingIdentity } = await import("../src/lib/writing/session");
    await expect(ensureWritingIdentity("alice")).rejects.toThrow("@alice");
  });
  it("同一作者重新登录后可以继续", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ username: "alice" })));
    const { ensureWritingIdentity } = await import("../src/lib/writing/session");
    await expect(ensureWritingIdentity("alice")).resolves.toBeUndefined();
  });
});
