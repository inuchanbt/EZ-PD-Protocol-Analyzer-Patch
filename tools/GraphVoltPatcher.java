import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public final class GraphVoltPatcher implements Opcodes {

    private static final String TARGET_CLASS =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";

    private static final String IDATA =
        "com/cypress/ezpdanalyzer/ui/model/IData";

    private static int voltCalls;
    private static int ampCalls;
    private static int patchedVoltCalls;
    private static int existingIand;
    private static int existing65535;

    private GraphVoltPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                "Usage: GraphVoltPatcher <input-class> <output-class>"
            );
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        byte[] original = Files.readAllBytes(input);

        System.out.println("Input : " + input);
        System.out.println("SHA256: " + sha256(original));

        ClassReader reader = new ClassReader(original);
        ClassWriter writer =
            new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor =
            new ClassVisitor(ASM8, writer) {

                private boolean correctClass;

                @Override
                public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String superName,
                        String[] interfaces) {

                    correctClass = TARGET_CLASS.equals(name);

                    if (!correctClass) {
                        throw new IllegalStateException(
                            "Unexpected class: " + name
                        );
                    }

                    super.visit(
                        version,
                        access,
                        name,
                        signature,
                        superName,
                        interfaces
                    );
                }

                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {

                    MethodVisitor downstream =
                        super.visitMethod(
                            access,
                            name,
                            descriptor,
                            signature,
                            exceptions
                        );

                    return new MethodVisitor(ASM8, downstream) {

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String methodName,
                                String methodDescriptor,
                                boolean isInterface) {

                            boolean isVolt =
                                opcode == INVOKEINTERFACE &&
                                IDATA.equals(owner) &&
                                "getVolt".equals(methodName) &&
                                "()S".equals(methodDescriptor);

                            boolean isAmp =
                                opcode == INVOKEINTERFACE &&
                                IDATA.equals(owner) &&
                                "getAmp".equals(methodName) &&
                                "()S".equals(methodDescriptor);

                            if (isVolt) {
                                voltCalls++;
                            }

                            if (isAmp) {
                                ampCalls++;
                            }

                            super.visitMethodInsn(
                                opcode,
                                owner,
                                methodName,
                                methodDescriptor,
                                isInterface
                            );

                            if (isVolt) {
                                /*
                                 * IData.getVolt() is declared as short, but the
                                 * graph producer stores unsigned 16-bit VBUS mV
                                 * values in it. Java sign-extends short values
                                 * >= 0x8000, so 32.768V+ becomes negative.
                                 *
                                 * Reinterpret ONLY VBUS as unsigned 16-bit:
                                 *
                                 *     getVolt() & 0xFFFF
                                 *
                                 * AMP is deliberately untouched.
                                 */
                                super.visitLdcInsn(
                                    Integer.valueOf(0xFFFF)
                                );
                                super.visitInsn(IAND);
                                patchedVoltCalls++;

                                System.out.println(
                                    "Patched VBUS getVolt() in method: " +
                                    name + descriptor
                                );
                            }
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == IAND) {
                                existingIand++;
                            }
                            super.visitInsn(opcode);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof Integer &&
                                ((Integer) value).intValue() == 0xFFFF) {

                                existing65535++;
                            }
                            super.visitLdcInsn(value);
                        }
                    };
                }
            };

        reader.accept(visitor, 0);

        /*
         * Structural guard for the confirmed 4.2.0 class:
         * exactly two VBUS plotting reads and two AMP reads.
         * The stock class contains no IAND and no 65535 LDC.
         */
        if (voltCalls != 2) {
            throw new IllegalStateException(
                "Expected exactly 2 IData.getVolt():short calls; found " +
                voltCalls
            );
        }

        if (ampCalls != 2) {
            throw new IllegalStateException(
                "Expected exactly 2 IData.getAmp():short calls; found " +
                ampCalls
            );
        }

        if (patchedVoltCalls != 2) {
            throw new IllegalStateException(
                "Expected to patch exactly 2 VBUS calls; patched " +
                patchedVoltCalls
            );
        }

        if (existingIand != 0) {
            throw new IllegalStateException(
                "Stock-class guard failed: existing IAND count is " +
                existingIand
            );
        }

        if (existing65535 != 0) {
            throw new IllegalStateException(
                "Stock-class guard failed: existing int 65535 LDC count is " +
                existing65535
            );
        }

        byte[] patched = writer.toByteArray();
        Files.write(output, patched);

        System.out.println(
            "Verified IData.getVolt():S calls : " + voltCalls
        );
        System.out.println(
            "Verified IData.getAmp():S calls  : " + ampCalls
        );
        System.out.println(
            "Inserted unsigned masks          : " + patchedVoltCalls
        );
        System.out.println("Output: " + output);
        System.out.println("Patched SHA256: " + sha256(patched));
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
