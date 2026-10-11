import { changeMemberPassword } from "./password-client";
import { initializePasswordValidation } from "./password";
import { ensureWritingIdentity } from "./session";

export function initializePasswordChange(form: HTMLFormElement | null): void {
  if (!form) return;
  const validate = initializePasswordValidation(form);
  const button = form.querySelector<HTMLButtonElement>("button[type=submit]")!;
  const notice = form.querySelector<HTMLElement>("[data-password-notice]")!;
  let busy = false;
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (busy || !validate()) return;
    const data = new FormData(form);
    const username = form.dataset.username!;
    busy = true; button.disabled = true;
    notice.hidden = false; notice.dataset.kind = "info"; notice.textContent = "正在更改密码…";
    try {
      await ensureWritingIdentity(username);
      await changeMemberPassword(username, String(data.get("currentPassword") ?? ""), String(data.get("newPassword") ?? ""));
      // 只清空本表单；存储设置中尚未保存的选择仍保留在原处。
      form.reset();
      for (const input of form.querySelectorAll<HTMLInputElement>("input")) { input.value = ""; input.disabled = true; }
      notice.dataset.kind = "success";
      notice.textContent = "密码已更改，已有登录已失效。请使用新密码重新登录；本页的其他设置输入仍保留。";
      document.querySelector<HTMLElement>("[data-password-login]")!.hidden = false;
    } catch (error) {
      notice.dataset.kind = "error";
      notice.textContent = error instanceof Error ? error.message : "密码未更改，请稍后重试。";
      button.disabled = false;
    } finally { busy = false; }
  });
}
