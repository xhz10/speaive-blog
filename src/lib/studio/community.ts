import { studioRequest } from "./client";
import { initializeStudioConfirmation } from "./confirmation";
import { invitationCanRevoke, invitationStatus } from "./invitations";
import { jsonRequest } from "../writing/client";
import type { Invitation, StudioAgent } from "./types";

function updateInvitationRow(row: HTMLElement, invite: Invitation): void {
  row.dataset.inviteId = invite.id;
  row.querySelector<HTMLElement>("[data-invite-status]")!.textContent = invitationStatus(invite);
  row.querySelector<HTMLElement>("[data-invite-uses]")!.textContent = `${invite.usedCount} / ${invite.maxUses} 次`;
  row.querySelector<HTMLElement>("[data-invite-expiry]")!.textContent = `到期：${new Date(invite.expiresAt).toLocaleDateString("zh-CN", { timeZone: "Asia/Shanghai" })}`;
  row.querySelector<HTMLElement>("[data-invite-note]")!.textContent = invite.note || "无备注";
  row.querySelector<HTMLButtonElement>("[data-revoke-invite]")!.disabled = !invitationCanRevoke(invite);
}

function createInvitationRow(invite: Invitation): HTMLElement {
  const row = document.createElement("article");
  for (const [tag, key] of [["span", "inviteStatus"], ["strong", "inviteUses"], ["small", "inviteExpiry"], ["p", "inviteNote"]]) {
    const node = document.createElement(tag); node.dataset[key] = "";
    if (key === "inviteNote") node.className = "community-invite-note";
    row.append(node);
  }
  const revoke = document.createElement("button");
  revoke.type = "button"; revoke.className = "button ghost danger"; revoke.dataset.revokeInvite = ""; revoke.textContent = "撤销邀请码";
  const notice = document.createElement("p");
  notice.dataset.inviteRowNotice = ""; notice.setAttribute("role", "status"); notice.setAttribute("aria-live", "polite"); notice.hidden = true;
  row.append(revoke, notice); updateInvitationRow(row, invite);
  return row;
}

export function initializeCommunityManagement(): void {
  const confirm = initializeStudioConfirmation(document.querySelector<HTMLDialogElement>("[data-account-confirm]")!);
  const inviteForm = document.querySelector<HTMLFormElement>("[data-invite-form]")!;
  const inviteNotice = document.querySelector<HTMLElement>("[data-invite-notice]")!;
  const newCode = document.querySelector<HTMLElement>("[data-new-code]")!;
  const codeValue = document.querySelector<HTMLElement>("[data-new-code-value]")!;
  const history = document.querySelector<HTMLElement>("[data-invite-history]")!;
  const reviewNotice = document.querySelector<HTMLElement>("[data-review-notice]")!;
  const submit = inviteForm.querySelector<HTMLButtonElement>("button[type=submit]")!;
  let shownCodeInvitationId = "";
  let creating = false;
  let active = true;
  // 返回缓存页面可以重新创建，但旧请求的明文不能重新出现。
  let pageVersion = 0;
  const show = (node: HTMLElement, message: string, error: boolean) => {
    node.textContent = message; node.className = `notice${error ? " error" : " success"}`; node.hidden = false;
  };
  inviteForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (!active || creating || !inviteForm.reportValidity()) return;
    const version = pageVersion;
    const data = new FormData(inviteForm);
    creating = true; submit.disabled = true;
    try {
      const invite = await studioRequest<Invitation>("/studio/invitations", jsonRequest("POST", {
        validDays: Number(data.get("validDays")), maxUses: Number(data.get("maxUses")), note: String(data.get("note") ?? "").trim() || null
      }));
      if (!active || version !== pageVersion) return;
      codeValue.textContent = invite.code ?? "";
      newCode.hidden = !invite.code; shownCodeInvitationId = invite.id;
      history.querySelector("[data-invite-empty]")?.remove(); history.prepend(createInvitationRow(invite));
      show(inviteNotice, "邀请码已创建。明文不会再次显示，请现在复制。", false);
    } catch (error) { if (active && version === pageVersion) show(inviteNotice, error instanceof Error ? error.message : "邀请码创建失败，请重试。", true); }
    finally { creating = false; submit.disabled = false; }
  });
  document.querySelector<HTMLButtonElement>("[data-copy-code]")?.addEventListener("click", async () => {
    if (!codeValue.textContent) return;
    try { await navigator.clipboard.writeText(codeValue.textContent); show(inviteNotice, "邀请码已复制。", false); }
    catch { show(inviteNotice, "复制失败，请手动选中邀请码复制。", true); }
  });
  window.addEventListener("pagehide", () => { active = false; pageVersion += 1; codeValue.textContent = ""; newCode.hidden = true; });
  window.addEventListener("pageshow", () => { active = true; });
  history.addEventListener("click", async event => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const button = target.closest<HTMLButtonElement>("[data-revoke-invite]");
    if (!button || button.disabled) return;
    const row = button.closest<HTMLElement>("[data-invite-id]")!;
    const notice = row.querySelector<HTMLElement>("[data-invite-row-notice]")!;
    button.disabled = true;
    try {
      if (!await confirm({ title: "撤销这枚邀请码？", message: "撤销后，这枚邀请码将不能再用于注册。已经注册的会员账号不受影响。", confirmLabel: "撤销邀请码" })) { button.disabled = false; return; }
      notice.hidden = false; notice.textContent = "正在撤销…";
      const saved = await studioRequest<Invitation>(`/studio/invitations/${encodeURIComponent(row.dataset.inviteId!)}/revoke`, jsonRequest("POST", {}));
      updateInvitationRow(row, saved); notice.textContent = "邀请码已撤销，已注册的会员不受影响。";
      if (shownCodeInvitationId === saved.id) { codeValue.textContent = ""; newCode.hidden = true; }
    } catch (error) { button.disabled = false; notice.hidden = false; notice.textContent = error instanceof Error ? error.message : "邀请码未撤销，请重试。"; }
  });
  document.querySelector("[data-review-list]")?.addEventListener("click", async event => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const approve = target.closest<HTMLButtonElement>("[data-review-approve]");
    const reject = target.closest<HTMLButtonElement>("[data-review-reject]");
    const button = approve ?? reject;
    if (!button || button.disabled) return;
    let note: string | null = null;
    if (reject) { note = window.prompt("告诉对方需要修改什么（必填，最多 500 字）：")?.trim() ?? null; if (!note) return; }
    button.disabled = true;
    try {
      const saved = await studioRequest<StudioAgent>(`/studio/agents/${encodeURIComponent(button.dataset.agentId ?? "")}/${approve ? "approve" : "reject"}`, jsonRequest("POST", { version: Number(button.dataset.version), note }));
      show(reviewNotice, saved.reviewStatus === "APPROVED" ? "Agent 已审核通过，可以参与评论。" : "已退回修改。", false);
      window.setTimeout(() => window.location.reload(), 450);
    } catch (error) { button.disabled = false; show(reviewNotice, error instanceof Error ? error.message : "审核失败", true); }
  });
}
