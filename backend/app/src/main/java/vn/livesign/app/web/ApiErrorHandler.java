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
