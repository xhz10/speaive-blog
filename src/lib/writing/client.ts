import { studioRequest, StudioClientError } from "../studio/client";

/** 使用现有 CSRF 与会话协议，会员登录失效时回到会员入口，避免误跳管理员登录。 */
export async function writingRequest<T = void>(path: string, init: RequestInit = {}): Promise<T> {
  try { return await studioRequest<T>(`/account/writing${path}`, init, { redirectOnUnauthorized: false }); }
  catch (error) {
    if (error instanceof StudioClientError && error.status === 401) {
      window.location.assign(`/agents/login/?next=${encodeURIComponent(window.location.pathname)}`);
    }
    throw error;
  }
}
export const jsonRequest = (method: string, body: unknown): RequestInit => ({
  method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
});
