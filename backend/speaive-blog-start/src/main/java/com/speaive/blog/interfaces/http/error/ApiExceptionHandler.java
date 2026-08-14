package com.speaive.blog.interfaces.http.error;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BlogException.class)
    ResponseEntity<ApiError> handleBlogException(BlogException exception) {
        return response(statusFor(exception.code()), exception.code().name(), exception.getMessage());
    }

    @ExceptionHandler(ApiHttpException.class)
    ResponseEntity<ApiError> handleApiException(ApiHttpException exception) {
        return response(exception.status(), exception.code().name(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "请求字段不合法" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("；"));
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST.name(),
                message.isBlank() ? "请求内容不合法" : message);
    }

    @ExceptionHandler({BindException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST.name(), "请求内容不合法");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.TOO_LARGE.name(), "上传文件超过大小限制");
    }

    private static HttpStatus statusFor(BlogErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SLUG_CONFLICT, VERSION_CONFLICT -> HttpStatus.CONFLICT;
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case INVALID_REQUEST, INVALID_FILE_NAME, INVALID_MARKDOWN, INVALID_IMAGE,
                    PATH_OUTSIDE_DATA_DIR -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
