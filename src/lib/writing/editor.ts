import { writingRequest, jsonRequest } from "./client";
import { writingStatus, type WritingPost, type WritingHistory } from "./types";

const form = document.querySelector<HTMLFormElement>("[data-writing-editor]");
if (form) initialize(form);
function initialize(form: HTMLFormElement): void {
  const field = (name: string) => form.elements.namedItem(name) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  const notice = form.querySelector<HTMLElement>("[data-editor-notice]")!;
  const saveState = form.querySelector<HTMLElement>("[data-save-state]")!;
  let slug = form.dataset.slug ?? "";
  let version = form.dataset.version ?? "";
  let busy = false;
  const writable = !field("title").hasAttribute("readonly");
  const message = (text: string) => { notice.textContent = text; notice.hidden = false; };
  const count = () => { form.querySelector<HTMLElement>("[data-word-count]")!.textContent = `${field("body").value.replace(/\s/g, "").length.toLocaleString("zh-CN")} 字`; };
  const dirty = (value: boolean) => { form.dataset.dirty = String(value); if (value) saveState.textContent = "有未保存的修改"; };
  count();
  form.addEventListener("input", () => { dirty(true); count(); });
  form.addEventListener("change", () => dirty(true));
  window.addEventListener("beforeunload", (event) => { if (form.dataset.dirty === "true") { event.preventDefault(); } });
  window.addEventListener("keydown", (event) => { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") { event.preventDefault(); if (writable) form.requestSubmit(); } });
  function apply(post: WritingPost, replaceFields = false): void {
    slug = post.slug; version = post.version; form.dataset.slug = slug; form.dataset.version = version; form.dataset.status = post.status;
    if (replaceFields) { field("title").value = post.title; field("body").value = post.body; field("description").value = post.description; field("tags").value = post.tags.join("，"); field("visibility").value = post.visibility; }
    dirty(false); count(); saveState.textContent = `${writingStatus(post)} · 已保存`;
    form.querySelector<HTMLElement>("[data-preview-title]")!.textContent = post.title;
    // html 来自后端 Markdown 清洗器；作者输入始终通过 textContent 或表单值显示。
    form.querySelector<HTMLElement>("[data-preview-html]")!.innerHTML = post.html;
    form.querySelector<HTMLElement>("[data-existing-tools]")!.hidden = false;
    form.querySelector<HTMLElement>("[data-unpublish]")!.hidden = post.status !== "PUBLISHED";
    const link = form.querySelector<HTMLAnchorElement>("[data-public-link]")!;
    link.href = `/profile/${encodeURIComponent(form.dataset.username ?? "")}/${slug}/`;
    link.hidden = writingStatus(post) !== "已公开";
    window.history.replaceState(null, "", `/writing/${slug}/`);
    const history = form.querySelector<HTMLDetailsElement>("[data-history]")!;
    history.open = false;
  }
  async function save(): Promise<WritingPost> {
    if (!form.reportValidity()) throw new Error("请先填写文章标题，并检查字段长度。");
    const tags = field("tags").value.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean);
    if (tags.length > 20 || tags.some((tag) => tag.length > 40)) throw new Error("最多 20 个标签，每个标签不超过 40 个字符。");
    const post = await writingRequest<WritingPost>(slug ? `/posts/${slug}` : "/posts", jsonRequest(slug ? "PUT" : "POST", {
      title: field("title").value, description: field("description").value, body: field("body").value,
      tags, visibility: field("visibility").value, version
    }));
    apply(post); return post;
  }
  async function run(action: () => Promise<void>): Promise<void> {
    if (busy) return;
    busy = true; form.setAttribute("aria-busy", "true");
    const controls = [...form.querySelectorAll<HTMLButtonElement | HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>("button,input,textarea,select")];
    const previous = controls.map((control) => control.disabled);
    // 请求期间冻结输入，避免返回的版本号把请求发出后的新输入误标成已保存。
    controls.forEach((control) => { control.disabled = true; });
    try { await action(); }
    catch (error) { message(error instanceof Error ? error.message : "操作失败，文字尚未保存，请重试。"); }
    finally { controls.forEach((control, index) => { control.disabled = previous[index]!; }); busy = false; form.removeAttribute("aria-busy"); }
  }
  form.addEventListener("submit", (event) => { event.preventDefault(); if (writable) void run(async () => { await save(); message("已保存。"); }); });
  form.querySelector("[data-publish]")?.addEventListener("click", () => {
    if (!confirm("发布后，所有人都可以在个人主页阅读这篇文章。确定公开发布吗？")) return;
    void run(async () => { field("visibility").value = "PUBLIC"; dirty(true); await save(); apply(await writingRequest<WritingPost>(`/posts/${slug}/publish`, jsonRequest("POST", { version }))); message("文章已公开，读者现在可以在你的个人主页看到它。"); });
  });
  form.querySelector("[data-unpublish]")?.addEventListener("click", () => {
    void run(async () => {
      // 撤回不要求先保存：撤销发布资格的作者仍能关闭公开入口，未保存输入保持在表单中。
      const unsaved = form.dataset.dirty === "true";
      apply(await writingRequest<WritingPost>(`/posts/${slug}/unpublish`, jsonRequest("POST", { version })));
      if (unsaved) dirty(true);
      message(unsaved ? "已撤回，表单中还有未保存的修改。" : "已撤回，文章现在仅自己可见。");
    });
  });
  form.querySelector("[data-archive]")?.addEventListener("click", () => {
    if (!confirm("归档后，文章会从列表与个人主页移除。未保存的修改不会保留，历史版本仍存储在数据库中。确定归档吗？")) return;
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
    if (!slug || form.dataset.dirty === "true") { message("请先保存文章，再预览已保存的内容。"); return; }
    preview(true);
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
        button.addEventListener("click", () => {
          if (!confirm(`恢复版本 ${revision.revision}？当前未保存的输入会被替换，已公开的文章会同步更新。`)) return;
          void run(async () => { apply(await writingRequest<WritingPost>(`/posts/${slug}/restore`, jsonRequest("POST", { revision: revision.revision, version })), true); message("已恢复为一个新版本。"); });
        });
        list.append(button);
      }
    } catch (error) { list.textContent = error instanceof Error ? error.message : "历史版本读取失败"; }
  });
}
