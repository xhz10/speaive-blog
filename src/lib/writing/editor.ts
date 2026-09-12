import { ensureWritingIdentity } from "./session";
import { confirmWritingAction } from "./confirmation";
import { writingRequest, jsonRequest as buildJsonRequest } from "./client";
import { writingStatus, type WritingPost, type WritingHistory } from "./types";

const form = document.querySelector<HTMLFormElement>("[data-writing-editor]");
if (form) initialize(form);
function initialize(form: HTMLFormElement): void {
  const jsonRequest = (method: string, body: unknown) => buildJsonRequest(method, body, form.dataset.username);
  const field = (name: string) => form.elements.namedItem(name) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  const notice = form.querySelector<HTMLElement>("[data-editor-notice]")!;
  const saveState = form.querySelector<HTMLElement>("[data-save-state]")!;
  let slug = form.dataset.slug ?? "";
  let version = form.dataset.version ?? "";
  let busy = false;
  let cover: string | null = form.dataset.cover || null;
  let publishedAt: string | null = form.dataset.publishedAt || null;
  const writable = !field("title").hasAttribute("readonly");
  const message = (text: string, kind: "info" | "success" | "error" = "info") => { notice.textContent = text; notice.hidden = false; notice.dataset.kind = kind; notice.setAttribute("role", kind === "error" ? "alert" : "status"); };
  const count = () => { form.querySelector<HTMLElement>("[data-word-count]")!.textContent = `${field("body").value.replace(/\s/g, "").length.toLocaleString("zh-CN")} 字`; };
  const dirty = (value: boolean) => { form.dataset.dirty = String(value); if (value) saveState.textContent = "有未保存的修改";
    form.querySelector<HTMLElement>("[data-save-label]")!.textContent = form.dataset.status === "PUBLISHED" && field("visibility").value === "PUBLIC" ? "更新公开文章" : "保存"; };
  count();
  form.addEventListener("input", () => { dirty(true); count(); });
  form.addEventListener("change", () => dirty(true));
  window.addEventListener("beforeunload", (event) => { if (form.dataset.dirty === "true") { event.preventDefault(); } });
  window.addEventListener("keydown", (event) => { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") { event.preventDefault(); if (writable) form.requestSubmit(); } });
  function apply(post: WritingPost, replaceFields = false): void {
    document.querySelector("[data-recovered-note]")?.remove();
    slug = post.slug; version = post.version; form.dataset.slug = slug; form.dataset.version = version; form.dataset.status = post.status;
    if (replaceFields) { field("title").value = post.title; field("body").value = post.body; field("description").value = post.description; field("tags").value = post.tags.join("，"); field("visibility").value = post.visibility; }
    cover = post.cover; publishedAt = post.publishedAt;
    const publishButton = form.querySelector<HTMLElement>("[data-publish]");
    if (publishButton) publishButton.hidden = writingStatus(post) === "已公开";
    form.querySelector<HTMLElement>("[data-preview-note]")!.hidden = true;
    dirty(false); count(); saveState.textContent = `${writingStatus(post)} · 已保存`;
    form.querySelector<HTMLElement>("[data-preview-title]")!.textContent = post.title;
    // html 来自后端 Markdown 清洗器；作者输入始终通过 textContent 或表单值显示。
    form.querySelector<HTMLElement>("[data-preview-html]")!.innerHTML = post.html;
    form.querySelector<HTMLElement>("[data-existing-tools]")!.hidden = false;
    form.querySelector<HTMLElement>("[data-unpublish]")!.hidden = post.status !== "PUBLISHED";
    const link = form.querySelector<HTMLAnchorElement>("[data-public-link]")!;
    link.href = `/profile/${encodeURIComponent(form.dataset.username ?? "")}/${slug}/`;
    link.hidden = writingStatus(post) !== "已公开";
    const latest = form.querySelector<HTMLAnchorElement>("[data-latest-version]")!;
    latest.href = `/writing/${slug}/`; latest.hidden = false;
    window.history.replaceState(null, "", `/writing/${slug}/`);
    const history = form.querySelector<HTMLDetailsElement>("[data-history]")!;
    history.open = false;
  }
  function validate(): boolean {
    // 必须先校验再禁用控件；disabled 控件不参与浏览器约束校验。
    preview(false);
    const title = field("title");
    title.setCustomValidity(title.value.trim() ? "" : "请给文章起一个标题。");
    const tags = field("tags").value.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean);
    field("tags").setCustomValidity(tags.length > 20 || tags.some((tag) => tag.length > 40) ? "最多 20 个标签，每个不超过 40 个字符。" : "");
    return form.reportValidity();
  }
  async function save(): Promise<WritingPost> {
    const tags = field("tags").value.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean);
    if (tags.length > 20 || tags.some((tag) => tag.length > 40)) throw new Error("最多 20 个标签，每个标签不超过 40 个字符。");
    const post = await writingRequest<WritingPost>(slug ? `/posts/${slug}` : "/posts", jsonRequest(slug ? "PUT" : "POST", {
      title: field("title").value, description: field("description").value, body: field("body").value,
      tags, cover, publishedAt, visibility: field("visibility").value, version
    }));
    apply(post); return post;
  }
  async function run(action: () => Promise<void>, requireValid = false, progress = "正在保存…"): Promise<void> {
    if (busy || (requireValid && !validate())) return;
    const trigger = document.activeElement;
    message(progress);
    busy = true; form.setAttribute("aria-busy", "true");
    const controls = [...form.querySelectorAll<HTMLButtonElement | HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>("button,input,textarea,select")];
    const previous = controls.map((control) => control.disabled);
    // 请求期间冻结输入，避免返回的版本号把请求发出后的新输入误标成已保存。
    controls.forEach((control) => { control.disabled = true; });
    try { await ensureWritingIdentity(form.dataset.username!); await action(); form.querySelector<HTMLElement>("[data-editor-recovery]")!.hidden = true; }
    catch (error) {
      message(error instanceof TypeError ? "连接暂时中断，文字尚未保存。请检查网络后重试。" : error instanceof Error ? error.message : "操作失败，文字尚未保存，请重试。", "error");
      form.querySelector<HTMLElement>("[data-editor-recovery]")!.hidden = false;
    }
    finally { controls.forEach((control, index) => { control.disabled = previous[index]!; }); busy = false; form.removeAttribute("aria-busy"); if (trigger instanceof HTMLElement && trigger.isConnected && document.activeElement === document.body) trigger.focus(); }
  }
  form.addEventListener("submit", (event) => { event.preventDefault(); if (writable) void run(async () => { await save(); message("已保存。", "success"); }, true); });
  form.querySelector("[data-publish]")?.addEventListener("click", async () => {
    if (busy || !validate()) return;
    if (!await confirmWritingAction("让这篇文字与读者见面？", "发布后，所有人都可以通过你的个人主页和“朋友的文字”阅读、复制和分享这篇文章。加密存储不会改变公开阅读权限。", "确认公开发布")) return;
    void run(async () => { field("visibility").value = "PUBLIC"; dirty(true); await save(); apply(await writingRequest<WritingPost>(`/posts/${slug}/publish`, jsonRequest("POST", { version }))); message("文章已公开，读者现在可以在你的个人主页和“朋友的文字”看到它。", "success"); }, true, "正在保存并发布…");
  });
  form.querySelector("[data-unpublish]")?.addEventListener("click", () => {
    void run(async () => {
      // 撤回不要求先保存：撤销发布资格的作者仍能关闭公开入口，未保存输入保持在表单中。
      const unsaved = form.dataset.dirty === "true";
      apply(await writingRequest<WritingPost>(`/posts/${slug}/unpublish`, jsonRequest("POST", { version })));
      if (unsaved) dirty(true);
      message(unsaved ? "已撤回，表单中还有未保存的修改。" : "已撤回，文章现在仅自己可见。", "success");
    });
  });
  form.querySelector("[data-archive]")?.addEventListener("click", async () => {
    if (busy || !await confirmWritingAction("归档这篇文章？", "文章将从公开入口移除，可以在“我的文章 → 归档”中找回。未保存的修改不会进入归档，历史记录仍按原存储设置保留。", "确认归档", true)) return;
    void run(async () => { await writingRequest(`/posts/${slug}/archive`, jsonRequest("POST", { version })); dirty(false); window.location.assign("/writing/"); });
  });
  function preview(show: boolean): void {
    form.querySelector<HTMLElement>("[data-edit-pane]")!.hidden = show;
    form.querySelector<HTMLElement>("[data-preview-pane]")!.hidden = !show;
    form.querySelector("[data-edit-tab]")!.setAttribute("aria-pressed", String(!show));
    form.querySelector("[data-preview-tab]")!.setAttribute("aria-pressed", String(show));
  }
  form.querySelector("[data-edit-tab]")?.addEventListener("click", () => preview(false));
  form.querySelector("[data-preview-tab]")?.addEventListener("click", () => {
    if (!writable) { preview(true); return; }
    void run(async () => {
      const result = await writingRequest<{ title: string; html: string }>("/preview", jsonRequest("POST", {
        title: field("title").value, body: field("body").value
      }));
      form.querySelector<HTMLElement>("[data-preview-title]")!.textContent = result.title || "无标题草稿";
      form.querySelector<HTMLElement>("[data-preview-html]")!.innerHTML = result.html;
      form.querySelector<HTMLElement>("[data-preview-note]")!.hidden = form.dataset.dirty !== "true";
      preview(true);
      message("这是当前文字的预览，没有保存或发布文章。");
    }, false, "正在生成预览…");
  });
  form.querySelector<HTMLDetailsElement>("[data-history]")?.addEventListener("toggle", async (event) => {
    if (!(event.currentTarget as HTMLDetailsElement).open || !slug) return;
    const list = form.querySelector<HTMLElement>("[data-history-list]")!;
    list.textContent = "正在读取历史版本…";
    try {
      const result = await writingRequest<WritingHistory>(`/posts/${slug}/history`);
      list.replaceChildren();
      for (const revision of result.items) {
        const button = document.createElement("button"); button.type = "button"; button.disabled = !writable;
        button.textContent = `版本 ${revision.revision} · ${revision.title}`;
        button.addEventListener("click", async () => {
          if (busy || !await confirmWritingAction(`恢复到版本 ${revision.revision}？`, "当前未保存的输入会被替换，已公开的文章会同步更新。系统会保留原有历史，并创建一个新版本。", "恢复这个版本")) return;
          void run(async () => { apply(await writingRequest<WritingPost>(`/posts/${slug}/restore`, jsonRequest("POST", { revision: revision.revision, version })), true); message("已恢复为一个新版本。", "success"); });
        });
        list.append(button);
      }
    } catch (error) { list.textContent = error instanceof Error ? error.message : "历史版本读取失败"; }
  });
}
