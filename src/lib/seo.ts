import type { PostAuthor } from "./post-author";
import { site } from "../site";

export interface StructuredDataOptions {
  canonicalUrl: URL;
  configuredSite: URL;
  title?: string;
  description: string;
  imageUrl?: URL;
  publishedAt?: string;
  updatedAt?: string;
  author?: PostAuthor;
  type: "website" | "article" | "fiction";
}

interface SearchVerificationEnvironment {
  SPEAIVE_GOOGLE_SITE_VERIFICATION?: string;
  SPEAIVE_BING_SITE_VERIFICATION?: string;
  SPEAIVE_BAIDU_SITE_VERIFICATION?: string;
}

export interface SearchVerificationTag {
  name: "google-site-verification" | "msvalidate.01" | "baidu-site-verification";
  content: string;
}

type SchemaNode = Record<string, unknown>;

const normalizedPath = (url: URL) => url.pathname.replace(/\/+$/, "") || "/";

export function buildStructuredData(options: StructuredDataOptions) {
  const homeUrl = new URL("/", options.configuredSite);
  const identityUrl = new URL(site.identity.profilePath, options.configuredSite);
  const websiteId = new URL("/#website", options.configuredSite).href;
  const identityId = new URL(`${site.identity.profilePath}#speaive`, options.configuredSite).href;
  const person = buildSiteIdentity(identityUrl, identityId);
  const path = normalizedPath(options.canonicalUrl);
  let nodes: SchemaNode[];

  if (options.type === "article") {
    nodes = [{
      "@type": "BlogPosting",
      "@id": `${options.canonicalUrl.href}#article`,
      headline: options.title,
      description: options.description,
      url: options.canonicalUrl.href,
      mainEntityOfPage: options.canonicalUrl.href,
      image: options.imageUrl?.href,
      datePublished: options.publishedAt,
      dateModified: options.updatedAt,
      inLanguage: site.locale,
      isPartOf: {
        "@type": "WebSite",
        "@id": websiteId,
        name: site.name,
        url: homeUrl.href
      },
      author: options.author
        ? buildArticleAuthor(options.author, person)
        : person
    }];
  } else if (options.type === "fiction") {
    nodes = [{
      "@type": "CreativeWork",
      "@id": `${options.canonicalUrl.href}#fiction`,
      name: options.title,
      abstract: options.description,
      url: options.canonicalUrl.href,
      mainEntityOfPage: options.canonicalUrl.href,
      datePublished: options.publishedAt,
      dateModified: options.updatedAt,
      inLanguage: site.locale,
      genre: "Fiction",
      isPartOf: {
        "@type": "WebSite",
        "@id": websiteId,
        name: site.name,
        url: homeUrl.href
      },
      author: options.author
        ? buildArticleAuthor(options.author, person)
        : person
    }];
  } else if (path === "/") {
    nodes = [
      {
        "@type": "WebSite",
        "@id": websiteId,
        name: site.name,
        alternateName: site.alternateNames,
        description: site.description,
        url: homeUrl.href,
        inLanguage: site.locale,
        creator: { "@id": identityId }
      },
      person
    ];
  } else if (path === normalizedPath(identityUrl)) {
    nodes = [
      {
        "@type": "ProfilePage",
        "@id": `${identityUrl.href}#profile`,
        name: options.title,
        description: options.description,
        url: identityUrl.href,
        inLanguage: site.locale,
        isPartOf: { "@id": websiteId },
        mainEntity: { "@id": identityId }
      },
      person
    ];
  } else {
    nodes = [{
      "@type": "WebPage",
      "@id": `${options.canonicalUrl.href}#webpage`,
      name: options.title,
      description: options.description,
      url: options.canonicalUrl.href,
      inLanguage: site.locale,
      isPartOf: { "@id": websiteId }
    }];
  }

  return {
    "@context": "https://schema.org",
    "@graph": nodes
  };
}

export function getSearchVerificationTags(
  environment: SearchVerificationEnvironment = process.env
): SearchVerificationTag[] {
  return [
    verificationTag("google-site-verification", environment.SPEAIVE_GOOGLE_SITE_VERIFICATION),
    verificationTag("msvalidate.01", environment.SPEAIVE_BING_SITE_VERIFICATION),
    verificationTag("baidu-site-verification", environment.SPEAIVE_BAIDU_SITE_VERIFICATION)
  ].filter((tag): tag is SearchVerificationTag => tag !== null);
}

function buildSiteIdentity(identityUrl: URL, identityId: string): SchemaNode {
  return {
    "@type": "Person",
    "@id": identityId,
    name: site.identity.name,
    description: site.identity.description,
    url: identityUrl.href
  };
}

function buildArticleAuthor(author: PostAuthor, siteIdentity: SchemaNode): SchemaNode {
  if (author.type === "HUMAN" && author.displayName === site.identity.name) {
    return siteIdentity;
  }

  return {
    "@type": author.type === "HUMAN" ? "Person" : "Organization",
    name: author.displayName
  };
}

function verificationTag(
  name: SearchVerificationTag["name"],
  value?: string
): SearchVerificationTag | null {
  const content = value?.trim();
  return content ? { name, content } : null;
}
