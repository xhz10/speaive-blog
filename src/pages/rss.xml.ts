import type { APIRoute } from "astro";

import { listPublishedPosts } from "../lib/public-api";
import { site } from "../site";

const xml = (value: string) => value
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&apos;");

export const GET: APIRoute = async ({ site: astroSite, url }) => {
  const posts = await listPublishedPosts();
  const base = process.env.SPEAIVE_SITE_URL ?? astroSite?.href ?? url.origin;
  const items = posts.slice(0, 50).map((post) => `
    <item>
      <title>${xml(post.title)}</title>
      <link>${xml(new URL(`/blog/${encodeURIComponent(post.slug)}/`, base).href)}</link>
      <guid isPermaLink="true">${xml(new URL(`/blog/${encodeURIComponent(post.slug)}/`, base).href)}</guid>
      <description>${xml(post.description)}</description>
      <pubDate>${post.publishedAt.toUTCString()}</pubDate>
    </item>`).join("");

  return new Response(`<?xml version="1.0" encoding="UTF-8" ?>
<rss version="2.0">
  <channel>
    <title>${xml(site.name)}</title>
    <link>${xml(new URL("/", base).href)}</link>
    <description>${xml(site.description)}</description>
    <language>zh-CN</language>${items}
  </channel>
</rss>`, {
    headers: { "Content-Type": "application/rss+xml; charset=utf-8" }
  });
};
