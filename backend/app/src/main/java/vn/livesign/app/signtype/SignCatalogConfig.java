package vn.livesign.app.signtype;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import vn.livesign.core.catalog.SignCatalog;

@Configuration(proxyBeanMethods = false)
class SignCatalogConfig {

  /** classes.yaml được Maven chép từ ai/configs vào classpath (app/pom.xml). */
  @Bean
  SignCatalog signCatalog() throws IOException {
    try (InputStream in = new ClassPathResource("livesign/classes.yaml").getInputStream()) {
      return SignCatalog.load(in);
    }
  }
}
