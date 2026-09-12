import { formatMarkdown, markdownLink, type MarkdownAction, type MarkdownEdit } from "./markdown";

/** 写作、预览、设置是界面视图，不改变文章的可见范围。 */
type WritingView = "edit" | "preview" | "settings";

export function initializeWritingExperience(form: HTMLFormElement) {
  const body = form.elements.namedItem("body") as HTMLTextAreaElement;
  const title = form.elements.namedItem("title") as HTMLTextAreaElement;
  const mobile = window.matchMedia("(max-width: 800px)");
  let view: WritingView = "edit";
  let editScroll = 0;
  let composing = false;
  let frame = 0;
  let measuredWidth = 0;

  const resize = () => {
    // 隐藏视图的 scrollHeight 为 0；等回到写作时再测量，避免切换后高度塌缩。
    if (form.querySelector<HTMLElement>("[data-edit-pane]")!.hidden || !body.clientWidth) return;
    const previousScroll = window.scrollY;
    for (const input of [title, body]) {
      input.style.height = "auto";
      input.style.height = `${input.scrollHeight}px`;
    }
    if (window.scrollY !== previousScroll) window.scrollTo({ top: previousScroll, behavior: "instant" });
  };
  const scheduleResize = () => {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(resize);
  };
  const show = (next: WritingView, navigate = false, previousScroll = window.scrollY) => {
    if (next === "settings" && !mobile.matches) next = "edit";
    if (view === "edit" && next !== view) editScroll = previousScroll;
    const changed = view !== next;
    view = next;
    form.dataset.view = next;
    form.querySelector<HTMLElement>("[data-edit-pane]")!.hidden = next !== "edit";
    form.querySelector<HTMLElement>("[data-preview-pane]")!.hidden = next !== "preview";
    form.querySelector("[data-edit-tab]")!.setAttribute("aria-pressed", String(next === "edit"));
    form.querySelector("[data-preview-tab]")!.setAttribute("aria-pressed", String(next === "preview"));
    form.querySelector("[data-settings-tab]")!.setAttribute("aria-pressed", String(next === "settings"));
    resize();
    if (navigate && changed) {
      // 不自动聚焦正文，避免只是看预览就再次唤起手机键盘。
      if (next === "edit") window.scrollTo({ top: editScroll, behavior: "instant" });
      else if (mobile.matches) window.scrollTo({ top: form.offsetTop, behavior: "instant" });
    }
  };
  form.querySelector("[data-edit-tab]")!.addEventListener("click", () => show("edit", true));
  form.querySelector("[data-settings-tab]")!.addEventListener("click", () => show("settings", true));
  mobile.addEventListener("change", () => { if (!mobile.matches && view === "settings") show("edit"); scheduleResize(); });
  form.addEventListener("input", scheduleResize);
  body.addEventListener("compositionstart", () => { composing = true; });
  body.addEventListener("compositionend", () => { composing = false; scheduleResize(); });
  const observer = new ResizeObserver(entries => {
    const width = entries[0]?.contentRect.width ?? 0;
    if (width !== measuredWidth) { measuredWidth = width; scheduleResize(); }
  });
  observer.observe(form);
  void document.fonts.ready.then(scheduleResize);
  resize();

  // Safari 等浏览器弹出键盘时可能只缩小可视区域；工具条跟随可视区域底边。
  // 放大阅读时交回浏览器处理，不把缩放误判成键盘。
  const viewport = window.visualViewport;
  const dialog = document.querySelector<HTMLDialogElement>("[data-writing-link-dialog]")!;
  const positionTools = () => {
    const inset = mobile.matches && viewport && viewport.scale === 1
      ? Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop) : 0;
    form.style.setProperty("--writing-keyboard-inset", `${inset}px`);
    const height = viewport?.height ?? window.innerHeight;
    dialog.style.setProperty("--writing-visible-height", `${height}px`);
    dialog.style.setProperty("--writing-visible-center", `${(viewport?.offsetTop ?? 0) + height / 2}px`);
  };
  viewport?.addEventListener("resize", positionTools);
  viewport?.addEventListener("scroll", positionTools);
  window.addEventListener("resize", positionTools);
  positionTools();

  function applyEdit(edit: MarkdownEdit): void {
    if (body.readOnly || body.disabled || composing) return;
    if (body.value.length - (edit.to - edit.from) + edit.text.length > body.maxLength) return;
    body.focus({ preventScroll: true });
    body.setSelectionRange(edit.from, edit.to);
    // 原生 insertText 可保留系统撤销记录；不支持时降级为选区替换。
    // https://developer.mozilla.org/en-US/docs/Web/API/Document/execCommand
    let inserted = false;
    try { inserted = document.execCommand("insertText", false, edit.text); } catch { /* 使用下方降级路径。 */ }
    if (!inserted) body.setRangeText(edit.text, edit.from, edit.to, "end");
    body.setSelectionRange(edit.selectionStart, edit.selectionEnd);
    body.dispatchEvent(new Event("input", { bubbles: true }));
    resize();
  }
  const linkForm = dialog.querySelector<HTMLFormElement>("[data-link-form]")!;
  let linkRange = { from: 0, to: 0 };
  let restoreBodyFocus = false;
  const linkField = (name: string) => linkForm.elements.namedItem(name) as HTMLInputElement;
  const linkError = dialog.querySelector<HTMLElement>("[data-link-error]")!;
  function openLink(): void {
    linkRange = { from: body.selectionStart, to: body.selectionEnd };
    linkForm.reset();
    linkError.hidden = true;
    linkField("label").value = body.value.slice(linkRange.from, linkRange.to).slice(0, 500);
    restoreBodyFocus = true;
    dialog.showModal();
    linkField(linkRange.from === linkRange.to ? "label" : "url").focus();
  }
  dialog.querySelectorAll("[data-link-cancel]").forEach(button => button.addEventListener("click", () => dialog.close()));
  dialog.addEventListener("close", () => {
    if (restoreBodyFocus) { body.focus({ preventScroll: true }); body.setSelectionRange(linkRange.from, linkRange.to); }
  });
  linkForm.addEventListener("submit", event => {
    event.preventDefault();
    try {
      const text = markdownLink(linkField("label").value, linkField("url").value);
      restoreBodyFocus = false;
      dialog.close();
      applyEdit({ ...linkRange, text, selectionStart: linkRange.from + text.length, selectionEnd: linkRange.from + text.length });
    } catch (error) {
      linkError.textContent = error instanceof Error ? error.message : "请检查网址。";
      linkError.hidden = false;
      linkField("url").focus();
    }
  });
  form.querySelectorAll<HTMLButtonElement>("[data-markdown]").forEach(button => {
    // 工具按钮不抢走原生选区和输入焦点，避免每次排版都收起再弹出键盘。
    button.addEventListener("pointerdown", event => { if (document.activeElement === body) event.preventDefault(); });
    button.addEventListener("click", () => {
      if (body.readOnly || body.disabled || composing) return;
      if (button.dataset.markdown === "link") openLink();
      else applyEdit(formatMarkdown(body.value, body.selectionStart, body.selectionEnd, button.dataset.markdown as MarkdownAction));
    });
  });
  form.querySelector("[data-dismiss-keyboard]")?.addEventListener("click", () => {
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
  });
  body.addEventListener("keydown", event => {
    if (!composing && !event.isComposing && (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "b") {
      event.preventDefault(); applyEdit(formatMarkdown(body.value, body.selectionStart, body.selectionEnd, "bold"));
    }
  });
  return { show, resize };
}
