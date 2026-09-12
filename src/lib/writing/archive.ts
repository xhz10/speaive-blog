import { confirmWritingAction } from "./confirmation";
import { writingRequest, jsonRequest } from "./client";
import { ensureWritingIdentity } from "./session";
import type { WritingPost } from "./types";

let recovering = false;
for (const button of document.querySelectorAll<HTMLButtonElement>("[data-recover-slug]")) {
  button.addEventListener("click", async () => {
    if (recovering || !await confirmWritingAction("找回这篇文章？", `“${button.dataset.recoverTitle}”将回到文章列表，作为仅自己可见的草稿。原有内容、历史和存储设置会保留。`, "找回为私密草稿")) return;
    const notice = document.querySelector<HTMLElement>("[data-list-notice]")!;
    recovering = true; button.disabled = true; notice.hidden = false; notice.textContent = "正在找回文章…";
    try {
      await ensureWritingIdentity(button.dataset.username!);
      const post = await writingRequest<WritingPost>(`/posts/${encodeURIComponent(button.dataset.recoverSlug!)}/recover`, jsonRequest("POST", { version: button.dataset.recoverVersion }, button.dataset.username));
      window.location.assign(`/writing/${encodeURIComponent(post.slug)}/?recovered=1`);
    } catch (error) {
      notice.textContent = error instanceof Error ? error.message : "暂时未能找回文章，请重试。";
      notice.dataset.kind = "error"; notice.setAttribute("role", "alert"); notice.scrollIntoView({ block: "center" });
      button.disabled = false;
    } finally { recovering = false; }
  });
}
