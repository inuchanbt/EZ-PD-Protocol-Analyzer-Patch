import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public final class GraphScrollRightPatcher implements Opcodes {

    private static final String OWNER =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
    private static final String DATA_MANAGER =
        "com/cypress/ezpdanalyzer/ui/util/DataManager";
    private static final String STOP_SUPPORT =
        "com/cypress/ezpdanalyzer/ui/jfreechart/GraphStopSupport";

    private GraphScrollRightPatcher() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                "Usage: GraphScrollRightPatcher <input-class> <output-class>"
            );
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        byte[] original = Files.readAllBytes(input);

        ClassReader reader = new ClassReader(original);
        ClassWriter writer =
            new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        final int[] targetMethods = {0};
        final int[] scopeCalls = {0};
        final int[] graphCalls = {0};
        final int[] originalArrayListSizeCalls = {0};
        final int[] replacements = {0};

        ClassVisitor visitor = new ClassVisitor(ASM8, writer) {
            @Override
            public void visit(
                    int version, int access, String name, String signature,
                    String superName, String[] interfaces) {

                if (!OWNER.equals(name)) {
                    throw new IllegalStateException(
                        "Unexpected class: " + name
                    );
                }
                super.visit(
                    version, access, name, signature, superName, interfaces
                );
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {

                MethodVisitor downstream =
                    super.visitMethod(
                        access, name, descriptor, signature, exceptions
                    );

                if (!"scrollRight".equals(name) ||
                    !"()V".equals(descriptor)) {
                    return downstream;
                }

                targetMethods[0]++;

                return new MethodVisitor(ASM8, downstream) {
                    private boolean suppressNextSize = false;

                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {

                        if (opcode == INVOKEVIRTUAL &&
                            DATA_MANAGER.equals(owner) &&
                            "getScopeDatas".equals(methodName) &&
                            "()Ljava/util/ArrayList;".equals(methodDescriptor)) {
                            scopeCalls[0]++;
                        }

                        if (opcode == INVOKEVIRTUAL &&
                            DATA_MANAGER.equals(owner) &&
                            "getGraphDatas".equals(methodName) &&
                            "()Ljava/util/ArrayList;".equals(methodDescriptor)) {

                            graphCalls[0]++;

                            // Preserve stock getGraphDatas().
                            super.visitMethodInsn(
                                opcode, owner, methodName,
                                methodDescriptor, isInterface
                            );

                            // [ArrayList] -> [DataManager, ArrayList]
                            super.visitVarInsn(ALOAD, 1);
                            super.visitInsn(SWAP);

                            // Replace only the following size result.
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                STOP_SUPPORT,
                                "graphDataSizeForScrollRight",
                                "(Ljava/lang/Object;Ljava/util/List;)I",
                                false
                            );

                            suppressNextSize = true;
                            replacements[0]++;
                            return;
                        }

                        if (opcode == INVOKEVIRTUAL &&
                            "java/util/ArrayList".equals(owner) &&
                            "size".equals(methodName) &&
                            "()I".equals(methodDescriptor)) {

                            originalArrayListSizeCalls[0]++;

                            if (suppressNextSize) {
                                suppressNextSize = false;
                                // helper already left replacement int on stack
                                return;
                            }
                        }

                        super.visitMethodInsn(
                            opcode, owner, methodName,
                            methodDescriptor, isInterface
                        );
                    }

                    @Override
                    public void visitEnd() {
                        if (suppressNextSize) {
                            throw new IllegalStateException(
                                "getGraphDatas() not followed by ArrayList.size()"
                            );
                        }
                        super.visitEnd();
                    }
                };
            }
        };

        reader.accept(visitor, 0);

        check("scrollRight methods", 1, targetMethods[0]);
        check("getScopeDatas calls", 1, scopeCalls[0]);
        check("getGraphDatas calls", 1, graphCalls[0]);
        check("original ArrayList.size calls", 2, originalArrayListSizeCalls[0]);
        check("stable-size replacements", 1, replacements[0]);

        Files.write(output, writer.toByteArray());

        System.out.println("scrollRight getScopeDatas() : " + scopeCalls[0]);
        System.out.println("scrollRight getGraphDatas() : " + graphCalls[0]);
        System.out.println("stable STOP size replacement: " + replacements[0]);
    }

    private static void check(String what, int expected, int actual) {
        if (actual != expected) {
            throw new IllegalStateException(
                "Expected exactly " + expected + " " + what +
                "; found " + actual
            );
        }
    }
}
