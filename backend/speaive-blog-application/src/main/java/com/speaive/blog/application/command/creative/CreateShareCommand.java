package com.speaive.blog.application.command.creative;

public record CreateShareCommand(String contentType, String contentSlug, int validDays) {
}
