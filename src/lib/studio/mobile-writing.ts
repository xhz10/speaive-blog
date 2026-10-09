import type { EditorView } from "@codemirror/view";
import { initializeBodyToolTarget } from "../writing/body-tools";

/** 站长文章编辑页的小屏视图；正文和撤销历史始终保留在同一个编辑器实例中。 */
type StudioWritingView = "edit" | "preview" | "settings";

export function initializeStudioMobileWriting(
  form: HTMLFormElement,
  editor: EditorView,
  refreshPreview: () => void
) {
  const mobile = window.matchMedia("(max-width: 800px)");
  const viewport = window.visualViewport;
  const tools = form.querySelector<HTMLElement>(".markdown-toolbar")!;
  const editPane = form.querySelector<HTMLElement>(".editor-pane")!;
  const title = form.querySelector<HTMLTextAreaElement>("[data-title]")!;
  const tabs = [...form.querySelectorAll<HTMLButtonElement>("[data-studio-view]")];
  const advanced = [...form.querySelectorAll<HTMLDetailsElement>(".studio-advanced-settings")];
  const formatScroll = form.querySelector<HTMLElement>(".studio-format-scroll");
  const extraTools = form.querySelector<HTMLElement>("[data-studio-format-extra]");
  const formatToggle = form.querySelector<HTMLButtonElement>("[data-studio-format-toggle]");
  const originalTools = formatScroll ? [...formatScroll.children] : [];
  const extraActions = new Set(["italic", "link", "list", "quote", "code"]);
  let current: StudioWritingView = "edit";
  let editScroll = 0;
  let readingAnchor: { pos: number; top: number; lineStart: boolean; doc: EditorView["state"]["doc"] } | null = null;
  let restoreGeneration = 0;
  let frame = 0;
  const canFormatBody = initializeBodyToolTarget(form, target => editor.dom.contains(target));
  let previousTop = viewport?.offsetTop ?? 0;
  let previousBottom = previousTop + (viewport?.height ?? window.innerHeight);
  let pendingViewport: { top: number; bottom: number } | null = null;
  // 用户重新开始手势滚动时，让正在进行的恢复立即让路。
  const cancelRestore = () => { restoreGeneration += 1; };
  window.addEventListener("touchstart", cancelRestore, { passive: true });
  window.addEventListener("wheel", cancelRestore, { passive: true });
  form.dataset.bodyToolsActive = "false";

  function closeFormatTools() {
    form.dataset.formatExpanded = "false";
    formatToggle?.setAttribute("aria-expanded", "false");
  }
  function adaptTools() {
    advanced.forEach(details => { details.open = !mobile.matches; });
    if (!formatScroll || !extraTools) return;
    if (mobile.matches) {
      for (const tool of originalTools) {
        const button = tool as HTMLButtonElement;
        if (extraActions.has(button.dataset.markdownAction ?? "") || button.dataset.editorHistory === "redo") extraTools.appendChild(button);
      }
    } else originalTools.forEach(tool => formatScroll.appendChild(tool));
    closeFormatTools();
  }
  adaptTools();
  formatToggle?.addEventListener("pointerdown", event => {
    if (editor.hasFocus) event.preventDefault();
  });
  formatToggle?.addEventListener("click", () => {
    if (!canFormatBody() || editor.compositionStarted) return;
    const expanded = form.dataset.formatExpanded !== "true";
    form.dataset.formatExpanded = String(expanded);
    formatToggle.setAttribute("aria-expanded", String(expanded));
  });
  form.addEventListener("focusin", event => {
    if (!tools.contains(event.target as Node) && !editor.dom.contains(event.target as Node)) closeFormatTools();
  });

  function syncViewControls() {
    tabs.forEach(tab => {
      const selected = tab.dataset.studioView === current;
      tab.setAttribute("aria-pressed", String(selected));
      if (tab.hasAttribute("data-studio-toggle")) {
        tab.textContent = selected ? "写作" : tab.dataset.studioView === "preview" ? "预览" : "更多";
        tab.setAttribute("aria-label", selected ? "返回写作" : tab.dataset.studioView === "preview" ? "文章预览" : "发布设置与更多工具");
      }
    });
  }

  function show(next: StudioWritingView, restoreScroll = true) {
    if (!mobile.matches || current === next) return;
    const generation = ++restoreGeneration;
    if (current === "edit") {
      editScroll = window.scrollY;
      readingAnchor = captureReadingAnchor();
      // 隐藏后 CodeMirror 会暂时使用估算高度。保留页面空间，避免返回长文时
      // 浏览器先把滚动位置截短；测量完成后再解除占位。
      editPane.style.minHeight = `${editPane.offsetHeight}px`;
    }
    const active = document.activeElement;
    if (active instanceof HTMLElement && form.contains(active)) active.blur();
    current = next;
    form.dataset.mobileView = next;
    form.dataset.bodyToolsActive = "false";
    closeFormatTools();
    syncViewControls();
    if (next === "edit") resizeTitle();
    editor.requestMeasure();
    if (next === "preview") refreshPreview();
    // 切回只恢复阅读位置，不主动聚焦，以免用户看完预览就被再次弹出的键盘打断。
    if (next === "edit") editor.requestMeasure({
      read: () => editor.contentHeight,
      write: () => requestAnimationFrame(() => {
        if (current !== "edit") return;
        editPane.style.minHeight = "";
        if (!restoreScroll || generation !== restoreGeneration) return;
        window.scrollTo({ top: editScroll, behavior: "instant" });
        // 长文重新显示会更新估算高度。先回到附近，再用同一段文字修正位置；
        // 不改变选区、不聚焦，也不追踪留在文末的输入光标。
        if (readingAnchor && readingAnchor.doc === editor.state.doc) {
          restoreReadingAnchor(readingAnchor, generation, 3);
        }
      })
    });
    else if (restoreScroll) window.scrollTo({ top: form.offsetTop, behavior: "instant" });
  }
  tabs.forEach(tab => {
    // 先记录正在阅读的正文位置，再由 show 收起键盘。按钮提前夺走焦点
    // 会触发编辑器重新测量，使记录的位置已经偏离用户刚才看到的段落。
    tab.addEventListener("pointerdown", event => {
      if (mobile.matches && editor.hasFocus) event.preventDefault();
    });
    tab.addEventListener("click", () => {
      const next = tab.dataset.studioView as StudioWritingView;
      show(tab.hasAttribute("data-studio-toggle") && next === current ? "edit" : next);
    });
  });
  form.dataset.mobileView = current;
  syncViewControls();

  function captureReadingAnchor() {
    const content = editor.contentDOM.getBoundingClientRect();
    const headerBottom = form.querySelector<HTMLElement>(".editor-mobile-header")?.getBoundingClientRect().bottom ?? 0;
    const visibleTop = Math.max(viewport?.offsetTop ?? 0, headerBottom, content.top);
    const visibleBottom = (viewport?.height ?? window.innerHeight) + (viewport?.offsetTop ?? 0);
    const y = visibleTop + 16;
    if (y >= visibleBottom) return null;
    const lineElement = [...editor.contentDOM.querySelectorAll<HTMLElement>(".cm-line")].find(line => {
      const bounds = line.getBoundingClientRect();
      return line.textContent?.trim() && bounds.top >= visibleTop && bounds.top < visibleBottom;
    });
    // DOM 映射不会受尚未刷新完的文档高度估算影响；超长段落才使用坐标回退。
    const pos = lineElement ? editor.posAtDOM(lineElement, 0) : editor.posAtCoords({ x: content.left + content.width / 2, y });
    if (pos === null) return null;
    if (lineElement) return { pos, top: lineElement.getBoundingClientRect().top, lineStart: true, doc: editor.state.doc };
    const line = editor.coordsAtPos(pos);
    return line ? { pos, top: line.top, lineStart: false, doc: editor.state.doc } : null;
  }

  function restoreReadingAnchor(anchor: NonNullable<typeof readingAnchor>, generation: number, remaining: number) {
    editor.requestMeasure({
      read: () => {
        if (current !== "edit" || generation !== restoreGeneration || anchor.doc !== editor.state.doc) return null;
        // 整段锚点以实际 DOM 顶部为准；坐标缓存可能还保留隐藏前的估算值。
        const lineElement = anchor.lineStart ? [...editor.contentDOM.querySelectorAll<HTMLElement>(".cm-line")]
          .find(line => editor.posAtDOM(line, 0) === anchor.pos) : null;
        const top = lineElement ? lineElement.getBoundingClientRect().top
          : anchor.lineStart ? editor.documentTop + editor.lineBlockAt(anchor.pos).top
          : editor.coordsAtPos(anchor.pos)?.top ?? editor.documentTop + editor.lineBlockAt(anchor.pos).top;
        return top - anchor.top;
      },
      write: delta => {
        if (delta === null || generation !== restoreGeneration) return;
        if (Math.abs(delta) > 1) window.scrollBy({ top: delta, behavior: "instant" });
        // 最多等待三次测量，覆盖长文可见范围重建，避免无限追踪页面位置。
        if (remaining > 1) requestAnimationFrame(() => restoreReadingAnchor(anchor, generation, remaining - 1));
      }
    });
  }

  function resizeTitle() {
    // 预览和设置隐藏了标题；此时 scrollHeight 为 0，不能覆盖已测量的高度。
    if (mobile.matches && current !== "edit") return;
    const scroll = window.scrollY;
    title.style.height = "auto";
    title.style.height = `${title.scrollHeight}px`;
    if (window.scrollY !== scroll) window.scrollTo({ top: scroll, behavior: "instant" });
  }
  title.addEventListener("input", resizeTitle);
  window.addEventListener("resize", resizeTitle);
  void document.fonts.ready.then(resizeTitle);
  resizeTitle();

  function positionTools() {
    // 键盘和浏览器地址栏可能只改变 visualViewport；放大页面不按键盘处理。
    const inset = mobile.matches && viewport && viewport.scale === 1
      ? Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop) : 0;
    form.style.setProperty("--studio-keyboard-inset", `${inset}px`);
    form.dataset.keyboardOpen = String(inset > 120);
  }
  function viewportResized() {
    positionTools();
    // 同一帧可能先后收到 visualViewport、window resize 和 scroll。
    // 保留第一次缩小前的范围，后续通知只更新当前范围，不能吃掉应有的避让。
    pendingViewport ??= { top: previousTop, bottom: previousBottom };
    previousTop = viewport?.offsetTop ?? 0;
    previousBottom = previousTop + (viewport?.height ?? window.innerHeight);
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      const visibleTop = viewport?.offsetTop ?? 0;
      const viewportBottom = visibleTop + (viewport?.height ?? window.innerHeight);
      const { top: oldTop, bottom: oldBottom } = pendingViewport!;
      pendingViewport = null;
      frame = 0;
      previousTop = visibleTop;
      previousBottom = viewportBottom;
      if (!mobile.matches || current !== "edit" || !editor.hasFocus || editor.compositionStarted
        || form.dataset.keyboardOpen !== "true" || viewportBottom >= oldBottom
        || (viewport && viewport.scale !== 1)) return;
      const caret = editor.coordsAtPos(editor.state.selection.main.head);
      if (!caret) return;
      // 地址栏收放不等于键盘弹出。用户已滚离光标时也不追回，保留回看前文的位置。
      if (caret.top < oldTop || caret.bottom > oldBottom) return;
      const visibleBottom = viewportBottom - tools.offsetHeight - 12;
      if (caret.bottom > visibleBottom) window.scrollBy({ top: caret.bottom - visibleBottom, behavior: "instant" });
    });
  }
  viewport?.addEventListener("resize", viewportResized);
  viewport?.addEventListener("scroll", () => {
    positionTools();
    // 软键盘打开时视口还能平移；下一次 resize 必须以平移后的范围判断。
    previousTop = viewport.offsetTop;
    previousBottom = previousTop + viewport.height;
  });
  window.addEventListener("resize", viewportResized);
  mobile.addEventListener("change", () => {
    if (!mobile.matches) {
      current = "edit";
      editPane.style.minHeight = "";
      form.dataset.mobileView = current;
      syncViewControls();
    }
    adaptTools();
    positionTools();
    editor.requestMeasure();
  });
  form.querySelector("[data-studio-keyboard-done]")!.addEventListener("click", () => {
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    form.dataset.bodyToolsActive = "false";
    closeFormatTools();
  });
  // 按下“完成”前保留正文焦点，由 click 明确收起，避免选择先被按钮夺走。
  form.querySelector("[data-studio-keyboard-done]")!.addEventListener("pointerdown", event => event.preventDefault());
  positionTools();

  return {
    canFormatBody,
    closeFormatTools,
    revealField(field: HTMLElement) {
      const details = field.closest("details");
      if (details) details.open = true;
      show(field.closest("[data-studio-settings]") ? "settings" : "edit", false);
    }
  };
}
