import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Removes only File-workflow success/exit dialogs, preserving the work itself. */
public final class FileWorkflowDialogPatcher {
    private static final String EXPORT =
        "com/cypress/ezpdanalyzer/ui/handler/ExcelExportHandler$2$1";
    private static final String IMPORT =
        "com/cypress/ezpdanalyzer/ui/reader/XLSXReader$2$1";
    private static final String SAVE =
        "com/cypress/ezpdanalyzer/ui/handler/SaveHandler$2$1";
    private static final String EXIT =
        "com/cypress/ezpdanalyzer/ui/util/ExitUtil";
    private static final String MESSAGE_DIALOG = "org/eclipse/jface/dialogs/MessageDialog";
    private static final String DIALOG_ARGS =
        "(Lorg/eclipse/swt/widgets/Shell;Ljava/lang/String;Ljava/lang/String;)";

    private FileWorkflowDialogPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: FileWorkflowDialogPatcher <input.class> <output.class>"
            );
        }
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        String className = reader.getClassName();
        if (!(EXPORT.equals(className) || IMPORT.equals(className) ||
                SAVE.equals(className) || EXIT.equals(className))) {
            throw new IllegalStateException("Unexpected class: " + className);
        }

        final boolean exitClass = EXIT.equals(className);
        final int[] targetMethods = {0};
        final int[] removedDialogs = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );
                boolean target = exitClass
                    ? "openExitConfirmDialog".equals(name) && "()Z".equals(descriptor)
                    : "run".equals(name) && "()V".equals(descriptor);
                if (!target) {
                    return downstream;
                }
                targetMethods[0]++;
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && MESSAGE_DIALOG.equals(owner) &&
                                ((!exitClass && "openInformation".equals(methodName) &&
                                    (DIALOG_ARGS + "V").equals(methodDescriptor)) ||
                                 (exitClass && "openConfirm".equals(methodName) &&
                                    (DIALOG_ARGS + "Z").equals(methodDescriptor)))) {
                            // Consume shell/title/message.  For Exit, supply the same
                            // affirmative result that clicking OK would have produced.
                            super.visitInsn(Opcodes.POP);
                            super.visitInsn(Opcodes.POP);
                            super.visitInsn(Opcodes.POP);
                            if (exitClass) {
                                super.visitInsn(Opcodes.ICONST_1);
                            }
                            removedDialogs[0]++;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (targetMethods[0] != 1 || removedDialogs[0] != 1) {
            throw new IllegalStateException(
                "Unexpected File workflow patch points: methods=" + targetMethods[0]
                + ", dialogs=" + removedDialogs[0]
            );
        }
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("Removed one File-workflow dialog from " + className + '.');
    }
}
