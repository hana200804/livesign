package vn.livesign.app.signtype;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.livesign.core.catalog.SignCatalog;

@RestController
@RequestMapping("/api/v1/sign-types")
class SignTypeController {

  record SignTypesResponse(String classesVersion, List<SignTypeItem> items) {}

  private final SignTypeRepository repository;
  private final SignCatalog catalog;

  SignTypeController(SignTypeRepository repository, SignCatalog catalog) {
    this.repository = repository;
    this.catalog = catalog;
  }

  @GetMapping
  SignTypesResponse list() {
    String version = catalog.classesVersion();
    return new SignTypesResponse(version, repository.findByClassesVersion(version));
  }
}
