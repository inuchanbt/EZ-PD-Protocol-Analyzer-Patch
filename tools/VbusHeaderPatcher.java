import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Changes only the USB PD Messages Vbus header spelling to VBUS(mV). */
public final class VbusHeaderPatcher {
    private static final String TABLE =
        "com/cypress/ezpdanalyzer/ui/nattable/CreateUSBPackageNatTable";
    private static final String SUPPORT =
        "com/cypress/ezpdanalyzer/ui/nattable/UsbPacketTableSupport";

    private VbusHeaderPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: VbusHeaderPatcher <input.class> <output.class>"
            );
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!TABLE.equals(reader.getClassName())) {
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
                if (!"createCustomNatTable".equals(name) ||
                        !"()Lorg/eclipse/nebula/widgets/nattable/NatTable;".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    private boolean vbusHeaderPending;

                    @Override
                    public void visitLdcInsn(Object value) {
                        super.visitLdcInsn(value);
                        if ("(mV)".equals(value)) {
                            vbusHeaderPending = true;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                        if (vbusHeaderPending && opcode == Opcodes.INVOKEVIRTUAL &&
                                "java/lang/StringBuilder".equals(owner) &&
                                "toString".equals(methodName) &&
                                "()Ljava/lang/String;".equals(methodDescriptor)) {
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, SUPPORT, "normalizeVbusHeader",
                                "(Ljava/lang/String;)Ljava/lang/String;", false
                            );
                            vbusHeaderPending = false;
                            inserts[0]++;
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || inserts[0] != 1) {
            throw new IllegalStateException(
                "Unexpected VBUS header patch points: methods=" + methods[0] +
                ", inserts=" + inserts[0]
            );
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched USB PD Messages VBUS(mV) header.");
    }
}
