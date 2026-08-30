import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Patch only the STOP final-buffer handoff; vendor flush still executes. */
public final class GraphStopHandlerPatcher implements Opcodes {

    private static final String OWNER =
        "com/cypress/ezpdanalyzer/ui/handler/StopHandler";
    private static final String CACHED =
        "com/cypress/ezpdanalyzer/ui/util/CachedDataListManager";
    private static final String SUPPORT =
        "com/cypress/ezpdanalyzer/ui/jfreechart/GraphStopSupport";

    private GraphStopHandlerPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                "Usage: GraphStopHandlerPatcher <input.class> <output.class>"
            );
            System.exit(2);
        }

        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        byte[] original = Files.readAllBytes(in);

        System.out.println("Input : " + in);
        System.out.println("SHA256: " + sha256(original));

        ClassReader reader = new ClassReader(original);
        if (!OWNER.equals(reader.getClassName())) {
            throw new IllegalStateException(
                "Unexpected class: " + reader.getClassName()
            );
        }

        ClassWriter writer =
            new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        final int[] targetMethods = {0};
        final int[] vendorFlushCalls = {0};
        final int[] captureInserted = {0};
        final int[] restoreInserted = {0};

        ClassVisitor cv = new ClassVisitor(ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {

                MethodVisitor mv = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );

                if (!"stopCapturing".equals(name) ||
                    !"()V".equals(descriptor)) {
                    return mv;
                }

                if ((access & ACC_STATIC) == 0) {
                    throw new IllegalStateException(
                        "stopCapturing() unexpectedly not static"
                    );
                }
                targetMethods[0]++;

                return new MethodVisitor(ASM8, mv) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String methodName,
                            String methodDescriptor,
                            boolean isInterface) {

                        if (opcode == INVOKEVIRTUAL &&
                            CACHED.equals(owner) &&
                            "processAndSavePrimaryBuffer".equals(methodName) &&
                            "()V".equals(methodDescriptor)) {

                            vendorFlushCalls[0]++;

                            // Existing stack: [CachedDataListManager].
                            // Local 0 is the DataManager established at the
                            // beginning of stock stopCapturing().
                            super.visitVarInsn(ALOAD, 0);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                SUPPORT,
                                "captureBeforeStopFlush",
                                "(Ljava/lang/Object;)V",
                                false
                            );
                            captureInserted[0]++;

                            // Vendor finalization executes exactly as stock.
                            super.visitMethodInsn(
                                opcode,
                                owner,
                                methodName,
                                methodDescriptor,
                                isInterface
                            );

                            // Restore UI-facing primary graph contents and
                            // refresh once after the vendor call returns.
                            super.visitVarInsn(ALOAD, 0);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                SUPPORT,
                                "restoreAfterStopFlush",
                                "(Ljava/lang/Object;)V",
                                false
                            );
                            restoreInserted[0]++;
                            return;
                        }

                        super.visitMethodInsn(
                            opcode,
                            owner,
                            methodName,
                            methodDescriptor,
                            isInterface
                        );
                    }
                };
            }
        };

        reader.accept(cv, 0);

        if (targetMethods[0] != 1) {
            fail("stopCapturing methods", 1, targetMethods[0]);
        }
        if (vendorFlushCalls[0] != 1) {
            fail("processAndSavePrimaryBuffer calls", 1, vendorFlushCalls[0]);
        }
        if (captureInserted[0] != 1) {
            fail("captureBeforeStopFlush inserts", 1, captureInserted[0]);
        }
        if (restoreInserted[0] != 1) {
            fail("restoreAfterStopFlush inserts", 1, restoreInserted[0]);
        }

        byte[] patched = writer.toByteArray();
        Files.write(out, patched);
        System.out.println("Output: " + out);
        System.out.println("Patched SHA256: " + sha256(patched));
    }

    private static void fail(String label, int expected, int actual) {
        throw new IllegalStateException(
            label + ": expected " + expected + ", found " + actual
        );
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
