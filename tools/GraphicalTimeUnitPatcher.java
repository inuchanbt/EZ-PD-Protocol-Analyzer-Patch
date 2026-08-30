import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Updates only the Graphical chart's x-axis microsecond label. */
public final class GraphicalTimeUnitPatcher {
    private static final String OWNER = "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";

    private GraphicalTimeUnitPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GraphicalTimeUnitPatcher <input.class> <output.class>");
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!OWNER.equals(reader.getClassName())) {
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
                        if ("Time(us)".equals(value)) {
                            super.visitLdcInsn("Time(µs)");
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
            throw new IllegalStateException("Expected exactly one Time(us) label; found " + replacements[0]);
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched Graphical x-axis label to Time(µs).");
    }
}
