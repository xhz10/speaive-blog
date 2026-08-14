package com.speaive.blog.application.error;

public enum BlogErrorCode {
    INVALID_REQUEST,
    INVALID_FILE_NAME,
    INVALID_MARKDOWN,
    INVALID_IMAGE,
    PATH_OUTSIDE_DATA_DIR,
    TOO_LARGE,
    NOT_FOUND,
    SLUG_CONFLICT,
    VERSION_CONFLICT,
    STORAGE_ERROR
}
