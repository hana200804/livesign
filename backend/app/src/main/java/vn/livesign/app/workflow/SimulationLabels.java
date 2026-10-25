package vn.livesign.app.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
class SimulationLabels implements ResponseBodyAdvice<Object> {
  private final DemoMode mode;
  private final ObjectMapper json;

  SimulationLabels(DemoMode mode, ObjectMapper json) {
    this.mode = mode;
    this.json = json;
  }

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converter) {
    return mode.active();
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter type,
      MediaType content,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    response.getHeaders().set("X-LiveSign-Scenario", mode.scenario());
    response.getHeaders().set("X-LiveSign-Data-Kind", "simulation");
    if (body == null || body instanceof byte[] || body instanceof String) return body;
    if (!MediaType.APPLICATION_JSON.isCompatibleWith(content)) return body;
    @SuppressWarnings("unchecked")
    Map<String, Object> copy = json.convertValue(body, LinkedHashMap.class);
    copy.putAll(mode.labels());
    return copy;
  }
}
