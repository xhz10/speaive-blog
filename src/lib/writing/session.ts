import { writingRequest } from "./client";
import type { WritingAccount } from "./types";

/** 多个标签页共享会话；重新登录到其他账号时，不能把旧编辑器的文字提交到新账号。 */
export async function ensureWritingIdentity(expectedUsername: string): Promise<void> {
  const account = await writingRequest<WritingAccount>("/settings");
  if (account.username !== expectedUsername) {
    throw new Error(`当前登录账号已改变。请在新窗口重新登录 @${expectedUsername}，然后回到这里继续操作。当前输入仍保留在本页。`);
  }
}
