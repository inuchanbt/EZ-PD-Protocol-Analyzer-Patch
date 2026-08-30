import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Installs the display-only X, Y, ΔX, ΔY ordering hook. */
public final class GraphSelectorHeaderPatcher {
    private static final String OWNER = "com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite";
    private static final String SUPPORT = "com/cypress/ezpdanalyzer/ui/views/GraphicalHeaderSupport";

    private GraphSelectorHeaderPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GraphSelectorHeaderPatcher <input.class> <output.class>");
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!OWNER.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }
        final int[] methods = {0};
        final int[] inserts = {0};
        final int[] existing = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"createComboGroup".equals(name)
                        || !"()Lorg/eclipse/swt/widgets/Group;".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                            String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && SUPPORT.equals(owner)
                                && "configure".equals(name)) {
                            existing[0]++;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            // The Group return value is already on the stack; retain it
                            // while passing this and local #1 to the display helper.
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "configure",
                                    "(L" + OWNER + ";Lorg/eclipse/swt/widgets/Group;)V", false);
                            inserts[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || inserts[0] != 1 || existing[0] != 0) {
            throw new IllegalStateException("Unexpected Graphical header patch points: methods=" + methods[0]
                    + ", inserts=" + inserts[0] + ", existing=" + existing[0]);
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched Graphical header order: X, Y, ΔX, ΔY.");
    }
}
