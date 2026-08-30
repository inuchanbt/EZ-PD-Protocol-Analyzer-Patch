import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Updates graph legend labels and raises only their text baseline by 4px. */
public final class LegendPatcher {
    private static final String CHART =
            "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
    private static final String SUPPORT =
            "com/cypress/ezpdanalyzer/ui/jfreechart/LegendTextBaselineSupport";
    private static final String LEGEND_TITLE = "org/jfree/chart/title/LegendTitle";

    private static final Map<String, String> ORIGINAL_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> UPDATED_LABELS = new LinkedHashMap<>();
    static {
        ORIGINAL_LABELS.put("createCC1Dataset", "CC1");
        ORIGINAL_LABELS.put("createCC2Dataset", "CC2");
        ORIGINAL_LABELS.put("createVBUSDataset", "VBUS");
        ORIGINAL_LABELS.put("createAMPDataset", "AMP");
        UPDATED_LABELS.put("createCC1Dataset", "CC1(mV)");
        UPDATED_LABELS.put("createCC2Dataset", "CC2(mV)");
        UPDATED_LABELS.put("createVBUSDataset", "VBUS(mV)");
        UPDATED_LABELS.put("createAMPDataset", "VBUS(mA)");
    }

    private LegendPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: LegendPatcher <input.class> <output.class>");
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        byte[] original = Files.readAllBytes(input);
        ClassReader reader = new ClassReader(original);
        if (!CHART.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }

        final boolean[] alreadyAdjusted = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM8) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM8) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner,
                            String calledName, String calledDescriptor,
                            boolean isInterface) {
                        if (SUPPORT.equals(owner) && "adjust".equals(calledName)) {
                            alreadyAdjusted[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        Map<String, Integer> seen = new LinkedHashMap<>();
        for (String method : UPDATED_LABELS.keySet()) {
            seen.put(method, 0);
        }
        final int[] baselineHooks = {0};
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                MethodVisitor downstream = super.visitMethod(
                        access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM8, downstream) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        String originalLabel = ORIGINAL_LABELS.get(name);
                        String updatedLabel = UPDATED_LABELS.get(name);
                        if (originalLabel != null && value instanceof String
                                && (originalLabel.equals(value)
                                || updatedLabel.equals(value))) {
                            seen.put(name, seen.get(name) + 1);
                            super.visitLdcInsn(updatedLabel);
                            return;
                        }
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner,
                            String calledName, String calledDescriptor,
                            boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, calledName,
                                calledDescriptor, isInterface);
                        if (!alreadyAdjusted[0] && "changeLegend".equals(name)
                                && "(Lorg/jfree/chart/plot/XYPlot;)V".equals(descriptor)
                                && opcode == Opcodes.INVOKEVIRTUAL
                                && LEGEND_TITLE.equals(owner)
                                && "setItemFont".equals(calledName)
                                && "(Ljava/awt/Font;)V".equals(calledDescriptor)) {
                            super.visitVarInsn(Opcodes.ALOAD, 2);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT,
                                    "adjust",
                                    "(Lorg/jfree/chart/title/LegendTitle;)V",
                                    false);
                            baselineHooks[0]++;
                        }
                    }
                };
            }
        };
        reader.accept(visitor, 0);

        for (Map.Entry<String, Integer> entry : seen.entrySet()) {
            if (entry.getValue() != 1) {
                throw new IllegalStateException("Expected one legend label in "
                        + entry.getKey() + "; found " + entry.getValue());
            }
        }
        if (!alreadyAdjusted[0] && baselineHooks[0] != 1) {
            throw new IllegalStateException("Expected one legend baseline hook; found "
                    + baselineHooks[0]);
        }
        Files.write(output, writer.toByteArray());
        System.out.println("Patched legend labels and text baseline.");
    }
}
