import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * Applies display-only graph labels to the exact v0.9 CyXYLineChart and
 * GraphSelectorComposite classes.  No data, axis range, or listener logic is
 * changed.
 */
public final class GraphLabelPatcher {
    private static final String CHART =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
    private static final String SELECTOR =
        "com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite";
    private static final String END_LABEL_GAP = "\u2007\u2007\u2007";

    private GraphLabelPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: GraphLabelPatcher <input.class> <output.class>"
            );
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        byte[] original = Files.readAllBytes(input);
        ClassReader reader = new ClassReader(original);
        Map<String, String> replacements = replacementsFor(reader.getClassName());
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (String text : replacements.keySet()) {
            seen.put(text, 0);
        }
        final int[] checkboxWidthHints = {0};

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (SELECTOR.equals(reader.getClassName()) &&
                                "getCheckBoxGridData".equals(name) &&
                                "()Lorg/eclipse/swt/layout/GridData;".equals(descriptor) &&
                                opcode == Opcodes.BIPUSH && operand == 60) {
                            super.visitIntInsn(Opcodes.BIPUSH, 80);
                            checkboxWidthHints[0]++;
                            return;
                        }
                        super.visitIntInsn(opcode, operand);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String &&
                                replacements.containsKey(value)) {
                            String text = (String) value;
                            seen.put(text, seen.get(text) + 1);
                            super.visitLdcInsn(replacements.get(text));
                            return;
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        };

        reader.accept(visitor, 0);

        for (Map.Entry<String, Integer> entry : seen.entrySet()) {
            if (entry.getValue() != 1) {
                throw new IllegalStateException(
                    "Expected exactly one [" + entry.getKey() +
                    "] constant in " + reader.getClassName() +
                    "; found " + entry.getValue()
                );
            }
        }
        if (SELECTOR.equals(reader.getClassName()) && checkboxWidthHints[0] != 1) {
            throw new IllegalStateException(
                "Expected exactly one 60 px checkbox width hint; found " +
                checkboxWidthHints[0]
            );
        }

        Files.write(output, writer.toByteArray());
        System.out.println("Patched display labels in " + reader.getClassName());
    }

    private static Map<String, String> replacementsFor(String className) {
        Map<String, String> result = new LinkedHashMap<>();
        if (CHART.equals(className)) {
            result.put("Time(us)", "Time(µs)");
            result.put("CC1/CC2 - Volt(mV)", "CC1/CC2 (mV)");
            result.put("VBUS - Volt(mV)", "VBUS (mV)");
            result.put("AMP - AMP(mA)", "VBUS (mA)");

            // The two marker labels are anchored outward from their marker.
            // Keep the Start value untouched and push only the End label away
            // from their shared boundary.  This preserves "Time :<value>".
            result.put("Start Time :", "Start Time :");
            result.put("End Time :", END_LABEL_GAP + "End Time :");
            return result;
        }
        if (SELECTOR.equals(className)) {
            result.put("CC1", "CC1(mV)");
            result.put("CC2", "CC2(mV)");
            result.put("VBUS", "VBUS(mV)");
            result.put("AMP", "VBUS(mA)");
            return result;
        }
        throw new IllegalStateException("Unexpected class: " + className);
    }
}
