import type { StudioPostDetail, StudioPostSummary } from "../studio/types";

/** 管理员授予的资格和作者选择的存储偏好是两件事，不能在前端合并判断。 */
export interface WritingAccount {
  id: string; username: string; displayName: string;
  role: "READER" | "WRITER";
  canPublish: boolean; encryptionAllowed: boolean; contentEncrypted: boolean;
  encryptionAvailable: boolean; version: number;
}
export interface WritingPosts {
  username: string; displayName: string; total: number; page: number; pageSize: number;
  items: StudioPostSummary[];
}
export type WritingPost = StudioPostDetail;
export interface WritingHistory { items: { revision: number; title: string; updatedAt: string; status: string }[]; }
export const pageNumber = (value: string | null): number => {
  const page = Number(value ?? 1);
  return Number.isInteger(page) && page >= 1 && page <= 100000 ? page : 1;
};
export const writingStatus = (post: { status: string; visibility: string }): string =>
  post.status === "PUBLISHED" && post.visibility === "PUBLIC" ? "已公开" : "仅自己可见";
export const writingDate = (value: string): string => new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai", year: "numeric", month: "short", day: "numeric"
}).format(new Date(value));
