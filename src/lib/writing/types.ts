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
/** 所有分页请求都由后端按此范围筛选，不能只过滤当前页。 */
export const writingFilters = [
  { value: "ALL", label: "全部文章" }, { value: "PRIVATE", label: "仅自己可见" },
  { value: "PUBLIC", label: "已公开" }, { value: "ARCHIVED", label: "归档" }
] as const;
export type WritingFilter = typeof writingFilters[number]["value"];
export const writingFilter = (value: string | null): WritingFilter =>
  writingFilters.find((filter) => filter.value === value)?.value ?? "ALL";
export interface CommunityPosts { total: number; page: number; pageSize: number; items: StudioPostSummary[]; }
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
