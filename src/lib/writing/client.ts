import { studioRequest, StudioClientError } from "../studio/client";

/** 写作请求不强制跳转：登录过期时保留页面和未保存输入，交由界面提供重新登录入口。 */
export async function writingRequest<T = void>(path: string, init: RequestInit = {}): Promise<T> {
  try { return await studioRequest<T>(`/account/writing${path}`, init, { redirectOnUnauthorized: false }); }
  catch (error) {
    if (error instanceof StudioClientError && error.status === 401) {
      throw new StudioClientError(401, "SESSION_EXPIRED", "登录已过期。请在新窗口重新登录，再回到这里重试。当前输入仍保留在本页。");
    }
    throw error;
  }
}
export const jsonRequest = (method: string, body: unknown, expectedUsername?: string): RequestInit => ({
  method, headers: { "Content-Type": "application/json", ...(expectedUsername ? { "X-Writing-Username": expectedUsername } : {}) }, body: JSON.stringify(body)
});
