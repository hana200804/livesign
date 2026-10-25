package vn.livesign.app.workflow;

import java.io.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import vn.livesign.core.config.CoreConfig;

@Configuration(proxyBeanMethods = false)
class WorkflowConfig {
  @Bean
  CoreConfig coreConfig(
      @Value("${livesign.params:classpath:livesign/params.yaml}") Resource resource)
      throws IOException {
    try (var in = resource.getInputStream()) {
      return new CoreConfig(in.readAllBytes());
    }
  }
}
