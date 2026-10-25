package vn.livesign.app.web;

import java.util.List;

/** Thân lỗi chung theo docs/contracts/api.md: {"error":{code,message,fields,request_id}}. */
public record ApiError(Body error) {

  public record Body(String code, String message, List<String> fields, String requestId) {}

  public static ApiError of(String code, String message, List<String> fields, String requestId) {
    return new ApiError(new Body(code, message, List.copyOf(fields), requestId));
  }
}
