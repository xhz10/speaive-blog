package com.speaive.blog.application.command.creative;

import java.util.List;

public record WorkCollectionWriteCommand(
        String slug,
        String title,
        String description,
        String cover,
        String visibility,
        List<WorkItemCommand> items
) {
    public WorkCollectionWriteCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record WorkItemCommand(String contentType, String contentSlug) {
    }
}
