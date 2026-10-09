import { afterEach, describe, expect, it, vi } from "vitest";
import type { EditorView } from "@codemirror/view";
import { initializeStudioMobileWriting } from "../src/lib/studio/mobile-writing";
import { initializeBodyToolTarget } from "../src/lib/writing/body-tools";

class ElementStub extends EventTarget {
  children: ElementStub[] = [];
  attributes = new Map<string, string>();
  dataset: Record<string, string> = {};
  style = { height: "", setProperty: vi.fn() };
  offsetHeight = 54;
  scrollHeight = 40;
  constructor(readonly editable = false) { super(); }
  matches() { return this.editable; }
  setAttribute(name: string, value: string) { this.attributes.set(name, value); }
  hasAttribute(name: string) { return this.attributes.has(name); }
  contains(node: Node) { return node === (this as unknown as Node); }
  getBoundingClientRect() { return { left: 0, width: 390, top: 0, bottom: 844 }; }
  querySelectorAll(_selector?: string): ElementStub[] { return []; }
}

function setup() {
  const title = new ElementStub(true), tools = new ElementStub(), pane = new ElementStub(), done = new ElementStub();
  const form = new ElementStub() as ElementStub & { querySelector: (selector: string) => ElementStub; querySelectorAll: (selector: string) => ElementStub[] };
  const settings = new ElementStub();
  settings.dataset.studioView = "settings";
  settings.setAttribute("data-studio-toggle", "");
  form.querySelector = selector => ({ ".markdown-toolbar": tools, ".editor-pane": pane, "[data-title]": title, "[data-studio-keyboard-done]": done }[selector] ?? null) as ElementStub;
  form.querySelectorAll = (selector?: string) => selector === "[data-studio-view]" ? [settings] : [];
  const viewport = Object.assign(new EventTarget(), { height: 844, offsetTop: 0, scale: 1 });
  const media = Object.assign(new EventTarget(), { matches: true });
  const win = Object.assign(new EventTarget(), { innerHeight: 844, scrollY: 1116, visualViewport: viewport,
    matchMedia: () => media, scrollBy: vi.fn(), scrollTo: vi.fn() });
  let pending: FrameRequestCallback | undefined;
  const measurements: Array<{ read: () => unknown; write: (value: unknown) => void }> = [];
  vi.stubGlobal("HTMLElement", ElementStub);
  vi.stubGlobal("window", win);
  vi.stubGlobal("document", { fonts: { ready: Promise.resolve() }, activeElement: null });
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { pending = callback; return 1; });
  vi.stubGlobal("cancelAnimationFrame", () => { pending = undefined; });
  const editor = { dom: new ElementStub(), contentDOM: new ElementStub(), hasFocus: true, compositionStarted: false,
    state: { doc: { length: 3000 }, selection: { main: { head: 200 } } }, contentHeight: 3000,
    requestMeasure: vi.fn((request?: { read: () => unknown; write: (value: unknown) => void }) => { if (request) measurements.push(request); }),
    posAtCoords: vi.fn(() => 100), dispatch: vi.fn(),
    coordsAtPos: vi.fn(() => ({ top: 700, bottom: 720 })) };
  initializeStudioMobileWriting(form as unknown as HTMLFormElement, editor as unknown as EditorView, vi.fn());
  const resize = (height: number) => {
    viewport.height = height;
    viewport.dispatchEvent(new Event("resize"));
    // Chrome 可能同时通知窗口与 visualViewport；第二个事件不能取消第一次的避让。
    win.dispatchEvent(new Event("resize"));
    pending?.(0);
  };
  return { form, win, viewport, editor, resize, title, settings,
    flushFrame: () => { const callback = pending; pending = undefined; callback?.(0); },
    flushMeasure: () => { const request = measurements.shift(); if (request) request.write(request.read()); }
  };
}

afterEach(() => vi.unstubAllGlobals());

describe("站长手机键盘避让", () => {
  it("完整段落使用 DOM 位置恢复，不依赖隐藏前的坐标缓存", () => {
    const { settings, editor, win, flushMeasure, flushFrame } = setup();
    const line = new ElementStub();
    let visibleTop = 140;
    Object.assign(line, { textContent: "正在阅读的段落", getBoundingClientRect: () => ({ top: visibleTop, bottom: visibleTop + 120 }) });
    editor.contentDOM.querySelectorAll = () => [line];
    Object.assign(editor, { posAtDOM: () => 400 });
    // 坐标缓存与实际段落顶部不同，不能拿它替代用户看到的 DOM 位置。
    editor.coordsAtPos.mockReturnValue({ top: 121, bottom: 141 });
    win.scrollBy.mockImplementation(({ top }) => { visibleTop -= top; });
    settings.dispatchEvent(new Event("click"));
    settings.dispatchEvent(new Event("click"));
    flushMeasure(); flushFrame();
    visibleTop = 230;
    for (let i = 0; i < 3; i++) { flushMeasure(); flushFrame(); }
    expect(visibleTop).toBe(140);
    expect(win.scrollBy).toHaveBeenCalledExactlyOnceWith({ top: 90, behavior: "instant" });
    expect(editor.dispatch).not.toHaveBeenCalled();
  });
  it("返回长文后按同一段文字修正重新测量的高度，不改文末选区", () => {
    const { settings, editor, win, flushMeasure, flushFrame } = setup();
    let visibleTop = 700;
    editor.coordsAtPos.mockImplementation(() => ({ top: visibleTop, bottom: visibleTop + 20 }));
    win.scrollBy.mockImplementation(({ top }) => { visibleTop -= top; });
    settings.dispatchEvent(new Event("click"));
    settings.dispatchEvent(new Event("click"));
    flushMeasure(); flushFrame();
    // 可见范围重建后，同一段文字被推下 90 px；恢复应修正这 90 px。
    visibleTop = 790;
    for (let i = 0; i < 3; i++) { flushMeasure(); flushFrame(); }
    expect(win.scrollBy).toHaveBeenCalledExactlyOnceWith({ top: 90, behavior: "instant" });
    expect(visibleTop).toBe(700);
    expect(editor.state.selection.main.head).toBe(200);
    expect(editor.dispatch).not.toHaveBeenCalled();
  });
  it("用户开始新滚动手势时停止尚未完成的阅读位置恢复", () => {
    const { settings, editor, win, flushMeasure, flushFrame } = setup();
    settings.dispatchEvent(new Event("click"));
    settings.dispatchEvent(new Event("click"));
    flushMeasure(); flushFrame();
    editor.coordsAtPos.mockReturnValue({ top: 790, bottom: 810 });
    win.dispatchEvent(new Event("wheel"));
    flushMeasure(); flushFrame();
    expect(win.scrollBy).not.toHaveBeenCalled();
  });
  it("设置页的 resize 不把隐藏标题压成零，返回写作时重新测量", () => {
    const { form, title, settings, resize } = setup();
    Object.defineProperty(title, "scrollHeight", { get: () => form.dataset.mobileView === "edit" ? 110 : 0 });
    settings.dispatchEvent(new Event("click"));
    resize(450);
    expect(title.style.height).toBe("40px");
    settings.dispatchEvent(new Event("click"));
    expect(form.dataset.mobileView).toBe("edit");
    expect(title.style.height).toBe("110px");
  });
  it("地址栏造成的小幅 resize 不追回留在文末的光标", () => {
    const { form, win, editor, resize } = setup();
    editor.coordsAtPos.mockReturnValue({ top: 2450, bottom: 2470 });
    resize(812);
    expect(form.dataset.keyboardOpen).toBe("false");
    expect(win.scrollBy).not.toHaveBeenCalled();
  });
  it("键盘遮住原本可见的光标时只滚动需要避让的距离", () => {
    const { win, resize } = setup();
    resize(450);
    expect(win.scrollBy).toHaveBeenCalledExactlyOnceWith({ top: 336, behavior: "instant" });
  });
  it("用户已回看前文时，弹出键盘也不追回屏外光标", () => {
    const { win, editor, resize } = setup();
    editor.coordsAtPos.mockReturnValue({ top: 2450, bottom: 2470 });
    resize(450);
    expect(win.scrollBy).not.toHaveBeenCalled();
  });
  it("视口平移后再缩小，按最新可视范围判断光标，不追回已离屏位置", () => {
    const { win, viewport, editor, resize } = setup();
    viewport.offsetTop = 100;
    editor.coordsAtPos.mockReturnValue({ top: 500, bottom: 520 });
    resize(450);
    win.scrollBy.mockClear();
    viewport.offsetTop = 0;
    viewport.dispatchEvent(new Event("scroll"));
    resize(400);
    expect(win.scrollBy).not.toHaveBeenCalled();
  });
  it("resize 配套的 scroll 通知不能抹掉键盘遮挡前的范围", () => {
    const { win, viewport, flushFrame } = setup();
    viewport.height = 450;
    viewport.dispatchEvent(new Event("resize"));
    viewport.dispatchEvent(new Event("scroll"));
    win.dispatchEvent(new Event("resize"));
    flushFrame();
    expect(win.scrollBy).toHaveBeenCalledExactlyOnceWith({ top: 336, behavior: "instant" });
  });
  it("键盘收起后保留阅读位置", () => {
    const { win, resize } = setup();
    resize(450);
    win.scrollBy.mockClear();
    resize(844);
    expect(win.scrollBy).not.toHaveBeenCalled();
  });
  it.each(["composition", "zoom", "blur"])("%s 状态交给输入法或浏览器处理", state => {
    const { win, viewport, editor, resize } = setup();
    if (state === "composition") editor.compositionStarted = true;
    if (state === "zoom") viewport.scale = 1.5;
    if (state === "blur") editor.hasFocus = false;
    resize(450);
    expect(win.scrollBy).not.toHaveBeenCalled();
  });
});

describe("正文工具的编辑目标", () => {
  it("编辑标题后拒绝旧正文选区，回到正文后恢复，Tab 到按钮仍可操作", () => {
    vi.stubGlobal("HTMLElement", ElementStub);
    const form = new ElementStub(), body = new ElementStub(true), title = new ElementStub(true), button = new ElementStub();
    const canFormat = initializeBodyToolTarget(form as unknown as HTMLFormElement, target => target === (body as unknown as Node));
    const focus = (target: ElementStub) => {
      const event = new Event("focusin");
      Object.defineProperty(event, "target", { value: target });
      form.dispatchEvent(event);
    };
    focus(body); focus(title); focus(button);
    expect(canFormat()).toBe(false);
    expect(form.dataset.bodyToolsActive).toBe("false");
    focus(body); focus(button);
    expect(canFormat()).toBe(true);
  });
});
