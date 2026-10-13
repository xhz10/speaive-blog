package com.speaive.blog.application.command.knowledge;

import java.util.List;

public record SaveHumorMaterialCommand(String excerpt, String sourceUrl, String sourceTitle, String sourceKind,
        List<String> topics, String mechanism, String analysis, String adaptation, int quality,
        boolean enabled, long version) {}
