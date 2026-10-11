import { resetCsrfToken, studioRequest, StudioClientError } from "../studio/client";
import type { MemberPasswordReset } from "../studio/types";
import { jsonRequest } from "./client";

export async function changeMemberPassword(username: string, currentPassword: string, newPassword: string): Promise<void> {
  try {
    await studioRequest("/account/password/change", jsonRequest("POST", { currentPassword, newPassword }, username), { redirectOnUnauthorized: false });
    resetCsrfToken();
  } catch (error) {
    if (error instanceof StudioClientError && error.status === 401 && error.code !== "INVALID_CREDENTIALS") {
      throw new Error("登录已过期，请重新登录后再修改密码。当前输入仍保留在本页。");
    }
    throw error;
  }
}

export async function resetMemberPassword(token: string, newPassword: string): Promise<void> {
  await studioRequest("/account/password/reset", jsonRequest("POST", { token, newPassword }), { redirectOnUnauthorized: false });
  resetCsrfToken();
}

export function createMemberPasswordReset(username: string): Promise<MemberPasswordReset> {
  return studioRequest(`/studio/members/${encodeURIComponent(username)}/password-reset`, jsonRequest("POST", {}));
}

/** 令牌只从片段读取，不能接受 query 中的凭据。重复 token 或异常格式一律拒绝。 */
export function passwordResetTokenFromFragment(fragment: string): string | null {
  if (!fragment.startsWith("#")) return null;
  const params = new URLSearchParams(fragment.slice(1));
  const tokens = params.getAll("token");
  return tokens.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(tokens[0]) ? tokens[0] : null;
}

export function memberPasswordResetUrl(origin: string, token: string): string {
  const url = new URL("/agents/reset-password/", origin);
  url.hash = new URLSearchParams({ token }).toString();
  return url.href;
}
