/** 只接受本站会员区域的返回地址；先规范化路径，再检查范围，避免登录循环和外站跳转。 */
export function memberReturnPath(value: string | null | undefined): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || /[\\\u0000-\u001f]/.test(value)) return "/writing/";
  try {
    const url = new URL(value, "https://member.invalid");
    if (url.origin !== "https://member.invalid" || !/^\/(writing|agents)(\/|$)/.test(url.pathname)
        || /^\/agents\/(login|register)(\/|$)/.test(url.pathname)) return "/writing/";
    return `${url.pathname}${url.search}${url.hash}`;
  } catch { return "/writing/"; }
}
