import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** v1.0j: gives the Status column an 80px default width. */
public final class StatusWidthPatcher {
    private static final String BODY =
        "com/cypress/ezpdanalyzer/ui/nattable/CustomBodyLayerStack";
    private static final String DATA_LAYER =
        "org/eclipse/nebula/widgets/nattable/layer/DataLayer";

    private StatusWidthPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: StatusWidthPatcher <input.class> <output.class>"
            );
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!BODY.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }
        final int[] constructors = {0};
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
                if (!"<init>".equals(name) ||
                        !"(Ljava/util/List;Lorg/eclipse/nebula/widgets/nattable/data/IColumnPropertyAccessor;)V".equals(descriptor)) {
                    return downstream;
                }
                constructors[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            // Column 0 is Status. Set it last to override the
                            // original 60px default while preserving all others.
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(
                                Opcodes.GETFIELD, BODY, "bodyDataLayer",
                                "L" + DATA_LAYER + ";"
                            );
                            super.visitInsn(Opcodes.ICONST_0);
                            super.visitIntInsn(Opcodes.BIPUSH, 80);
                            super.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL, DATA_LAYER,
                                "setColumnWidthByPosition", "(II)V", false
                            );
                            inserts[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (constructors[0] != 1 || inserts[0] != 1) {
            throw new IllegalStateException(
                "Unexpected Status-width patch points: constructors=" + constructors[0] +
                ", inserts=" + inserts[0]
            );
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched Status column width to 80px.");
    }
}
