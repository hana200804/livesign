package vn.livesign.app.flow;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.livesign.app.workflow.SafeStorage;

class StorageTest {
  @TempDir Path dir;

  @Test
  void chiDocTrongKho() throws Exception {
    Files.writeString(dir.resolve("a.txt"), "test");
    var store = new SafeStorage(dir.toString());
    assertEquals("test", new String(store.bytes("a.txt")));
    for (String ref :
        new String[] {
          "../a.txt", "C:/secret", "/etc/passwd", "https://example.com/a", "a/../a.txt", "a\\b"
        }) assertThrows(RuntimeException.class, () -> store.bytes(ref));
    assertThrows(RuntimeException.class, () -> store.bytes("missing.txt"));
  }
}
