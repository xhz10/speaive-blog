import type { APIRoute } from "astro";

export const GET: APIRoute = ({ site, url }) => {
  const base = process.env.SPEAIVE_SITE_URL ?? site?.href ?? url.origin;
  const sitemapUrl = new URL("/sitemap.xml", base).href;

  return new Response(`User-agent: *
Allow: /
Disallow: /studio/
Disallow: /api/v1/studio/

Sitemap: ${sitemapUrl}
`, {
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "public, max-age=3600"
    }
  });
};
