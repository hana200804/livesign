package vn.livesign.app.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có mã hợp đồng; ApiErrorHandler đổi thành HTTP + ApiError. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final List<String> fields;

  public ApiException(HttpStatus status, String code, String message, List<String> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = List.copyOf(fields);
  }

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of());
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<String> fields() {
    return fields;
  }
}
