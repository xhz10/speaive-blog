import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

class ElementStub extends EventTarget {
  textContent = "";
  hidden = false;
  disabled = false;
  checked = false;
  value = "";
  isConnected = true;
  dataset: Record<string, string> = {};
  selectors: Record<string, ElementStub> = {};
  focus = vi.fn();
  querySelector(selector: string) { return this.selectors[selector] ?? null; }
}
class DialogStub extends ElementStub {
  open = false;
  showModal() { this.open = true; }
  close() { this.open = false; this.dispatchEvent(new Event("close")); }
}

beforeEach(() => vi.resetModules());
afterEach(() => vi.unstubAllGlobals());

async function setup() {
  const create = new ElementStub(), copy = new ElementStub(), dismiss = new ElementStub();
  const result = new ElementStub(), details = new ElementStub(), url = new ElementStub(), notice = new ElementStub();
  result.hidden = true;
  const form = new ElementStub();
  form.dataset = { username: "alice", version: "2" };
  const fields = { role: new ElementStub(), canPublish: new ElementStub(), encryptionAllowed: new ElementStub() };
  Object.assign(form, { elements: { namedItem: (name: keyof typeof fields) => fields[name] } });
  form.selectors = {
    "button[type=submit]": new ElementStub(), "[data-permissions-notice]": new ElementStub(),
    "[data-create-password-reset]": create, "[data-copy-password-reset]": copy, "[data-dismiss-password-reset]": dismiss,
    "[data-recovery-result]": result, "[data-recovery-details]": details, "[data-recovery-url]": url, "[data-recovery-notice]": notice
  };
  const dialog = new DialogStub(), accept = new ElementStub(), cancel = new ElementStub();
  dialog.selectors = { "[data-confirm-title]": new ElementStub(), "[data-confirm-message]": new ElementStub(),
    "[data-confirm-accept]": accept, "[data-confirm-cancel]": cancel };
  vi.stubGlobal("HTMLElement", ElementStub);
  vi.stubGlobal("document", { activeElement: create, body: new ElementStub(),
    querySelector: () => dialog, querySelectorAll: () => [form] });
  const win = Object.assign(new EventTarget(), { location: { origin: "https://blog.test" } });
  vi.stubGlobal("window", win);
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { callback(0); return 1; });
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { clipboard: { writeText } });
  const fetch = vi.fn().mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" }));
  vi.stubGlobal("fetch", fetch);
  const { initializeMemberManagement } = await import("../src/lib/studio/members");
  initializeMemberManagement();
  const click = (element: ElementStub) => element.dispatchEvent(new Event("click"));
  const generate = () => { click(create); click(accept); };
  return { create, copy, dismiss, result, details, url, notice, cancel, dialog, generate, click, fetch, writeText, win };
}

describe("会员找回链接的临时展示", () => {
  const token = "a".repeat(43);
  const resetResponse = () => Response.json({ username: "alice", token, expiresAt: "2030-01-01T00:00:00Z" });
  it("确认重新生成后立刻清掉旧链接，响应丢失时也不能再复制已被替代的链接", async () => {
    const ui = await setup();
    ui.fetch.mockResolvedValueOnce(resetResponse());
    ui.generate();
    await vi.waitFor(() => expect(ui.result.hidden).toBe(false));
    expect(ui.url.textContent).toContain(`#token=${token}`);
    expect(ui.details.textContent).toContain("@alice");
    let rejectRequest!: (reason: unknown) => void;
    ui.fetch.mockImplementationOnce(() => new Promise((_resolve, reject) => { rejectRequest = reject; }));
    ui.generate();
    await vi.waitFor(() => expect(ui.create.disabled).toBe(true));
    expect(ui.url.textContent).toBe("");
    expect(ui.result.hidden).toBe(true);
    expect(ui.copy.disabled).toBe(true);
    ui.click(ui.copy);
    expect(ui.writeText).not.toHaveBeenCalled();
    rejectRequest(new TypeError("Failed to fetch"));
    await vi.waitFor(() => expect(ui.create.disabled).toBe(false));
    expect(ui.copy.disabled).toBe(true);
    expect(ui.url.textContent).toBe("");
    expect(ui.notice.textContent).toContain("暂时无法连接服务器");
  });
  it("取消重发保留原链接；成功复制后从页面和后续复制操作中清掉", async () => {
    const ui = await setup();
    ui.fetch.mockResolvedValueOnce(resetResponse());
    ui.generate();
    await vi.waitFor(() => expect(ui.result.hidden).toBe(false));
    ui.click(ui.create);
    expect(ui.dialog.open).toBe(true);
    ui.click(ui.cancel);
    await Promise.resolve();
    expect(ui.fetch).toHaveBeenCalledTimes(2);
    expect(ui.result.hidden).toBe(false);
    expect(ui.url.textContent).toContain(token);
    ui.click(ui.copy);
    await vi.waitFor(() => expect(ui.result.hidden).toBe(true));
    expect(ui.writeText).toHaveBeenCalledExactlyOnceWith(`https://blog.test/agents/reset-password/#token=${token}`);
    expect(ui.url.textContent).toBe("");
    expect(ui.copy.disabled).toBe(true);
    ui.click(ui.copy);
    expect(ui.writeText).toHaveBeenCalledTimes(1);
  });
  it("离开又返回页面后，在途签发响应不能重新显示已清除的令牌，可以重新生成", async () => {
    const ui = await setup();
    let resolveRequest!: (response: Response) => void;
    ui.fetch.mockImplementationOnce(() => new Promise(resolve => { resolveRequest = resolve; }));
    ui.generate();
    await vi.waitFor(() => expect(ui.fetch).toHaveBeenCalledTimes(2));
    ui.win.dispatchEvent(new Event("pagehide"));
    ui.win.dispatchEvent(new Event("pageshow"));
    resolveRequest(resetResponse());
    await vi.waitFor(() => expect(ui.create.disabled).toBe(false));
    expect(ui.result.hidden).toBe(true);
    expect(ui.url.textContent).toBe("");
    expect(ui.copy.disabled).toBe(true);
    ui.fetch.mockResolvedValueOnce(resetResponse());
    ui.generate();
    await vi.waitFor(() => expect(ui.result.hidden).toBe(false));
    ui.win.dispatchEvent(new Event("pagehide"));
    expect(ui.url.textContent).toBe("");
  });
  it("确认期间离开页面会取消决定，返回后不能沿用旧确认签发链接", async () => {
    const ui = await setup();
    ui.click(ui.create);
    expect(ui.dialog.open).toBe(true);
    ui.win.dispatchEvent(new Event("pagehide"));
    expect(ui.dialog.open).toBe(false);
    ui.win.dispatchEvent(new Event("pageshow"));
    await Promise.resolve();
    expect(ui.fetch).not.toHaveBeenCalled();
    expect(ui.url.textContent).toBe("");
  });
});
