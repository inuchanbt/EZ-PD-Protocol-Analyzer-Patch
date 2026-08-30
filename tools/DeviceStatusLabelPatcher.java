import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * Makes the upper-right device-status labels unambiguous without changing
 * their live value-update code: V: -> VBUS:, A: -> comma separator.
 */
public final class DeviceStatusLabelPatcher {
    private static final String TARGET =
        "com/cypress/ezpdanalyzer/ui/views/DeviceStatusView";
    private static final String METHOD = "createVoltAmpDetailsSection";
    private static final String DESCRIPTOR = "(Lorg/eclipse/swt/widgets/Composite;)V";

    private DeviceStatusLabelPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: DeviceStatusLabelPatcher <input.class> <output.class>"
            );
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!TARGET.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }

        final int[] methods = {0};
        final int[] vbusLabels = {0};
        final int[] separators = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );
                if (!METHOD.equals(name) || !DESCRIPTOR.equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if ("V:".equals(value)) {
                            super.visitLdcInsn("VBUS:");
                            vbusLabels[0]++;
                        } else if ("A:".equals(value)) {
                            super.visitLdcInsn(",");
                            separators[0]++;
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || vbusLabels[0] != 1 || separators[0] != 1) {
            throw new IllegalStateException(
                "Unexpected device-status patch points: methods=" + methods[0]
                + ", VBUS labels=" + vbusLabels[0]
                + ", separators=" + separators[0]
            );
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched Device Status labels: VBUS: <voltage>, <current>.");
    }
}
