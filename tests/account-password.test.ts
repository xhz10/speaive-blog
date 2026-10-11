import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { newPasswordError, passwordConfirmationError } from "../src/lib/writing/password";
import { invitationCanRevoke, invitationStatus } from "../src/lib/studio/invitations";
import { memberReturnPath } from "../src/lib/writing/auth-navigation";

beforeEach(() => vi.resetModules());
afterEach(() => vi.unstubAllGlobals());

describe("会员新密码规则", () => {
  it("按 UTF-8 字节限制中文、表情和 ASCII，避免 BCrypt 截断", () => {
    expect(newPasswordError("a".repeat(72))).toBe("");
    expect(newPasswordError("a".repeat(73))).toContain("72 字节");
    expect(newPasswordError("汉".repeat(24))).toBe("");
    expect(newPasswordError("汉".repeat(25))).toContain("72 字节");
    expect(newPasswordError("🙂".repeat(18))).toBe("");
    expect(newPasswordError("🙂".repeat(19))).toContain("72 字节");
  });
  it("沿用 HTML 与 Java 的 UTF-16 长度，并拒绝全部为空白的密码", () => {
    expect(newPasswordError("123456789")).toContain("10–100");
    expect(newPasswordError("🙂".repeat(5))).toBe("");
    expect(newPasswordError("a".repeat(101))).toContain("10–100");
    expect(newPasswordError(" ".repeat(10))).toContain("空白");
    expect(newPasswordError("\u3000".repeat(10))).toContain("空白");
    expect(newPasswordError("\u00a0".repeat(10))).toBe("");
  });
  it("保留合法密码首尾空格，两次输入按原值比较", () => {
    expect(newPasswordError("  valid-password  ")).toBe("");
    expect(passwordConfirmationError("  valid-password  ", "valid-password")).toContain("不一致");
    expect(passwordConfirmationError("  valid-password  ", "  valid-password  ")).toBe("");
  });
});

describe("找回令牌与页面引导", () => {
  const token = "a".repeat(43);
  it("找回页面不能被用作登录返回地址或把凭据嵌进下一步导航", () => {
    expect(memberReturnPath(`/agents/reset-password/#token=${token}`)).toBe("/writing/");
  });
  it("只接受片段中的一个符合后端格式的令牌，拒绝缺失、重复和异常值", async () => {
    const { passwordResetTokenFromFragment } = await import("../src/lib/writing/password-client");
    expect(passwordResetTokenFromFragment(`#token=${token}`)).toBe(token);
    for (const fragment of ["", `?token=${token}`, `#token=${token}&token=${token}`, "#token=short", `#token=${"<".repeat(43)}`]) {
      expect(passwordResetTokenFromFragment(fragment)).toBeNull();
    }
  });
  it("生成链接把令牌留在 fragment，HTTP 请求地址不含令牌", async () => {
    const { memberPasswordResetUrl } = await import("../src/lib/writing/password-client");
    const url = new URL(memberPasswordResetUrl("https://blog.test", token));
    expect(url.pathname).toBe("/agents/reset-password/");
    expect(url.search).toBe("");
    expect(url.hash).toBe(`#token=${token}`);
    expect(`${url.origin}${url.pathname}${url.search}`).not.toContain(token);
  });
  it("真实页面的头部引导立即清 fragment，并只允许内存交接一次", () => {
    const page = readFileSync(new URL("../src/pages/agents/reset-password.astro", import.meta.url), "utf8");
    const bootstrap = page.match(/<script is:inline slot="head">([\s\S]*?)<\/script>/)![1];
    const window = {
      location: { hash: `#token=${token}`, pathname: "/agents/reset-password/", search: "?from=member" },
      history: { state: { from: "member" }, replaceState: vi.fn() },
      addEventListener: vi.fn(),
      __takeMemberPasswordResetFragment: undefined as undefined | (() => string)
    };
    runInNewContext(bootstrap, { window });
    expect(window.history.replaceState).toHaveBeenCalledExactlyOnceWith(window.history.state, "", "/agents/reset-password/?from=member");
    expect(window.__takeMemberPasswordResetFragment!()).toBe(`#token=${token}`);
    expect(window.__takeMemberPasswordResetFragment).toBeUndefined();
  });
  it("离开页面会清除尚未交接的令牌，避免浏览器返回时重复保留", () => {
    const page = readFileSync(new URL("../src/pages/agents/reset-password.astro", import.meta.url), "utf8");
    const bootstrap = page.match(/<script is:inline slot="head">([\s\S]*?)<\/script>/)![1];
    const addEventListener = vi.fn();
    const window = { location: { hash: `#token=${token}`, pathname: "/agents/reset-password/", search: "" },
      history: { state: null, replaceState: vi.fn() }, addEventListener,
      __takeMemberPasswordResetFragment: undefined as undefined | (() => string) };
    runInNewContext(bootstrap, { window });
    const heldGetter = window.__takeMemberPasswordResetFragment!;
    expect(addEventListener.mock.calls[0][0]).toBe("pagehide");
    addEventListener.mock.calls[0][1]();
    expect(window.__takeMemberPasswordResetFragment).toBeUndefined();
    expect(heldGetter()).toBe("");
  });
});

describe("密码请求的账号和 CSRF 边界", () => {
  it("改密携带原账号与 CSRF；成功后重新获取 CSRF，匿名找回不携带账号或自动导航", async () => {
    const assign = vi.fn(); vi.stubGlobal("window", { location: { assign } });
    const fetch = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf-1" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf-2" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);
    const { changeMemberPassword, resetMemberPassword } = await import("../src/lib/writing/password-client");
    await changeMemberPassword("alice", "old-password", "new-password");
    await resetMemberPassword("a".repeat(43), "reset-password");
    expect(fetch.mock.calls[1][0]).toBe("/api/v1/account/password/change");
    const changeInit = fetch.mock.calls[1][1] as RequestInit;
    expect(new Headers(changeInit.headers).get("X-Writing-Username")).toBe("alice");
    expect(new Headers(changeInit.headers).get("X-CSRF-TOKEN")).toBe("csrf-1");
    expect(JSON.parse(changeInit.body as string)).toEqual({ currentPassword: "old-password", newPassword: "new-password" });
    expect(fetch.mock.calls[2][0]).toBe("/api/v1/studio/csrf");
    expect(fetch.mock.calls[3][0]).toBe("/api/v1/account/password/reset");
    const resetInit = fetch.mock.calls[3][1] as RequestInit;
    expect(new Headers(resetInit.headers).get("X-CSRF-TOKEN")).toBe("csrf-2");
    expect(new Headers(resetInit.headers).has("X-Writing-Username")).toBe(false);
    expect(resetInit.credentials).toBe("same-origin");
    expect(assign).not.toHaveBeenCalled();
  });
  it("有效登录下输错旧密码保留提示，不误报会话过期或跳转", async () => {
    const assign = vi.fn(); vi.stubGlobal("window", { location: { assign } });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" }))
      .mockResolvedValueOnce(Response.json({ code: "INVALID_CREDENTIALS", message: "当前密码不正确" }, { status: 401 })));
    const { changeMemberPassword } = await import("../src/lib/writing/password-client");
    await expect(changeMemberPassword("alice", "wrong-password", "new-password"))
      .rejects.toMatchObject({ code: "INVALID_CREDENTIALS", message: "当前密码不正确" });
    expect(assign).not.toHaveBeenCalled();
  });
});

describe("邀请码列表兼容和撤销状态", () => {
  const oldInvite = { usedCount: 0, maxUses: 1, expiresAt: "2030-01-01T00:00:00Z" };
  it("旧响应缺少 revokedAt 时仍可识别可用邀请", () => {
    expect(invitationStatus(oldInvite, 0)).toBe("可使用");
    expect(invitationCanRevoke(oldInvite, 0)).toBe(true);
  });
  it("撤销优先于用完和过期；只有可用邀请码可以发起撤销", () => {
    expect(invitationStatus({ ...oldInvite, usedCount: 1, revokedAt: "2020-01-01T00:00:00Z" }, Date.parse("2031-01-01"))).toBe("已撤销");
    expect(invitationCanRevoke({ ...oldInvite, revokedAt: "2020-01-01T00:00:00Z" }, 0)).toBe(false);
    expect(invitationStatus({ ...oldInvite, usedCount: 1 }, 0)).toBe("已用完");
    expect(invitationCanRevoke(oldInvite, Date.parse("2031-01-01"))).toBe(false);
  });
});
