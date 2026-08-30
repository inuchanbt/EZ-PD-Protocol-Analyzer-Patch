import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Creates Payload's first TableViewerColumn with SWT.RIGHT from the outset. */
public final class PayloadFirstColumnAlignmentPatcher {
    private static final String OWNER = "com/cypress/ezpdanalyzer/ui/views/PayloadView";
    private static final String COLUMN = "org/eclipse/jface/viewers/TableViewerColumn";
    private static final String CTOR = "(Lorg/eclipse/jface/viewers/TableViewer;I)V";
    private static final int SWT_RIGHT = 131072;

    private PayloadFirstColumnAlignmentPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: PayloadFirstColumnAlignmentPatcher <input.class> <output.class>");
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        if (!OWNER.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }
        final int[] methods = {0};
        final int[] constructors = {0};
        final int[] changes = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"createPartControl".equals(name)
                        || !"(Lorg/eclipse/swt/widgets/Composite;)V".equals(descriptor)) {
                    return downstream;
                }
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                            String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESPECIAL && COLUMN.equals(owner)
                                && "<init>".equals(name) && CTOR.equals(descriptor)) {
                            if (constructors[0] == 0) {
                                // Stack: uninitialized-column, viewer, old-style.
                                // Replace only the first column's style before invoking it.
                                super.visitInsn(Opcodes.POP);
                                super.visitLdcInsn(SWT_RIGHT);
                                changes[0]++;
                            }
                            constructors[0]++;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (methods[0] != 1 || constructors[0] != 2 || changes[0] != 1) {
            throw new IllegalStateException("Unexpected Payload column patch points: methods=" + methods[0]
                    + ", constructors=" + constructors[0] + ", changes=" + changes[0]);
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Patched Payload Byte Index column to be created right-aligned.");
    }
}
