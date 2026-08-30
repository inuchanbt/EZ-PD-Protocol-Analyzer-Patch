import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * Produces the compact VBUS status text: VBUS: <voltage>mV, <current>mA.
 * The existing live-reading flow remains unchanged.
 */
public final class DeviceStatusSpacingPatcher {
    private static final String BASE =
        "com/cypress/ezpdanalyzer/ui/views/DeviceStatusView";
    private static final String LABEL = "org/eclipse/swt/widgets/Label";
    private static final String GRID_DATA = "org/eclipse/swt/layout/GridData";

    private DeviceStatusSpacingPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: DeviceStatusSpacingPatcher <input.class> <output.class>"
            );
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        String name = reader.getClassName();
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        if (BASE.equals(name)) {
            patchView(reader, writer);
        } else if ((BASE + "$1").equals(name)) {
            patchRefresh(reader, writer);
        } else if ((BASE + "$2").equals(name)) {
            patchClear(reader, writer);
        } else {
            throw new IllegalStateException("Unexpected class: " + name);
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
    }

    private static void patchView(ClassReader reader, ClassWriter writer) {
        final int[] methods = {0};
        final int[] initialVoltages = {0};
        final int[] excludedSeparators = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );
                if (!"createVoltAmpDetailsSection".equals(name) ||
                        !"(Lorg/eclipse/swt/widgets/Composite;)V".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    private boolean vbusDefaultPending;
                    private boolean separatorTextPending;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (vbusDefaultPending) {
                            super.visitLdcInsn("0mV,");
                            vbusDefaultPending = false;
                            initialVoltages[0]++;
                        } else if (",".equals(value)) {
                            super.visitLdcInsn("");
                            separatorTextPending = true;
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode, String owner, String fieldName, String fieldDescriptor) {
                        super.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor);
                        if (opcode == Opcodes.PUTSTATIC && BASE.equals(owner) &&
                                "vbusVolt".equals(fieldName) &&
                                "Lorg/eclipse/swt/widgets/Label;".equals(fieldDescriptor)) {
                            vbusDefaultPending = true;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                        if (separatorTextPending && opcode == Opcodes.INVOKEVIRTUAL &&
                                LABEL.equals(owner) && "setText".equals(methodName) &&
                                "(Ljava/lang/String;)V".equals(methodDescriptor)) {
                            // Exclude the former comma-only Label from GridLayout so it
                            // cannot reserve a column or introduce horizontal spacing.
                            super.visitVarInsn(Opcodes.ALOAD, 6);
                            super.visitTypeInsn(Opcodes.NEW, GRID_DATA);
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(
                                Opcodes.INVOKESPECIAL, GRID_DATA, "<init>", "()V", false
                            );
                            super.visitInsn(Opcodes.DUP);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitFieldInsn(
                                Opcodes.PUTFIELD, GRID_DATA, "exclude", "Z"
                            );
                            super.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL, LABEL, "setLayoutData",
                                "(Ljava/lang/Object;)V", false
                            );
                            separatorTextPending = false;
                            excludedSeparators[0]++;
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || initialVoltages[0] != 1 || excludedSeparators[0] != 1) {
            throw new IllegalStateException(
                "Unexpected view patch points: methods=" + methods[0]
                + ", initialVoltages=" + initialVoltages[0]
                + ", excludedSeparators=" + excludedSeparators[0]
            );
        }
    }

    private static void patchRefresh(ClassReader reader, ClassWriter writer) {
        patchVbusValue(reader, writer, "mV,", "refresh");
    }

    private static void patchClear(ClassReader reader, ClassWriter writer) {
        patchVbusValue(reader, writer, "0mV,", "clear");
    }

    private static void patchVbusValue(
            ClassReader reader, ClassWriter writer, String newValue, String label) {
        final int[] methods = {0};
        final int[] replacements = {0};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );
                if (!"run".equals(name) || !"()V".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    private boolean vbusValuePending;

                    @Override
                    public void visitFieldInsn(
                            int opcode, String owner, String fieldName, String fieldDescriptor) {
                        super.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor);
                        if (opcode == Opcodes.GETSTATIC && BASE.equals(owner) &&
                                "vbusVolt".equals(fieldName) &&
                                "Lorg/eclipse/swt/widgets/Label;".equals(fieldDescriptor)) {
                            vbusValuePending = true;
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (vbusValuePending) {
                            super.visitLdcInsn(newValue);
                            vbusValuePending = false;
                            replacements[0]++;
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || replacements[0] != 1) {
            throw new IllegalStateException(
                "Unexpected " + label + " patch points: methods=" + methods[0]
                + ", replacements=" + replacements[0]
            );
        }
    }
}
