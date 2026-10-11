import { studioRequest } from "./client";
import { initializeStudioConfirmation } from "./confirmation";
import { jsonRequest } from "../writing/client";
import { createMemberPasswordReset, memberPasswordResetUrl } from "../writing/password-client";
import type { WritingAccount } from "../writing/types";

export function initializeMemberManagement(): void {
  const dialog = document.querySelector<HTMLDialogElement>("[data-account-confirm]")!;
  const confirm = initializeStudioConfirmation(dialog);
  for (const form of document.querySelectorAll<HTMLFormElement>("[data-member-permissions]")) {
    const username = form.dataset.username!;
    const role = form.elements.namedItem("role") as HTMLSelectElement;
    const publish = form.elements.namedItem("canPublish") as HTMLInputElement;
    const encryption = form.elements.namedItem("encryptionAllowed") as HTMLInputElement;
    const save = form.querySelector<HTMLButtonElement>("button[type=submit]")!;
    const permissionNotice = form.querySelector<HTMLElement>("[data-permissions-notice]")!;
    role.addEventListener("change", () => { publish.disabled = role.value !== "WRITER"; if (publish.disabled) publish.checked = false; });
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (save.disabled) return;
      save.disabled = true; permissionNotice.hidden = false; permissionNotice.dataset.kind = "info"; permissionNotice.textContent = "正在保存…";
      try {
        const updated = await studioRequest<WritingAccount>(`/studio/members/${encodeURIComponent(username)}/permissions`, jsonRequest("PUT", {
          role: role.value, canPublish: publish.checked, encryptionAllowed: encryption.checked, version: Number(form.dataset.version)
        }));
        form.dataset.version = String(updated.version); permissionNotice.dataset.kind = "success"; permissionNotice.textContent = "权限已保存，下次操作立即生效。";
      } catch (error) { permissionNotice.dataset.kind = "error"; permissionNotice.textContent = error instanceof Error ? error.message : "权限未保存，请重试。"; }
      finally { save.disabled = false; }
    });
    const create = form.querySelector<HTMLButtonElement>("[data-create-password-reset]")!;
    const copy = form.querySelector<HTMLButtonElement>("[data-copy-password-reset]")!;
    const dismiss = form.querySelector<HTMLButtonElement>("[data-dismiss-password-reset]")!;
    const result = form.querySelector<HTMLElement>("[data-recovery-result]")!;
    const details = form.querySelector<HTMLElement>("[data-recovery-details]")!;
    const urlText = form.querySelector<HTMLElement>("[data-recovery-url]")!;
    const notice = form.querySelector<HTMLElement>("[data-recovery-notice]")!;
    let url = "";
    let generating = false;
    let copying = false;
    let active = true;
    // 浏览器返回缓存页面时，不能把离开前的在途响应当作新的签发结果展示。
    let pageVersion = 0;
    const show = (message: string, error = false) => {
      notice.textContent = message; notice.hidden = false; notice.dataset.kind = error ? "error" : "success";
    };
    const clear = () => { url = ""; urlText.textContent = ""; result.hidden = true; copy.disabled = true; };
    create.addEventListener("click", async () => {
      if (!active || generating || copying) return;
      const version = pageVersion;
      generating = true;
      try {
        const confirmed = await confirm({
          title: `已确认 @${username} 是账号本人？`,
          message: "请先通过你们熟悉的方式核对身份。新链接会替代旧链接，30 分钟内有效，只能使用一次。生成时不会改变密码或退出已有登录；使用成功后旧登录才失效，加密文章和存储设置不会改变。",
          confirmLabel: "已确认本人，生成链接"
        });
        if (!confirmed || !active || version !== pageVersion) return;
        // 重发可能已在服务器成功而响应丢失，不能继续把旧链接交给会员。
        clear();
        create.disabled = true; show("正在生成找回链接…");
        const reset = await createMemberPasswordReset(username);
        if (!active || version !== pageVersion) return;
        url = memberPasswordResetUrl(window.location.origin, reset.token);
        // 链接使用 textContent 展示，绝不写入 HTML、data 属性或持久存储。
        urlText.textContent = url;
        details.textContent = `账号：@${reset.username} · 到期：${new Date(reset.expiresAt).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false })}（北京时间）`;
        result.hidden = false; copy.disabled = false; copy.textContent = "复制一次";
        show(`已为 @${reset.username} 生成链接，请只交给已确认身份的本人。`);
      } catch (error) { if (active && version === pageVersion) show(error instanceof Error ? error.message : "找回链接未生成，请稍后重试。", true); }
      finally { generating = false; create.disabled = false; }
    });
    copy.addEventListener("click", async () => {
      if (!active || !url || copy.disabled) return;
      const version = pageVersion;
      copying = true; copy.disabled = true; create.disabled = true;
      try {
        await navigator.clipboard.writeText(url);
        if (!active || version !== pageVersion) return;
        clear(); show(`@${username} 的找回链接已复制，并已从本页清除。请只交给本人。`);
      } catch { if (active && version === pageVersion) { copy.disabled = false; show("复制失败，请手动选中链接复制，随后隐藏链接。", true); } }
      finally { copying = false; create.disabled = false; }
    });
    dismiss.addEventListener("click", () => { clear(); show("链接已从本页清除；已生成的链接仍在有效期内可用。需要时可重新生成。" ); });
    window.addEventListener("pagehide", () => { active = false; pageVersion += 1; clear(); });
    window.addEventListener("pageshow", () => { active = true; });
  }
}
