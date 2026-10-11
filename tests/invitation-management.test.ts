import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

class ElementStub extends EventTarget {
  textContent = "";
  className = "";
  hidden = false;
  disabled = false;
  isConnected = true;
  dataset: Record<string, string> = {};
  children: ElementStub[] = [];
  parent: ElementStub | null = null;
  selectors: Record<string, ElementStub> = {};
  focus = vi.fn();
  constructor(readonly tagName = "div") { super(); }
  matches(selector: string) {
    const attr = selector.match(/^\[data-([a-z-]+)\]$/)?.[1];
    return !!attr && attr.replace(/-([a-z])/g, (_match, letter: string) => letter.toUpperCase()) in this.dataset;
  }
  querySelector(selector: string): ElementStub | null {
    return this.selectors[selector] ?? this.children.find(child => child.matches(selector))
      ?? this.children.map(child => child.querySelector(selector)).find(Boolean) ?? null;
  }
  closest(selector: string): ElementStub | null { return this.matches(selector) ? this : this.parent?.closest(selector) ?? null; }
  append(...nodes: ElementStub[]) { nodes.forEach(node => { node.parent = this; this.children.push(node); }); }
  prepend(node: ElementStub) { node.parent = this; this.children.unshift(node); }
  setAttribute() {}
}
class DialogStub extends ElementStub {
  open = false;
  showModal() { this.open = true; }
  close() { this.open = false; this.dispatchEvent(new Event("close")); }
}

beforeEach(() => vi.resetModules());
afterEach(() => vi.unstubAllGlobals());

async function setup() {
  const dialog = new DialogStub(), accept = new ElementStub(), cancel = new ElementStub();
  dialog.selectors = { "[data-confirm-title]": new ElementStub(), "[data-confirm-message]": new ElementStub(),
    "[data-confirm-accept]": accept, "[data-confirm-cancel]": cancel };
  const form = Object.assign(new ElementStub(), { reportValidity: () => true });
  const submit = new ElementStub(), notice = new ElementStub(), code = new ElementStub(), codeValue = new ElementStub();
  const copy = new ElementStub(), history = new ElementStub(), reviewNotice = new ElementStub();
  const data = { validDays: "30", maxUses: "2", note: "  给朋友 <img src=x>  " };
  form.selectors["button[type=submit]"] = submit;
  const elements: Record<string, ElementStub> = { "[data-account-confirm]": dialog, "[data-invite-form]": form,
    "[data-invite-notice]": notice, "[data-new-code]": code, "[data-new-code-value]": codeValue,
    "[data-invite-history]": history, "[data-review-notice]": reviewNotice, "[data-copy-code]": copy };
  const win = Object.assign(new EventTarget(), { location: { reload: vi.fn() } });
  const fetch = vi.fn().mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-TOKEN", token: "csrf" }));
  vi.stubGlobal("Element", ElementStub); vi.stubGlobal("HTMLElement", ElementStub);
  vi.stubGlobal("window", win);
  vi.stubGlobal("document", { querySelector: (selector: string) => elements[selector] ?? null,
    createElement: (tag: string) => new ElementStub(tag), activeElement: null, body: new ElementStub() });
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { callback(0); return 1; });
  vi.stubGlobal("FormData", class { get(name: keyof typeof data) { return data[name]; } });
  vi.stubGlobal("fetch", fetch);
  vi.stubGlobal("navigator", { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
  const { initializeCommunityManagement } = await import("../src/lib/studio/community");
  initializeCommunityManagement();
  const click = (node: ElementStub) => node.dispatchEvent(new Event("click"));
  return { form, submit, notice, code, codeValue, copy, history, dialog, accept, cancel, win, fetch, data, click,
    generate: () => form.dispatchEvent(new Event("submit", { cancelable: true })),
    revoke: (row: ElementStub) => {
      const event = new Event("click");
      Object.defineProperty(event, "target", { value: row.querySelector("[data-revoke-invite]") });
      history.dispatchEvent(event);
    }
  };
}

const invite = { id: "invite-1", code: "secret-invite-code", usedCount: 0, maxUses: 2,
  expiresAt: "2030-01-01T00:00:00Z", createdAt: "2026-01-01T00:00:00Z", note: "给朋友 <img src=x>", revokedAt: null };

describe("邀请码创建与撤销", () => {
  it("创建携带备注并在列表作为文字显示，在途重复提交只生成一次", async () => {
    const ui = await setup();
    ui.fetch.mockResolvedValueOnce(Response.json(invite));
    ui.generate(); ui.generate();
    await vi.waitFor(() => expect(ui.history.children).toHaveLength(1));
    expect(ui.fetch).toHaveBeenCalledTimes(2);
    const init = ui.fetch.mock.calls[1][1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({ validDays: 30, maxUses: 2, note: invite.note });
    const row = ui.history.children[0];
    expect(row.querySelector("[data-invite-note]")!.textContent).toBe(invite.note);
    expect(row.querySelector("[data-invite-status]")!.textContent).toBe("可使用");
    expect(ui.codeValue.textContent).toBe(invite.code);
    expect(ui.submit.disabled).toBe(false);
  });
  it("取消撤销不会请求服务端；确认后更新同一行，清除该邀请码明文，不刷新页面", async () => {
    const ui = await setup();
    ui.fetch.mockResolvedValueOnce(Response.json(invite));
    ui.generate();
    await vi.waitFor(() => expect(ui.history.children).toHaveLength(1));
    const row = ui.history.children[0], revoke = row.querySelector("[data-revoke-invite]")!;
    ui.revoke(row); ui.click(ui.cancel);
    await vi.waitFor(() => expect(revoke.disabled).toBe(false));
    expect(ui.fetch).toHaveBeenCalledTimes(2);
    ui.fetch.mockResolvedValueOnce(Response.json({ ...invite, code: null, revokedAt: "2026-10-11T14:00:00Z" }));
    ui.revoke(row); ui.revoke(row); ui.click(ui.accept);
    await vi.waitFor(() => expect(row.querySelector("[data-invite-status]")!.textContent).toBe("已撤销"));
    expect(ui.fetch).toHaveBeenCalledTimes(3);
    expect(ui.fetch.mock.calls[2][0]).toBe("/api/v1/studio/invitations/invite-1/revoke");
    expect(revoke.disabled).toBe(true);
    expect(ui.codeValue.textContent).toBe("");
    expect(ui.code.hidden).toBe(true);
    expect(ui.win.location.reload).not.toHaveBeenCalled();
  });
  it("撤销失败保留明文和备注，重新启用按钮供站长重试", async () => {
    const ui = await setup();
    ui.fetch.mockResolvedValueOnce(Response.json(invite));
    ui.generate();
    await vi.waitFor(() => expect(ui.history.children).toHaveLength(1));
    const row = ui.history.children[0], revoke = row.querySelector("[data-revoke-invite]")!;
    ui.fetch.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    ui.revoke(row); ui.click(ui.accept);
    await vi.waitFor(() => expect(revoke.disabled).toBe(false));
    expect(row.querySelector("[data-invite-row-notice]")!.textContent).toContain("暂时无法连接服务器");
    expect(row.querySelector("[data-invite-note]")!.textContent).toBe(invite.note);
    expect(ui.codeValue.textContent).toBe(invite.code);
  });
  it("离开后返回时在途创建响应不能再次显示明文邀请码", async () => {
    const ui = await setup();
    let resolveRequest!: (response: Response) => void;
    ui.fetch.mockImplementationOnce(() => new Promise(resolve => { resolveRequest = resolve; }));
    ui.generate();
    await vi.waitFor(() => expect(ui.fetch).toHaveBeenCalledTimes(2));
    ui.win.dispatchEvent(new Event("pagehide"));
    ui.win.dispatchEvent(new Event("pageshow"));
    resolveRequest(Response.json(invite));
    await vi.waitFor(() => expect(ui.submit.disabled).toBe(false));
    expect(ui.codeValue.textContent).toBe("");
    expect(ui.code.hidden).toBe(true);
    expect(ui.history.children).toHaveLength(0);
  });
});
