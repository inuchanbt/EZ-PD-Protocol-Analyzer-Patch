import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Register the chart for STOP refresh and apply non-destructive cosmetics. */
public final class GraphUiPatcher implements Opcodes {

    private static final String OWNER =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
    private static final String STOP_SUPPORT =
        "com/cypress/ezpdanalyzer/ui/jfreechart/GraphStopSupport";
    private static final String UI_SUPPORT =
        "com/cypress/ezpdanalyzer/ui/jfreechart/GraphUiCosmeticSupport";

    private GraphUiPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                "Usage: GraphUiPatcher <input.class> <output.class>"
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

        final int[] customizeMethods = {0};
        final int[] returns = {0};
        final int[] registerInserted = {0};
        final int[] cosmeticInserted = {0};

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

                if (!"customizeChart".equals(name) ||
                    !"(Lorg/jfree/chart/JFreeChart;)V".equals(descriptor)) {
                    return mv;
                }

                customizeMethods[0]++;

                return new MethodVisitor(ASM8, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == RETURN) {
                            returns[0]++;

                            // this = CyXYLineChart instance used by the view.
                            super.visitVarInsn(ALOAD, 0);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                STOP_SUPPORT,
                                "registerChart",
                                "(Ljava/lang/Object;)V",
                                false
                            );
                            registerInserted[0]++;

                            // arg1 = JFreeChart.  Cosmetic helper touches only
                            // tick label formatting on right-side axes.
                            super.visitVarInsn(ALOAD, 1);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                UI_SUPPORT,
                                "configureChart",
                                "(Ljava/lang/Object;)V",
                                false
                            );
                            cosmeticInserted[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };

        reader.accept(cv, 0);

        if (customizeMethods[0] != 1) {
            fail("customizeChart methods", 1, customizeMethods[0]);
        }
        if (returns[0] != 1) {
            fail("customizeChart RETURN count", 1, returns[0]);
        }
        if (registerInserted[0] != 1) {
            fail("registerChart inserts", 1, registerInserted[0]);
        }
        if (cosmeticInserted[0] != 1) {
            fail("configureChart inserts", 1, cosmeticInserted[0]);
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
