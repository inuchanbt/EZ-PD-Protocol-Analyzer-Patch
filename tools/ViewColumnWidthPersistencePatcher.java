import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Adds width-persistence hooks to the Details Tree and Payload Table. */
public final class ViewColumnWidthPersistencePatcher {
    private static final String DETAILS = "com/cypress/ezpdanalyzer/ui/views/DetailsView";
    private static final String PAYLOAD = "com/cypress/ezpdanalyzer/ui/views/PayloadView";
    private static final String SUPPORT =
        "com/cypress/ezpdanalyzer/ui/views/ViewColumnWidthPersistence";
    private static final String TREE = "org/eclipse/swt/widgets/Tree";
    private static final String TABLE = "org/eclipse/swt/widgets/Table";

    private ViewColumnWidthPersistencePatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                "usage: ViewColumnWidthPersistencePatcher <details-in> <details-out> <payload-in> <payload-out>"
            );
        }
        patch(Path.of(args[0]), Path.of(args[1]), DETAILS, "tree", TREE, "installDetails");
        patch(Path.of(args[2]), Path.of(args[3]), PAYLOAD, "payloadTable", TABLE, "installPayload");
        System.out.println("Patched Details and Payload column-width persistence.");
    }

    private static void patch(
            Path input, Path output, String owner, String field, String widget,
            String helperMethod) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(input));
        if (!owner.equals(reader.getClassName())) {
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
                    access, name, descriptor, signature, exceptions);
                if (!"createPartControl".equals(name)
                        || !"(Lorg/eclipse/swt/widgets/Composite;)V".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(
                                Opcodes.GETFIELD, owner, field, "L" + widget + ";"
                            );
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, SUPPORT, helperMethod,
                                "(L" + widget + ";)V", false
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
                "Unexpected " + owner + " patch points: methods=" + methods[0]
                + ", inserts=" + inserts[0]
            );
        }
        Files.write(output, writer.toByteArray());
    }
}
