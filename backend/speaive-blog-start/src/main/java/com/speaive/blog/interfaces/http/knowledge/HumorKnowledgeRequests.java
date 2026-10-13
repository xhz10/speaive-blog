package com.speaive.blog.interfaces.http.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class HumorKnowledgeRequests {
    private HumorKnowledgeRequests() {}
    public record SaveMaterialRequest(
            @NotBlank @Size(max=500) String excerpt,
            @NotBlank @Size(max=2048) String sourceUrl,
            @NotBlank @Size(max=200) String sourceTitle,
            @NotBlank String sourceKind,
            @NotNull @Size(min=1, max=12) List<@NotBlank @Size(max=50) String> topics,
            @NotBlank String mechanism,
            @NotBlank @Size(max=1200) String analysis,
            @NotBlank @Size(max=1200) String adaptation,
            @Min(1) @Max(5) int quality, boolean enabled, @Min(1) long version) {}
}
