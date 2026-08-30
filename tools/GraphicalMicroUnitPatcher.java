import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Replaces Graphical coordinate microsecond suffixes with the µ symbol. */
public final class GraphicalMicroUnitPatcher {
    private static final Set<String> TARGETS = Set.of(
            "com/cypress/ezpdanalyzer/ui/views/GraphicalView$2$1",
            "com/cypress/ezpdanalyzer/ui/views/GraphicalView$2$2"
    );

    private GraphicalMicroUnitPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GraphicalMicroUnitPatcher <input.class> <output.class>");
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!TARGETS.contains(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }
        final int[] replacements = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if ("u".equals(value)) {
                            super.visitLdcInsn("µ");
                            replacements[0]++;
                            return;
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (replacements[0] != 1) {
            throw new IllegalStateException("Expected exactly one u suffix in "
                    + reader.getClassName() + "; found " + replacements[0]);
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched Graphical microsecond suffix to µ.");
    }
}
