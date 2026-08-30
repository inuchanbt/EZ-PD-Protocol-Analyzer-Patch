import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Upgrades only the graph-label classes emitted by the original v1.0d. */
public final class GraphLayoutUpgradePatcher {
    private static final String CHART =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
    private static final String SELECTOR =
        "com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite";
    private static final String OLD_GAP = "\u2007\u2007";
    private static final String NEW_GAP = "\u2007\u2007\u2007";

    private GraphLayoutUpgradePatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "usage: GraphLayoutUpgradePatcher <input.class> <output.class>"
            );
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        ClassReader reader = new ClassReader(Files.readAllBytes(input));
        String owner = reader.getClassName();
        if (!CHART.equals(owner) && !SELECTOR.equals(owner)) {
            throw new IllegalStateException("Unexpected class: " + owner);
        }

        Map<String, String> replacements = new LinkedHashMap<>();
        Map<String, String> expectedStrings = new LinkedHashMap<>();
        if (CHART.equals(owner)) {
            replacements.put("Start Time :" + OLD_GAP, "Start Time :");
            replacements.put(OLD_GAP + "End Time :", NEW_GAP + "End Time :");
            expectedStrings.putAll(replacements);
        } else {
            for (String label : new String[] {
                    "CC1(mV)", "CC2(mV)", "VBUS(mV)", "VBUS(mA)" }) {
                expectedStrings.put(label, label);
            }
        }
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (String value : expectedStrings.keySet()) {
            seen.put(value, 0);
        }
        final int[] widthHints = {0};

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
                        if (SELECTOR.equals(owner) &&
                                "getCheckBoxGridData".equals(name) &&
                                "()Lorg/eclipse/swt/layout/GridData;".equals(descriptor) &&
                                opcode == Opcodes.BIPUSH && operand == 60) {
                            super.visitIntInsn(Opcodes.BIPUSH, 80);
                            widthHints[0]++;
                            return;
                        }
                        super.visitIntInsn(opcode, operand);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String && seen.containsKey(value)) {
                            String text = (String) value;
                            seen.put(text, seen.get(text) + 1);
                            super.visitLdcInsn(replacements.getOrDefault(text, text));
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
                    "] constant; found " + entry.getValue()
                );
            }
        }
        if (SELECTOR.equals(owner) && widthHints[0] != 1) {
            throw new IllegalStateException(
                "Expected exactly one 60 px checkbox width hint; found " +
                widthHints[0]
            );
        }
        Files.write(output, writer.toByteArray());
        System.out.println("Upgraded " + owner);
    }
}
