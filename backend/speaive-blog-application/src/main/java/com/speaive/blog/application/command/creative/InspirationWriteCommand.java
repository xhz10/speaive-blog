package com.speaive.blog.application.command.creative;

public record InspirationWriteCommand(String title, String body, String kind, boolean pinned) {
}
