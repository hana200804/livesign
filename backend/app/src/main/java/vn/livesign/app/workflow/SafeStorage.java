package vn.livesign.app.workflow;

import static vn.livesign.app.workflow.Jsons.*;

import java.io.*;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SafeStorage {
  private final Path root;

  public SafeStorage(@Value("${livesign.storage-root:./data}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public Path path(String ref) {
    if (ref == null) throw error(404, "FILE_NOT_AVAILABLE", "Chưa có file trong kho");
    ObservationValidator.safeRef(ref);
    try {
      Path base = root.toRealPath(), target = base.resolve(ref).normalize().toRealPath();
      if (!target.startsWith(base) || !Files.isRegularFile(target))
        throw error(404, "FILE_NOT_AVAILABLE", "Không có file được phép đọc");
      return target;
    } catch (IOException e) {
      throw error(404, "FILE_NOT_AVAILABLE", "Không có file trong kho");
    }
  }

  public byte[] bytes(String ref) {
    try {
      return Files.readAllBytes(path(ref));
    } catch (IOException e) {
      throw error(404, "FILE_NOT_AVAILABLE", "Không đọc được file trong kho");
    }
  }

  public boolean exists(String ref) {
    try {
      path(ref);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
