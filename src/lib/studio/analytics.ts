/** 管理员访客统计响应；不导入公开页面数据模型。 */
export interface VisitCount { key: string; label: string; count: number }
export interface ArticleVisit {
  id: string; postId: string; postSlug: string; postTitle: string; visitedAt: string;
  ip: string; deviceType: string; deviceModel: string; operatingSystem: string;
  browser: string; location: string; referrerHost: string;
}
export interface VisitAnalytics {
  pageViews: number; visitors: number; articles: number; page: number; pageSize: number; retentionDays: number;
  items: ArticleVisit[]; daily: VisitCount[]; devices: VisitCount[]; locations: VisitCount[]; topArticles: VisitCount[];
}
export const deviceLabels: Record<string, string> = {
  MOBILE: "手机", TABLET: "平板", DESKTOP: "电脑", BOT: "机器人", UNKNOWN: "未识别"
};
