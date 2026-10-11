import {
  getPublishedDiscussionDigest, getPublishedNovelFragment, getPublishedPost,
  getPublishedWorkNavigation, listPublishedComments
} from "./public-api";

/** 正文决定页面是否可读；评论、摘要与连续阅读导航分别降级，不把故障当成空内容。 */
export async function loadArticlePage(slug: string, workSlug?: string) {
  let post;
  try { post = await getPublishedPost(slug); }
  catch { return { state: "unavailable" as const }; }
  if (!post) return { state: "missing" as const };
  const [comments, digest, navigation] = await Promise.allSettled([
    listPublishedComments(slug), getPublishedDiscussionDigest(slug),
    getPublishedWorkNavigation("POST", slug, workSlug)
  ]);
  return {
    state: "ready" as const, post,
    comments: comments.status === "fulfilled" ? comments.value : [],
    digest: digest.status === "fulfilled" ? digest.value : null,
    navigation: navigation.status === "fulfilled" ? navigation.value : null,
    commentsUnavailable: comments.status === "rejected",
    digestUnavailable: digest.status === "rejected",
    navigationUnavailable: navigation.status === "rejected"
  };
}

/** 小说片段同样以正文为主，作品集导航故障不能阻止阅读。 */
export async function loadNovelPage(slug: string, workSlug?: string) {
  let fragment;
  try { fragment = await getPublishedNovelFragment(slug); }
  catch { return { state: "unavailable" as const }; }
  if (!fragment) return { state: "missing" as const };
  const [navigation] = await Promise.allSettled([getPublishedWorkNavigation("NOVEL", slug, workSlug)]);
  return {
    state: "ready" as const, fragment,
    navigation: navigation.status === "fulfilled" ? navigation.value : null,
    navigationUnavailable: navigation.status === "rejected"
  };
}
