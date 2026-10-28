package app.rwun;

import java.nio.file.*;

public final class LegacyModelCleanupTest {
  private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception {
    Path root=Files.createTempDirectory("retired-model"),files=Files.createDirectories(root.resolve("files")),cache=Files.createDirectories(root.resolve("cache"));
    Path model=Files.createDirectories(files.resolve("offline-coach")),compiled=Files.createDirectories(cache.resolve("gemma/compiled"));
    Files.writeString(model.resolve("gemma3-1b.litertlm"),"synthetic model bytes");Files.writeString(model.resolve("cancelled.part"),"partial download");Files.writeString(compiled.resolve("kernel.bin"),"synthetic cache");
    Path history=Files.writeString(files.resolve("recorded-route.gpx"),"synthetic route"),other=Files.writeString(cache.resolve("race-cache.json"),"synthetic cache");
    LegacyModelCleanup.remove(files.toFile(),cache.toFile());
    check(!Files.exists(model)&&!Files.exists(cache.resolve("gemma")),"retired model and cache removed");
    check(Files.readString(history).equals("synthetic route")&&Files.exists(other),"unrelated activity and cache files preserved");
    LegacyModelCleanup.remove(files.toFile(),cache.toFile());check(Files.exists(history),"cleanup is idempotent");
    Path external=Files.createDirectories(root.resolve("unrelated")),keep=Files.writeString(external.resolve("keep"),"preserve");Files.createSymbolicLink(files.resolve("offline-coach"),external);
    LegacyModelCleanup.remove(files.toFile(),cache.toFile());check(Files.exists(keep),"symlink target never traversed");
    System.out.println("4 retired-model cleanup assertions passed");
  }
}
