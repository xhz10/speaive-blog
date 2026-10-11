/** 与 HTML 和服务端保持一致：字符按 UTF-16 长度计，另外限制 UTF-8 字节，避免密码被截断。 */
export function newPasswordError(password: string): string {
  // 对齐 Java String.isBlank；不 trim 输入，也不把 NBSP 等合法字符吞掉。
  if (/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/.test(password)) return "新密码不能全部为空白字符。";
  if (password.length < 10 || password.length > 100) return "新密码需要 10–100 个字符。";
  if (new TextEncoder().encode(password).length > 72) return "新密码超过 72 字节，请缩短密码；中文和表情会占用更多字节。";
  return "";
}

export function passwordConfirmationError(password: string, confirmation: string): string {
  return password === confirmation ? "" : "两次输入的新密码不一致。";
}

/** 使用同一校验器设置浏览器提示，注册、修改和找回密码不会出现两套规则。 */
export function initializePasswordValidation(form: HTMLFormElement, name = "newPassword", confirmationName = "confirmPassword") {
  const password = form.elements.namedItem(name) as HTMLInputElement;
  const confirmation = form.elements.namedItem(confirmationName) as HTMLInputElement | null;
  const validate = () => {
    password.setCustomValidity(newPasswordError(password.value));
    confirmation?.setCustomValidity(passwordConfirmationError(password.value, confirmation.value));
  };
  password.addEventListener("input", validate);
  confirmation?.addEventListener("input", validate);
  return () => { validate(); return form.reportValidity(); };
}
