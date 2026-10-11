import { StudioClientError } from "../studio/client";
import { initializePasswordValidation } from "./password";
import { passwordResetTokenFromFragment, resetMemberPassword } from "./password-client";

declare global { interface Window { __takeMemberPasswordResetFragment?: () => string; } }

export function initializePasswordReset(form: HTMLFormElement | null): void {
  let fragment = window.__takeMemberPasswordResetFragment?.() ?? "";
  let token = passwordResetTokenFromFragment(fragment);
  fragment = "";
  if (!form) return;
  const fields = form.querySelector<HTMLFieldSetElement>("[data-reset-fields]")!;
  const notice = form.querySelector<HTMLElement>("[data-reset-notice]")!;
  const login = form.querySelector<HTMLElement>("[data-reset-login]")!;
  const validate = initializePasswordValidation(form);
  let busy = false;
  let active = true;
  const show = (message: string, kind: "error" | "success" | "info") => {
    notice.textContent = message; notice.className = `notice ${kind === "info" ? "" : kind}`;
  };
  if (token) { fields.disabled = false; show("请输入两次新密码，保存后使用新密码登录。", "info"); }
  else show("找回链接缺失或格式无效。请联系站长确认本人身份，并获取新的完整链接。", "error");
  window.addEventListener("pagehide", () => {
    active = false;
    token = null; form.reset(); fields.disabled = true;
    show("本页的找回链接已清除。需要继续时，请重新打开站长发来的完整链接。", "info");
  }, { once: true });
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!token || busy || !validate()) return;
    const data = new FormData(form);
    busy = true; fields.disabled = true; show("正在保存新密码…", "info");
    try {
      await resetMemberPassword(token, String(data.get("newPassword") ?? ""));
      if (!active) return;
      token = null; form.reset();
      for (const input of form.querySelectorAll<HTMLInputElement>("input")) input.value = "";
      fields.hidden = true; login.hidden = false;
      show("密码已更新，已有登录已失效。请使用新密码登录。", "success");
    } catch (error) {
      if (!active) return;
      if (error instanceof StudioClientError && error.code === "INVALID_REQUEST" && /链接/.test(error.message)) {
        token = null; form.reset(); fields.hidden = true;
        show("找回链接无效、已使用或已过期。请联系站长重新确认身份并生成链接。", "error");
      } else {
        fields.disabled = false;
        show(error instanceof Error ? error.message : "密码未更新，请稍后重试。", "error");
      }
    } finally { busy = false; }
  });
}
