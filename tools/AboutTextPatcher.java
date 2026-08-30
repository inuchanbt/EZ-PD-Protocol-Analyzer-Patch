import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Adds the public mod signature to the existing About build line. */
public final class AboutTextPatcher {
    private static final String ORIGINAL =
        "Build     :  155&#10;";
    private static final String PATCHED =
        "Build     :  155 (Mod by @USB_PD_EPR_240W v1.0p)&#10;";

    private AboutTextPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: AboutTextPatcher <input-plugin.xml> <output-plugin.xml>"
            );
        }
        Path input = Path.of(args[0]);
        String xml = new String(Files.readAllBytes(input), StandardCharsets.UTF_8);
        int first = xml.indexOf(ORIGINAL);
        int last = xml.lastIndexOf(ORIGINAL);
        if (first < 0 || first != last || xml.contains(PATCHED)) {
            throw new IllegalStateException("Unexpected About build-text patch points.");
        }
        String patched = xml.substring(0, first) + PATCHED
            + xml.substring(first + ORIGINAL.length());
        Files.write(Path.of(args[1]), patched.getBytes(StandardCharsets.UTF_8));
        System.out.println("Patched About build line with mod signature.");
    }
}
