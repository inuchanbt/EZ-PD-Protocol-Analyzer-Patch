import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * v1.0h: restores v1.0g's per-column alignment after the application's theme
 * has configured the table, and expands the Data column from 200px to 400px.
 */
public final class UsbPacketTableLayoutFixPatcher {
    private static final String TABLE =
        "com/cypress/ezpdanalyzer/ui/nattable/CreateUSBPackageNatTable";
    private static final String BODY =
        "com/cypress/ezpdanalyzer/ui/nattable/CustomBodyLayerStack";
    private static final String SUPPORT =
        "com/cypress/ezpdanalyzer/ui/nattable/UsbPacketTableSupport";
    private static final String NAT_TABLE =
        "org/eclipse/nebula/widgets/nattable/NatTable";
    private static final String THEME =
        "org/eclipse/nebula/widgets/nattable/style/theme/ThemeConfiguration";
    private static final String REGISTRY =
        "org/eclipse/nebula/widgets/nattable/config/IConfigRegistry";
    private static final String DATA_LAYER =
        "org/eclipse/nebula/widgets/nattable/layer/DataLayer";

    private UsbPacketTableLayoutFixPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                "usage: UsbPacketTableLayoutFixPatcher <table.in> <table.out> <body.in> <body.out>"
            );
        }
        patchTable(Path.of(args[0]), Path.of(args[1]));
        patchBody(Path.of(args[2]), Path.of(args[3]));
        System.out.println("Patched USB PD Messages alignment priority and Data column width.");
    }

    private static void patchTable(Path input, Path output) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(input));
        requireClass(reader, TABLE);
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
                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                        if (opcode == Opcodes.INVOKEVIRTUAL && NAT_TABLE.equals(owner) &&
                                "setTheme".equals(methodName) &&
                                ("(L" + THEME + ";)V").equals(methodDescriptor)) {
                            // FontStylingThemeConfiguration runs after NatTable.configure()
                            // and overwrites our earlier alignment attributes. Re-registering
                            // the label-specific attribute here gives it final precedence.
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, SUPPORT, "configure",
                                "(L" + REGISTRY + ";)V", false
                            );
                            inserts[0]++;
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || inserts[0] != 1) {
            throw new IllegalStateException(
                "Unexpected alignment patch points: methods=" + methods[0] +
                ", inserts=" + inserts[0]
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
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            // Column 11 is Data. Set it last so the new 400px width
                            // wins over v1.0g's existing 200px default.
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(
                                Opcodes.GETFIELD, BODY, "bodyDataLayer",
                                "L" + DATA_LAYER + ";"
                            );
                            super.visitIntInsn(Opcodes.BIPUSH, 11);
                            super.visitIntInsn(Opcodes.SIPUSH, 400);
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
                "Unexpected Data-width patch points: constructors=" + constructors[0] +
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
