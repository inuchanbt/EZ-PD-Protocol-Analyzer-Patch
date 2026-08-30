import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Restores community multi-select filters after every vendor table reset. */
public final class DataManagerFilterReapplyPatcher {
    private static final String DATA_MANAGER =
        "com/cypress/ezpdanalyzer/ui/util/DataManager";
    private static final String SUPPORT =
        "com/cypress/ezpdanalyzer/ui/nattable/UsbPacketTableSupport";
    private static final String NAT_TABLE =
        "org/eclipse/nebula/widgets/nattable/NatTable";
    private static final String REGISTRY =
        "org/eclipse/nebula/widgets/nattable/config/IConfigRegistry";

    private DataManagerFilterReapplyPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: DataManagerFilterReapplyPatcher <input.class> <output.class>"
            );
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!DATA_MANAGER.equals(reader.getClassName())) {
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
                if (!"themeUpdaterAfterFilterDataReset".equals(name) ||
                        !"()V".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL, DATA_MANAGER, "getNatTable",
                                "()L" + NAT_TABLE + ";", false
                            );
                            super.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL, NAT_TABLE, "getConfigRegistry",
                                "()L" + REGISTRY + ";", false
                            );
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, SUPPORT,
                                "configureMultiSelectFilters",
                                "(L" + REGISTRY + ";)V", false
                            );
                            inserts[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || inserts[0] != 1) {
            throw new IllegalStateException(
                "Unexpected filter-reset patch points: methods=" + methods[0] +
                ", inserts=" + inserts[0]
            );
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched multi-select filter reapply after table reset.");
    }
}
