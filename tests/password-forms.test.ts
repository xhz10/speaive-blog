import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

class ElementStub extends EventTarget {
  value = "";
  textContent = "";
  className = "";
  hidden = false;
  disabled = false;
  dataset: Record<string, string> = {};
  selectors: Record<string, ElementStub> = {};
  setCustomValidity = vi.fn();
  querySelector(selector: string) { return this.selectors[selector] ?? null; }
}

function setup() {
  const newPassword = new ElementStub(), confirmPassword = new ElementStub(), currentPassword = new ElementStub();
  newPassword.value = confirmPassword.value = "new-password";
  currentPassword.value = "old-password";
  const inputs = { newPassword, confirmPassword, currentPassword };
  const fields = new ElementStub(), notice = new ElementStub(), login = new ElementStub(), button = new ElementStub();
  fields.disabled = true; login.hidden = true;
  const form = Object.assign(new ElementStub(), {
    elements: { namedItem: (name: keyof typeof inputs) => inputs[name] },
    reportValidity: vi.fn(() => true),
    querySelectorAll: () => Object.values(inputs),
    reset: vi.fn(() => { for (const input of Object.values(inputs)) input.value = ""; })
  });
  form.dataset.username = "alice";
  form.selectors = { "[data-reset-fields]": fields, "[data-reset-notice]": notice, "[data-reset-login]": login,
    "button[type=submit]": button, "[data-password-notice]": notice };
  const win = Object.assign(new EventTarget(), {
    __takeMemberPasswordResetFragment: () => `#token=${"a".repeat(43)}`,
    location: { assign: vi.fn() }
  });
  vi.stubGlobal("window", win);
  vi.stubGlobal("document", { querySelector: () => login });
  vi.stubGlobal("FormData", class {
    values: Record<string, string>;
    constructor() { this.values = Object.fromEntries(Object.entries(inputs).map(([name, input]) => [name, input.value])); }
    get(name: string) { return this.values[name]; }
  });
  const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
  return { form: form as unknown as HTMLFormElement, fields, notice, login, button, inputs, win, fetch,
    submit: () => form.dispatchEvent(new Event("submit", { cancelable: true })) };
}
const csrf = () => Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" });

beforeEach(() => vi.resetModules());
afterEach(() => vi.unstubAllGlobals());

describe("找回密码表单", () => {
  it("失败保留密码并可重试，在途重复提交只发送一次，成功后清除密码并引导登录", async () => {
    const ui = setup();
    let failRequest!: (error: unknown) => void;
    ui.fetch.mockResolvedValueOnce(csrf()).mockImplementationOnce(() => new Promise((_resolve, reject) => { failRequest = reject; }));
    const { initializePasswordReset } = await import("../src/lib/writing/password-reset");
    initializePasswordReset(ui.form);
    expect(ui.fields.disabled).toBe(false);
    ui.submit(); ui.submit();
    await vi.waitFor(() => expect(ui.fetch).toHaveBeenCalledTimes(2));
    expect(ui.fields.disabled).toBe(true);
    failRequest(new TypeError("Failed to fetch"));
    await vi.waitFor(() => expect(ui.fields.disabled).toBe(false));
    expect(ui.inputs.newPassword.value).toBe("new-password");
    expect(ui.notice.textContent).toContain("暂时无法连接服务器");
    ui.fetch.mockResolvedValueOnce(new Response(null, { status: 204 }));
    ui.submit();
    await vi.waitFor(() => expect(ui.login.hidden).toBe(false));
    expect(ui.fields.hidden).toBe(true);
    expect(ui.inputs.newPassword.value).toBe("");
    expect(ui.notice.textContent).toContain("已有登录已失效");
    ui.submit();
    expect(ui.fetch).toHaveBeenCalledTimes(3);
    expect(ui.win.location.assign).not.toHaveBeenCalled();
  });
  it("离开时清除令牌和输入，返回后的在途失败不能重新启用表单", async () => {
    const ui = setup();
    let failRequest!: (error: unknown) => void;
    ui.fetch.mockResolvedValueOnce(csrf()).mockImplementationOnce(() => new Promise((_resolve, reject) => { failRequest = reject; }));
    const { initializePasswordReset } = await import("../src/lib/writing/password-reset");
    initializePasswordReset(ui.form);
    ui.submit();
    await vi.waitFor(() => expect(ui.fetch).toHaveBeenCalledTimes(2));
    ui.win.dispatchEvent(new Event("pagehide"));
    ui.win.dispatchEvent(new Event("pageshow"));
    failRequest(new TypeError("Failed to fetch"));
    await vi.waitFor(() => expect(ui.inputs.newPassword.value).toBe(""));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(ui.fields.disabled).toBe(true);
    expect(ui.notice.textContent).toContain("找回链接已清除");
    ui.submit();
    expect(ui.fetch).toHaveBeenCalledTimes(2);
  });
  it("过期或已使用的链接不提供反复提交入口", async () => {
    const ui = setup();
    ui.fetch.mockResolvedValueOnce(csrf()).mockResolvedValueOnce(Response.json({ code: "INVALID_REQUEST", message: "找回链接已失效" }, { status: 400 }));
    const { initializePasswordReset } = await import("../src/lib/writing/password-reset");
    initializePasswordReset(ui.form);
    ui.submit();
    await vi.waitFor(() => expect(ui.fields.hidden).toBe(true));
    expect(ui.inputs.newPassword.value).toBe("");
    expect(ui.notice.textContent).toContain("已使用或已过期");
    ui.submit();
    expect(ui.fetch).toHaveBeenCalledTimes(2);
  });
});

describe("会员改密表单", () => {
  it("旧会话过期时保留输入并允许重试，不请求改密或离开当前页面", async () => {
    const ui = setup();
    ui.fetch.mockResolvedValueOnce(Response.json({ code: "UNAUTHORIZED", message: "未登录" }, { status: 401 }));
    const { initializePasswordChange } = await import("../src/lib/writing/password-settings");
    initializePasswordChange(ui.form);
    ui.submit();
    await vi.waitFor(() => expect(ui.notice.dataset.kind).toBe("error"));
    expect(ui.notice.textContent).toContain("重新登录");
    expect(ui.inputs.currentPassword.value).toBe("old-password");
    expect(ui.inputs.newPassword.value).toBe("new-password");
    expect(ui.button.disabled).toBe(false);
    expect(ui.fetch).toHaveBeenCalledExactlyOnceWith("/api/v1/account/writing/settings", expect.any(Object));
    expect(ui.win.location.assign).not.toHaveBeenCalled();
  });
  it("成功只清除密码输入，阻止重复提交并提供重新登录入口", async () => {
    const ui = setup();
    ui.fetch.mockResolvedValueOnce(Response.json({ username: "alice" })).mockResolvedValueOnce(csrf()).mockResolvedValueOnce(new Response(null, { status: 204 }));
    const { initializePasswordChange } = await import("../src/lib/writing/password-settings");
    initializePasswordChange(ui.form);
    ui.submit(); ui.submit();
    await vi.waitFor(() => expect(ui.notice.dataset.kind).toBe("success"));
    expect(ui.fetch).toHaveBeenCalledTimes(3);
    expect(ui.inputs.currentPassword.value).toBe("");
    expect(ui.inputs.newPassword.disabled).toBe(true);
    expect(ui.button.disabled).toBe(true);
    expect(ui.login.hidden).toBe(false);
    expect(ui.win.location.assign).not.toHaveBeenCalled();
  });
});
