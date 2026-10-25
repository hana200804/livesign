package vn.livesign.app.signtype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vn.livesign.core.catalog.SignCatalog;

/** Sau Flyway, nạp danh mục classes.yaml vào sign_type để FK của sign/observation dùng. */
@Component
class SignTypeSync implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SignTypeSync.class);

  private final SignTypeRepository repository;
  private final SignCatalog catalog;

  SignTypeSync(SignTypeRepository repository, SignCatalog catalog) {
    this.repository = repository;
    this.catalog = catalog;
  }

  @Override
  public void run(ApplicationArguments args) {
    repository.upsert(catalog);
    log.info(
        "Đã nạp {} loại biển, classes_version={}",
        catalog.types().size(),
        catalog.classesVersion());
  }
}
