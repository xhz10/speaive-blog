package com.speaive.blog.interfaces.http.creative;

import java.time.Instant;
import java.util.List;

public final class CreativeResponses {
    private CreativeResponses() {
    }

    public record InspirationDetail(
            String id, String title, String body, String kind, String status, boolean pinned,
            String targetType, String targetSlug, long revision, Instant createdAt, Instant updatedAt) {
    }

    public record InspirationList(List<InspirationDetail> items) {
    }

    public record ContentRevisionDetail(
            String contentType, long revision, String eventType, String slug, String title, String summary,
            String body, Instant publishedAt, List<String> tags, String cover, String status, String visibility,
            Instant updatedAt, Instant recordedAt) {
    }

    public record ContentRevisionList(List<ContentRevisionDetail> items) {
    }

    public record WorkDetail(
            String slug, String title, String description, String cover, String visibility, long revision,
            Instant updatedAt, List<WorkItemDetail> items) {
    }

    public record WorkItemDetail(
            String contentType, String contentSlug, int position, String title, String summary,
            String href, Instant publishedAt) {
    }

    public record WorkList(List<WorkDetail> items) {
    }

    public record WorkNavigation(
            String workSlug, String workTitle, NavigationItem previous, NavigationItem next) {
    }

    public record NavigationItem(String contentType, String contentSlug, String title, String href) {
    }

    public record ShareDetail(
            String id, String contentType, String contentSlug, String token, Instant expiresAt,
            Instant revokedAt, Instant lastAccessedAt, Instant createdAt, boolean active) {
    }

    public record ShareList(List<ShareDetail> items) {
    }

    public record SharedContent(
            String contentType, String slug, String title, String summary, String body, String html,
            Instant publishedAt, Instant updatedAt, String authorDisplayName, Instant expiresAt) {
    }

    public record EditorialReviewDetail(
            String id, long postRevision, String agentId, String agentDisplayName, String quoteText,
            String quotePrefix, String quoteSuffix, String body, String model, Instant createdAt, boolean stale) {
    }

    public record EditorialReviewList(List<EditorialReviewDetail> items) {
    }

    public record DiscussionDigest(
            String postSlug, String body, String model, String state, int commentCount, Instant updatedAt) {
    }

    public record Resurfacing(List<ResurfacingItem> items) {
    }

    public record ResurfacingItem(
            String kind, String title, String description, String href, List<String> tags,
            List<String> relatedSlugs) {
    }
}
