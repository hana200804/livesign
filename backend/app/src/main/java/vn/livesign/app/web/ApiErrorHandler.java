package vn.livesign.app.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiErrorHandler {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ApiErrorHandler.class);

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> method(HttpServletRequest request) {
    return ResponseEntity.status(405)
        .body(
            ApiError.of(
                "METHOD_NOT_ALLOWED",
                "Phương thức không được hỗ trợ",
                List.of(),
                RequestIdFilter.of(request)));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ApiError> missing(HttpServletRequest request) {
    return ResponseEntity.status(404)
        .body(
            ApiError.of(
                "NOT_FOUND",
                "Không có endpoint hoặc tài nguyên",
                List.of(),
                RequestIdFilter.of(request)));
  }

  @ExceptionHandler({
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<ApiError> query(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            ApiError.of(
                "INVALID_QUERY", "Thiếu hoặc sai query", List.of(), RequestIdFilter.of(request)));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> internal(Exception e, HttpServletRequest request) {
    log.error("Request failed: {}", RequestIdFilter.of(request), e);
    return ResponseEntity.internalServerError()
        .body(
            ApiError.of(
                "INTERNAL_ERROR",
                "Lỗi dịch vụ; có thể thử lại bằng cùng ID",
                List.of(),
                RequestIdFilter.of(request)));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException e, HttpServletRequest request) {
    return ResponseEntity.status(e.status())
        .body(ApiError.of(e.code(), e.getMessage(), e.fields(), RequestIdFilter.of(request)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e, HttpServletRequest request) {
    List<String> fields =
        e.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).distinct().toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ApiError.of(
                "INVALID_REQUEST", "Thiếu hoặc sai trường", fields, RequestIdFilter.of(request)));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> unreadable(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ApiError.of(
                "INVALID_JSON", "Không đọc được JSON", List.of(), RequestIdFilter.of(request)));
  }
}
