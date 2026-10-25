package vn.livesign.app.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Gán request_id cho mỗi request, chạy trước Spring Security để lỗi 401/403 cũng có ID. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String ATTRIBUTE = "livesign.request_id";
  public static final String HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = "req-" + UUID.randomUUID();
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader(HEADER, id);
    MDC.put("request_id", id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("request_id");
    }
  }

  public static String of(HttpServletRequest request) {
    Object id = request.getAttribute(ATTRIBUTE);
    return id == null ? null : id.toString();
  }
}
