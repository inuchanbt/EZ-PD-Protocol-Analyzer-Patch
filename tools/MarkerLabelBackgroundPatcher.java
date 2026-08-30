import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Adds a transparent label background immediately before each selected marker is registered. */
public final class MarkerLabelBackgroundPatcher {
    private static final String TARGET =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
    private static final String PLOT = "org/jfree/chart/plot/XYPlot";
    private static final String HELPER =
        "com/cypress/ezpdanalyzer/ui/jfreechart/MarkerLabelBackgroundSupport";
    private static final String ADD_MARKER =
        "(Lorg/jfree/chart/plot/Marker;)V";

    private MarkerLabelBackgroundPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: MarkerLabelBackgroundPatcher <input.class> <output.class>"
            );
        }
        byte[] input = Files.readAllBytes(Path.of(args[0]));
        ClassReader reader = new ClassReader(input);
        if (!TARGET.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }
        final int[] methods = {0};
        final int[] inserts = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );
                if (!"displayGraph".equals(name) ||
                        !"(Lorg/eclipse/jface/viewers/ISelection;)V".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && PLOT.equals(owner) &&
                                "addDomainMarker".equals(methodName) &&
                                ADD_MARKER.equals(methodDescriptor)) {
                            // Stack is [ XYPlot, Marker ]; preserve both for the
                            // original call after passing a duplicate to helper.
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, HELPER, "clear",
                                "(Ljava/lang/Object;)V", false
                            );
                            inserts[0]++;
                        }
                        super.visitMethodInsn(
                            opcode, owner, methodName, methodDescriptor, isInterface
                        );
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || inserts[0] != 2) {
            throw new IllegalStateException(
                "Expected one displayGraph method and two marker inserts; found methods=" +
                methods[0] + ", inserts=" + inserts[0]
            );
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched two marker-label backgrounds.");
    }
}
