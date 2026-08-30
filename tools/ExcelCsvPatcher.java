import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * EZ-PD Protocol Analyzer Utility 4.2.0
 * CSV End Time + Sno bytecode patcher, test v5.
 *
 * Fixes:
 *  1. CSV End Time: second USBPacketData.getsTime() -> geteTime()
 *  2. CSV Sno: USBPacketData.getSno() -> 1-based row number of the
 *     current FilterList iteration (matching the GUI "#" column).
 *
 * v5 deliberately stores the export row counter in a new private instance
 * field instead of a new local-variable slot. This avoids StackMapTable /
 * local-type interactions in the stock Java 17 class.
 *
 * No Infineon/Cypress class or source is embedded here.
 */
public final class ExcelCsvPatcher {
    private static final int ASM_API = Opcodes.ASM8;

    private static final String TARGET_CLASS =
        "com/cypress/ezpdanalyzer/ui/handler/ExcelExportHandler$1";
    private static final String TARGET_METHOD = "run";
    private static final String TARGET_METHOD_DESC =
        "(Lorg/eclipse/core/runtime/IProgressMonitor;)Lorg/eclipse/core/runtime/IStatus;";

    private static final String USB_PACKET_DATA =
        "com/cypress/ezpdanalyzer/ui/model/USBPacketData";
    private static final String FILTER_LIST =
        "ca/odell/glazedlists/FilterList";

    private static final String STRING_DESC = "()Ljava/lang/String;";
    private static final String ITERATOR_DESC = "()Ljava/util/Iterator;";

    private static final String COUNTER_FIELD = "__ezpdCsvRowCounter";

    private static final String EXPECTED_ORIGINAL_SHA256 =
        "fb25f1ff39e0658ca87cc338457cf7432fb5c9d6c71c562c52eb265f1367106f";

    private ExcelCsvPatcher() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println(
                "Usage: java ExcelCsvPatcher <input.class> <output.class> [--force]"
            );
            System.exit(2);
        }

        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        boolean force = args.length == 3 && "--force".equals(args[2]);
        if (args.length == 3 && !force) {
            throw new IllegalArgumentException("Unknown option: " + args[2]);
        }

        byte[] original = Files.readAllBytes(in);
        String originalSha = sha256(original);

        System.out.println("Input : " + in);
        System.out.println("SHA256: " + originalSha);

        if (!EXPECTED_ORIGINAL_SHA256.equalsIgnoreCase(originalSha) && !force) {
            throw new IllegalStateException(
                "Target class SHA-256 does not match the confirmed stock 4.2.0 class.\n" +
                "Expected: " + EXPECTED_ORIGINAL_SHA256 + "\n" +
                "Actual  : " + originalSha + "\n" +
                "No output written. Restore the stock class/JAR first."
            );
        }

        ClassReader reader = new ClassReader(original);
        if (!TARGET_CLASS.equals(reader.getClassName())) {
            throw new IllegalStateException("Unexpected class: " + reader.getClassName());
        }

        // We add instructions and one field, but do not change control-flow.
        // COMPUTE_MAXS is enough; existing frames remain valid because the
        // counter is an instance field, not a new local variable.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        int[] targetMethodCount = {0};
        int[] getsTimeCalls = {0};
        int[] endTimePatched = {0};
        int[] getSnoCalls = {0};
        int[] snoPatched = {0};
        int[] iteratorCalls = {0};
        int[] counterInitInserted = {0};
        int[] counterFieldExisting = {0};

        ClassVisitor cv = new ClassVisitor(ASM_API, writer) {
            @Override
            public FieldVisitor visitField(
                int access, String name, String descriptor,
                String signature, Object value
            ) {
                if (COUNTER_FIELD.equals(name)) {
                    counterFieldExisting[0]++;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public void visitEnd() {
                if (counterFieldExisting[0] != 0) {
                    throw new IllegalStateException(
                        "Counter field already exists; class is not stock/unpatched."
                    );
                }
                FieldVisitor fv = super.visitField(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC,
                    COUNTER_FIELD,
                    "I",
                    null,
                    null
                );
                if (fv != null) {
                    fv.visitEnd();
                }
                super.visitEnd();
            }

            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor,
                String signature, String[] exceptions
            ) {
                MethodVisitor mv = super.visitMethod(
                    access, name, descriptor, signature, exceptions
                );

                if (!TARGET_METHOD.equals(name) ||
                    !TARGET_METHOD_DESC.equals(descriptor)) {
                    return mv;
                }

                targetMethodCount[0]++;

                return new MethodVisitor(ASM_API, mv) {
                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String methodName,
                        String methodDescriptor, boolean isInterface
                    ) {
                        // Initialize row counter exactly once, immediately
                        // before the FilterList iterator is created.
                        if (opcode == Opcodes.INVOKEVIRTUAL &&
                            FILTER_LIST.equals(owner) &&
                            "iterator".equals(methodName) &&
                            ITERATOR_DESC.equals(methodDescriptor)) {

                            iteratorCalls[0]++;

                            // Stack on entry: [filterList]
                            // Preserve it while executing:
                            //   this.__ezpdCsvRowCounter = 1;
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitFieldInsn(
                                Opcodes.PUTFIELD,
                                TARGET_CLASS,
                                COUNTER_FIELD,
                                "I"
                            );
                            // Stack is still [filterList].
                            counterInitInserted[0]++;
                        }

                        // End Time bug: only the second getsTime() is wrong.
                        if (opcode == Opcodes.INVOKEVIRTUAL &&
                            USB_PACKET_DATA.equals(owner) &&
                            "getsTime".equals(methodName) &&
                            STRING_DESC.equals(methodDescriptor)) {

                            getsTimeCalls[0]++;

                            if (getsTimeCalls[0] == 2) {
                                super.visitMethodInsn(
                                    opcode, owner, "geteTime",
                                    methodDescriptor, isInterface
                                );
                                endTimePatched[0]++;
                                return;
                            }
                        }

                        // Sno bug:
                        // stock stack here is [packet].
                        // Discard packet and leave Integer.toString(counter++)
                        // on the stack, exactly where getSno() returned String.
                        if (opcode == Opcodes.INVOKEVIRTUAL &&
                            USB_PACKET_DATA.equals(owner) &&
                            "getSno".equals(methodName) &&
                            STRING_DESC.equals(methodDescriptor)) {

                            getSnoCalls[0]++;

                            super.visitInsn(Opcodes.POP);            // [ ]

                            // String value = Integer.toString(this.counter);
                            super.visitVarInsn(Opcodes.ALOAD, 0);   // [this]
                            super.visitFieldInsn(
                                Opcodes.GETFIELD,
                                TARGET_CLASS,
                                COUNTER_FIELD,
                                "I"
                            );                                      // [counter]
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "java/lang/Integer",
                                "toString",
                                "(I)Ljava/lang/String;",
                                false
                            );                                      // [String]

                            // Keep String on stack while counter increments.
                            super.visitVarInsn(Opcodes.ALOAD, 0);   // [String,this]
                            super.visitInsn(Opcodes.DUP);           // [String,this,this]
                            super.visitFieldInsn(
                                Opcodes.GETFIELD,
                                TARGET_CLASS,
                                COUNTER_FIELD,
                                "I"
                            );                                      // [String,this,counter]
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitInsn(Opcodes.IADD);          // [String,this,counter+1]
                            super.visitFieldInsn(
                                Opcodes.PUTFIELD,
                                TARGET_CLASS,
                                COUNTER_FIELD,
                                "I"
                            );                                      // [String]

                            snoPatched[0]++;
                            return;
                        }

                        super.visitMethodInsn(
                            opcode, owner, methodName,
                            methodDescriptor, isInterface
                        );
                    }
                };
            }
        };

        reader.accept(cv, 0);

        if (targetMethodCount[0] != 1) {
            throw new IllegalStateException(
                "Expected one target run() method, found " + targetMethodCount[0]
            );
        }
        if (iteratorCalls[0] != 1 || counterInitInserted[0] != 1) {
            throw new IllegalStateException(
                "FilterList iterator pattern mismatch: iterator=" +
                iteratorCalls[0] + ", counterInit=" + counterInitInserted[0]
            );
        }
        if (getsTimeCalls[0] != 2 || endTimePatched[0] != 1) {
            throw new IllegalStateException(
                "End Time pattern mismatch: getsTime=" +
                getsTimeCalls[0] + ", patched=" + endTimePatched[0]
            );
        }
        if (getSnoCalls[0] != 1 || snoPatched[0] != 1) {
            throw new IllegalStateException(
                "Sno pattern mismatch: getSno=" +
                getSnoCalls[0] + ", patched=" + snoPatched[0]
            );
        }

        byte[] patched = writer.toByteArray();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, patched);

        System.out.println("Verified target run() methods   : " + targetMethodCount[0]);
        System.out.println("Verified FilterList.iterator()  : " + iteratorCalls[0]);
        System.out.println("Inserted counter init           : " + counterInitInserted[0]);
        System.out.println("Verified getsTime() calls       : " + getsTimeCalls[0]);
        System.out.println("Changed second call to geteTime : " + endTimePatched[0]);
        System.out.println("Verified getSno() calls         : " + getSnoCalls[0]);
        System.out.println("Replaced getSno() with counter  : " + snoPatched[0]);
        System.out.println("Output: " + out);
        System.out.println("Patched SHA256: " + sha256(patched));
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }
}
