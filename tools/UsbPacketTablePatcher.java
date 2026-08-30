import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Applies the v1.0g USB PD Messages presentation changes without source redistribution. */
public final class UsbPacketTablePatcher {
    private static final String TABLE =
        "com/cypress/ezpdanalyzer/ui/nattable/CreateUSBPackageNatTable";
    private static final String TABLE_LABELS = TABLE + "$1";
    private static final String BODY =
        "com/cypress/ezpdanalyzer/ui/nattable/CustomBodyLayerStack";
    private static final String SUPPORT =
        "com/cypress/ezpdanalyzer/ui/nattable/UsbPacketTableSupport";
    private static final String DATA_LAYER =
        "org/eclipse/nebula/widgets/nattable/layer/DataLayer";
    private static final String REGISTRY =
        "org/eclipse/nebula/widgets/nattable/config/IConfigRegistry";
    private static final String LABEL_STACK =
        "org/eclipse/nebula/widgets/nattable/layer/LabelStack";

    private UsbPacketTablePatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                "usage: UsbPacketTablePatcher <table.in> <table.out> " +
                "<labels.in> <labels.out> <body.in> <body.out>"
            );
        }
        patchTable(Path.of(args[0]), Path.of(args[1]));
        patchLabels(Path.of(args[2]), Path.of(args[3]));
        patchBody(Path.of(args[4]), Path.of(args[5]));
        System.out.println("Patched USB PD Messages layout, labels, and numeric alignment.");
    }

    private static void patchTable(Path input, Path output) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(input));
        requireClass(reader, TABLE);
        final int[] methods = {0};
        final int[] registries = {0};
        final int[] vbusLabels = {0};
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
                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        super.visitVarInsn(opcode, var);
                        if (opcode == Opcodes.ASTORE && var == 1) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, SUPPORT, "configure",
                                "(L" + REGISTRY + ";)V", false
                            );
                            registries[0]++;
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if ("(V)".equals(value)) {
                            super.visitLdcInsn("(mV)");
                            vbusLabels[0]++;
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || registries[0] != 1 || vbusLabels[0] != 1) {
            throw new IllegalStateException(
                "Unexpected table patch points: methods=" + methods[0] +
                ", registries=" + registries[0] + ", Vbus labels=" + vbusLabels[0]
            );
        }
        Files.write(output, writer.toByteArray());
    }

    private static void patchLabels(Path input, Path output) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(input));
        requireClass(reader, TABLE_LABELS);
        final int[] methods = {0};
        final int[] returns = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );
                if (!"accumulateConfigLabels".equals(name) ||
                        !("(L" + LABEL_STACK + ";II)V").equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitVarInsn(Opcodes.ILOAD, 4);
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, SUPPORT, "addRightAlignedColumnLabel",
                                "(L" + LABEL_STACK + ";I)V", false
                            );
                            returns[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || returns[0] != 1) {
            throw new IllegalStateException(
                "Unexpected label patch points: methods=" + methods[0] +
                ", returns=" + returns[0]
            );
        }
        Files.write(output, writer.toByteArray());
    }

    private static void patchBody(Path input, Path output) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(input));
        requireClass(reader, BODY);
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
                    public void visitFieldInsn(
                            int opcode, String owner, String fieldName, String descriptor) {
                        super.visitFieldInsn(opcode, owner, fieldName, descriptor);
                        if (opcode == Opcodes.PUTFIELD && BODY.equals(owner) &&
                                "bodyDataLayer".equals(fieldName) &&
                                ("L" + DATA_LAYER + ";").equals(descriptor)) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(
                                Opcodes.GETFIELD, BODY, "bodyDataLayer",
                                "L" + DATA_LAYER + ";"
                            );
                            super.visitInsn(Opcodes.ICONST_2);
                            super.visitIntInsn(Opcodes.SIPUSH, 200);
                            super.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL, DATA_LAYER,
                                "setColumnWidthByPosition", "(II)V", false
                            );
                            inserts[0]++;
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (constructors[0] != 1 || inserts[0] != 1) {
            throw new IllegalStateException(
                "Unexpected Message width patch points: constructors=" + constructors[0] +
                ", inserts=" + inserts[0]
            );
        }
        Files.write(output, writer.toByteArray());
    }

    private static void requireClass(ClassReader reader, String expected) {
        if (!expected.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }
    }
}
