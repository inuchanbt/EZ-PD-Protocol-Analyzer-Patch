import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public final class GraphCheckboxPatcher implements Opcodes {

    private static final String CHART =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";

    private GraphCheckboxPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println(
                "Usage: GraphCheckboxPatcher " +
                "<input-class> <output-class> <expected-internal-class> " +
                "<set-enable-method> <create-axis-method>"
            );
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String expectedClass = args[2];
        String setEnableMethod = args[3];
        String createAxisMethod = args[4];

        byte[] original = Files.readAllBytes(input);

        System.out.println("Input : " + input);
        System.out.println("SHA256: " + sha256(original));
        System.out.println("Class : " + expectedClass);
        System.out.println("Set   : " + setEnableMethod);
        System.out.println("Axis  : " + createAxisMethod);

        ClassReader reader = new ClassReader(original);
        ClassWriter writer =
            new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        final int[] widgetSelectedMethods = {0};
        final int[] setEnableCalls = {0};
        final int[] refreshCalls = {0};
        final int[] targetAxisCalls = {0};
        final int[] targetAxisCallsInWidgetSelected = {0};
        final int[] anyAxisCalls = {0};
        final int[] patchedCalls = {0};

        ClassVisitor visitor =
            new ClassVisitor(ASM8, writer) {

                @Override
                public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String superName,
                        String[] interfaces) {

                    if (!expectedClass.equals(name)) {
                        throw new IllegalStateException(
                            "Unexpected class: " + name +
                            " (expected " + expectedClass + ")"
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

                    final boolean widgetSelected =
                        "widgetSelected".equals(name) &&
                        "(Lorg/eclipse/swt/events/SelectionEvent;)V"
                            .equals(descriptor);

                    if (widgetSelected) {
                        widgetSelectedMethods[0]++;
                    }

                    return new MethodVisitor(ASM8, downstream) {

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String methodName,
                                String methodDescriptor,
                                boolean isInterface) {

                            if (opcode == INVOKEVIRTUAL &&
                                CHART.equals(owner)) {

                                if (setEnableMethod.equals(methodName) &&
                                    "(Z)V".equals(methodDescriptor)) {
                                    setEnableCalls[0]++;
                                }

                                if ("refreshGraph".equals(methodName) &&
                                    "()V".equals(methodDescriptor)) {
                                    refreshCalls[0]++;
                                }

                                if (methodName.startsWith("create") &&
                                    methodName.endsWith("Axix") &&
                                    "(Z)V".equals(methodDescriptor)) {
                                    anyAxisCalls[0]++;
                                }

                                if (createAxisMethod.equals(methodName) &&
                                    "(Z)V".equals(methodDescriptor)) {

                                    targetAxisCalls[0]++;

                                    if (widgetSelected) {
                                        targetAxisCallsInWidgetSelected[0]++;

                                        /*
                                         * Stock listener stack immediately
                                         * before invokevirtual is:
                                         *
                                         *   [ CyXYLineChart, boolean ]
                                         *
                                         * create*Axix(Z)V consumes both.
                                         * Replace only that invocation with
                                         * POP2, preserving stack balance and
                                         * control flow while preventing the
                                         * axis/dataset/XYSeries recreation.
                                         */
                                        super.visitInsn(POP2);
                                        patchedCalls[0]++;
                                        return;
                                    }
                                }
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

        reader.accept(visitor, 0);

        if (widgetSelectedMethods[0] != 1) {
            throw new IllegalStateException(
                "Expected exactly 1 widgetSelected() method; found " +
                widgetSelectedMethods[0]
            );
        }

        if (setEnableCalls[0] != 2) {
            throw new IllegalStateException(
                "Expected exactly 2 " + setEnableMethod +
                "(Z)V calls; found " + setEnableCalls[0]
            );
        }

        if (refreshCalls[0] != 2) {
            throw new IllegalStateException(
                "Expected exactly 2 refreshGraph() calls; found " +
                refreshCalls[0]
            );
        }

        if (targetAxisCalls[0] != 2 ||
            targetAxisCallsInWidgetSelected[0] != 2) {

            throw new IllegalStateException(
                "Expected exactly 2 " + createAxisMethod +
                "(Z)V calls in widgetSelected(); found total=" +
                targetAxisCalls[0] + ", inWidgetSelected=" +
                targetAxisCallsInWidgetSelected[0]
            );
        }

        if (anyAxisCalls[0] != 2) {
            throw new IllegalStateException(
                "Expected exactly 2 create*Axix(Z)V calls in listener; found " +
                anyAxisCalls[0]
            );
        }

        if (patchedCalls[0] != 2) {
            throw new IllegalStateException(
                "Expected exactly 2 patched calls; patched " +
                patchedCalls[0]
            );
        }

        byte[] patched = writer.toByteArray();
        Files.write(output, patched);

        System.out.println(
            "Verified widgetSelected()       : " +
            widgetSelectedMethods[0]
        );
        System.out.println(
            "Verified set-enable calls       : " +
            setEnableCalls[0]
        );
        System.out.println(
            "Verified refreshGraph calls     : " +
            refreshCalls[0]
        );
        System.out.println(
            "Removed destructive axis calls  : " +
            patchedCalls[0]
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
