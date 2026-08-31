package com.speaive.blog.application.result.creative;

public record WorkNavigationResult(
        String workSlug,
        String workTitle,
        NavigationItem previous,
        NavigationItem next
) {
    public record NavigationItem(String contentType, String contentSlug, String title, String href) {
    }
}
