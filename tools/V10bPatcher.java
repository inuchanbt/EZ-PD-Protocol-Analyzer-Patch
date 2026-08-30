import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/** Minimal semantic patcher for the exact v0.9 CyXYLineChart. */
public final class V10bPatcher {
    private static final String TARGET = "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
    private static final String HELPER = "com/cypress/ezpdanalyzer/ui/jfreechart/DomainAxisSupport";
    private static final String CHART_FACTORY = "org/jfree/chart/ChartFactory";
    private static final String DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/jfree/data/xy/XYDataset;Lorg/jfree/chart/plot/PlotOrientation;ZZZ)Lorg/jfree/chart/JFreeChart;";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: V10bPatcher input.class output.class");
        }
        byte[] input = Files.readAllBytes(Path.of(args[0]));
        ClassReader cr = new ClassReader(input);
        if (!TARGET.equals(cr.getClassName())) {
            throw new IllegalStateException("unexpected class: " + cr.getClassName());
        }

        AtomicInteger targetMethods = new AtomicInteger();
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicInteger existingHooks = new AtomicInteger();
        AtomicInteger insertedHooks = new AtomicInteger();

        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM8, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"createXYLineChart".equals(name)
                        || !"()Lorg/jfree/chart/JFreeChart;".equals(descriptor)) {
                    return base;
                }
                targetMethods.incrementAndGet();
                return new MethodVisitor(Opcodes.ASM8, base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                            String methodDesc, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && HELPER.equals(owner)
                                && "install".equals(methodName)
                                && "(Lorg/jfree/chart/JFreeChart;)Lorg/jfree/chart/JFreeChart;".equals(methodDesc)) {
                            existingHooks.incrementAndGet();
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                        if (opcode == Opcodes.INVOKESTATIC && CHART_FACTORY.equals(owner)
                                && "createXYLineChart".equals(methodName) && DESC.equals(methodDesc)) {
                            factoryCalls.incrementAndGet();
                            if (existingHooks.get() == 0) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        HELPER, "install",
                                        "(Lorg/jfree/chart/JFreeChart;)Lorg/jfree/chart/JFreeChart;",
                                        false);
                                insertedHooks.incrementAndGet();
                            }
                        }
                    }
                };
            }
        };
        cr.accept(cv, 0);

        if (targetMethods.get() != 1) {
            throw new IllegalStateException("expected one createXYLineChart method, got " + targetMethods.get());
        }
        if (factoryCalls.get() != 1) {
            throw new IllegalStateException("expected one ChartFactory.createXYLineChart call, got " + factoryCalls.get());
        }
        if (existingHooks.get() > 1) {
            throw new IllegalStateException("too many existing DomainAxisSupport hooks: " + existingHooks.get());
        }
        if (existingHooks.get() == 0 && insertedHooks.get() != 1) {
            throw new IllegalStateException("expected one inserted hook, got " + insertedHooks.get());
        }

        byte[] output = cw.toByteArray();
        Files.write(Path.of(args[1]), output);
        System.out.println("Input bytes   : " + input.length);
        System.out.println("Output bytes  : " + output.length);
        System.out.println("Target methods: " + targetMethods.get());
        System.out.println("Factory calls : " + factoryCalls.get());
        System.out.println("Existing hooks: " + existingHooks.get());
        System.out.println("Inserted hooks: " + insertedHooks.get());
    }
}
