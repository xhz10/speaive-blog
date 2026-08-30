import type { APIRoute } from "astro";

import { listPublishedNovelFragments, listPublishedPosts } from "../lib/public-api";

const escapeXml = (value: string) => value
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&apos;");

export const GET: APIRoute = async ({ site, url }) => {
  const [posts, novelFragments] = await Promise.all([
    listPublishedPosts(),
    listPublishedNovelFragments()
  ]);
  const base = process.env.SPEAIVE_SITE_URL ?? site?.href ?? url.origin;
  const staticPaths = ["/", "/archive/", "/novels/", "/about/"];
  const urls: Array<{ loc: string; lastmod?: string }> = [
    ...staticPaths.map((path) => ({ loc: new URL(path, base).href })),
    ...posts.map((post) => ({
      loc: new URL(`/blog/${encodeURIComponent(post.slug)}/`, base).href,
      lastmod: (post.updatedAt ?? post.publishedAt).toISOString()
    })),
    ...novelFragments.map((fragment) => ({
      loc: new URL(`/novels/${encodeURIComponent(fragment.slug)}/`, base).href,
      lastmod: fragment.updatedAt.toISOString()
    }))
  ];

  return new Response(`<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map((entry) => `  <url><loc>${escapeXml(entry.loc)}</loc>${entry.lastmod ? `<lastmod>${entry.lastmod}</lastmod>` : ""}</url>`).join("\n")}
</urlset>`, {
    headers: {
      "Content-Type": "application/xml; charset=utf-8",
      "Cache-Control": "public, max-age=300"
    }
  });
};
