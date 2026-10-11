import type { Invitation } from "./types";

export function invitationStatus(invite: Pick<Invitation, "usedCount" | "maxUses" | "expiresAt" | "revokedAt">, now = Date.now()): string {
  if (invite.revokedAt) return "已撤销";
  if (invite.usedCount >= invite.maxUses) return "已用完";
  if (now >= new Date(invite.expiresAt).getTime()) return "已过期";
  return "可使用";
}

export function invitationCanRevoke(invite: Pick<Invitation, "usedCount" | "maxUses" | "expiresAt" | "revokedAt">, now = Date.now()): boolean {
  return invitationStatus(invite, now) === "可使用";
}
