package com.speaive.blog.interfaces.http.creative;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CreativeRequests {
    private CreativeRequests() {
    }

    public record InspirationWriteRequest(
            @Size(max = 120) String title,
            @NotBlank @Size(max = 10_000) String body,
            @NotBlank String kind,
            boolean pinned,
            Long revision
    ) {
    }

    public record InspirationTransitionRequest(
            @NotBlank String status,
            String targetType,
            @Size(max = 100) String targetSlug,
            @Min(1) long revision
    ) {
    }

    public record WorkWriteRequest(
            @Size(max = 100) String slug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 800) String description,
            @Size(max = 2048) String cover,
            @NotBlank String visibility,
            @NotNull @Size(max = 200) List<@Valid WorkItemRequest> items,
            Long revision
    ) {
    }

    public record WorkItemRequest(@NotBlank String contentType, @NotBlank @Size(max = 100) String contentSlug) {
    }

    public record RestoreRevisionRequest(@NotBlank String currentVersion) {
    }

    public record CreateShareRequest(
            @NotBlank String contentType,
            @NotBlank @Size(max = 100) String contentSlug,
            @Min(1) @Max(90) int validDays
    ) {
    }

    public record EditorialReviewRequest(
            @NotBlank String agentId,
            @Size(max = 4_000) String quoteText,
            @Size(max = 240) String quotePrefix,
            @Size(max = 240) String quoteSuffix
    ) {
    }
}
