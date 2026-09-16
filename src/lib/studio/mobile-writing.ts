import type { EditorView } from "@codemirror/view";

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
  let current: StudioWritingView = "edit";
  let editScroll = 0;
  let frame = 0;

  function show(next: StudioWritingView, restoreScroll = true) {
    if (!mobile.matches || current === next) return;
    if (current === "edit") {
      editScroll = window.scrollY;
      // 隐藏后 CodeMirror 会暂时使用估算高度。保留页面空间，避免返回长文时
      // 浏览器先把滚动位置截短；测量完成后再解除占位。
      editPane.style.minHeight = `${editPane.offsetHeight}px`;
    }
    const active = document.activeElement;
    if (active instanceof HTMLElement && form.contains(active)) active.blur();
    current = next;
    form.dataset.mobileView = next;
    tabs.forEach(tab => tab.setAttribute("aria-pressed", String(tab.dataset.studioView === next)));
    editor.requestMeasure();
    if (next === "preview") refreshPreview();
    // 切回只恢复阅读位置，不主动聚焦，以免用户看完预览就被再次弹出的键盘打断。
    if (next === "edit") editor.requestMeasure({
      read: () => editor.contentHeight,
      write: () => requestAnimationFrame(() => {
        if (current !== "edit") return;
        editPane.style.minHeight = "";
        if (restoreScroll) window.scrollTo({ top: editScroll, behavior: "instant" });
      })
    });
    else if (restoreScroll) window.scrollTo({ top: form.offsetTop, behavior: "instant" });
  }
  tabs.forEach(tab => tab.addEventListener("click", () => show(tab.dataset.studioView as StudioWritingView)));
  form.dataset.mobileView = current;

  function resizeTitle() {
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
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      if (!mobile.matches || current !== "edit" || !editor.hasFocus || editor.compositionStarted
        || (viewport && viewport.scale !== 1)) return;
      const caret = editor.coordsAtPos(editor.state.selection.main.head);
      if (!caret) return;
      const visibleTop = viewport?.offsetTop ?? 0;
      const visibleBottom = visibleTop + (viewport?.height ?? window.innerHeight) - tools.offsetHeight - 12;
      // 只在可视高度改变时避让键盘；普通滚动不抢回光标，允许向上查看前文。
      if (caret.bottom > visibleBottom) window.scrollBy({ top: caret.bottom - visibleBottom, behavior: "instant" });
      else if (caret.top < visibleTop + 12) window.scrollBy({ top: caret.top - visibleTop - 12, behavior: "instant" });
    });
  }
  viewport?.addEventListener("resize", viewportResized);
  viewport?.addEventListener("scroll", positionTools);
  window.addEventListener("resize", viewportResized);
  mobile.addEventListener("change", () => {
    if (!mobile.matches) {
      current = "edit";
      editPane.style.minHeight = "";
      form.dataset.mobileView = current;
      tabs.forEach(tab => tab.setAttribute("aria-pressed", String(tab.dataset.studioView === current)));
    }
    positionTools();
    editor.requestMeasure();
  });
  form.querySelector("[data-studio-keyboard-done]")!.addEventListener("click", () => {
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
  });
  // 按下“完成”前保留正文焦点，由 click 明确收起，避免选择先被按钮夺走。
  form.querySelector("[data-studio-keyboard-done]")!.addEventListener("pointerdown", event => event.preventDefault());
  positionTools();

  return {
    revealField(field: HTMLElement) {
      show(field.closest("[data-studio-settings]") ? "settings" : "edit", false);
    }
  };
}
