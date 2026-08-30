import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public final class GraphNavigationPatcher implements Opcodes {

    private static final String OWNER =
        "com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";

    private static final String SUPPORT =
        "com/cypress/ezpdanalyzer/ui/jfreechart/GraphNavigationSupport";

    private static final String STOP_SUPPORT =
        "com/cypress/ezpdanalyzer/ui/jfreechart/GraphStopSupport";

    private static final String USB_PACKET =
        "com/cypress/ezpdanalyzer/ui/model/USBPacketData";

    private GraphNavigationPatcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                "Usage: GraphNavigationPatcher <input-class> <output-class>"
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

        final int[] displayMethods = {0};
        final int[] refreshMethods = {0};
        final int[] scrollLeftMethods = {0};

        final int[] usbPacketStores = {0};
        final int[] markerClearInserted = {0};
        final int[] staleAmpRangeCalls = {0};
        final int[] staleAmpRangeRemoved = {0};
        final int[] timeStoresSlot8 = {0};
        final int[] snappedTimeInserted = {0};
        final int[] displayReturns = {0};
        final int[] normalizeInserted = {0};
        final int[] displayStoppedDataInserted = {0};
        final int[] refreshStoppedDataInserted = {0};

        final int[] scrollLeftMaxPointsReads = {0};
        final int[] scrollLeftShiftReads = {0};
        final int[] scrollLeftGuardPatched = {0};

        ClassVisitor visitor = new ClassVisitor(ASM8, writer) {

            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {

                if (!OWNER.equals(name)) {
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

                final boolean displayGraph =
                    "displayGraph".equals(name) &&
                    "(Lorg/eclipse/jface/viewers/ISelection;)V"
                        .equals(descriptor);

                final boolean refreshGraph =
                    "refreshGraph".equals(name) &&
                    "()V".equals(descriptor);

                final boolean scrollLeft =
                    "scrollLeft".equals(name) &&
                    "()V".equals(descriptor);

                if (displayGraph) {
                    displayMethods[0]++;
                }
                if (refreshGraph) {
                    refreshMethods[0]++;
                }
                if (scrollLeft) {
                    scrollLeftMethods[0]++;
                }

                return new MethodVisitor(ASM8, downstream) {

                    private int returnOrdinal = 0;

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor) {

                        if (displayGraph &&
                            opcode == PUTFIELD &&
                            OWNER.equals(owner) &&
                            "usbPacket".equals(fieldName) &&
                            ("L" + USB_PACKET + ";")
                                .equals(fieldDescriptor)) {

                            usbPacketStores[0]++;

                            super.visitFieldInsn(
                                opcode,
                                owner,
                                fieldName,
                                fieldDescriptor
                            );

                            /*
                             * Stock adds new Start/End domain markers on every
                             * row selection but never removes the old ones.
                             * Clear the previous selection marker pair first.
                             */
                            super.visitVarInsn(ALOAD, 0);
                            super.visitMethodInsn(
                                INVOKEVIRTUAL,
                                OWNER,
                                "clearMarkers",
                                "()V",
                                false
                            );
                            markerClearInserted[0]++;
                            return;
                        }

                        if (scrollLeft &&
                            opcode == GETFIELD &&
                            OWNER.equals(owner)) {

                            if ("maxPoints".equals(fieldName) &&
                                "I".equals(fieldDescriptor)) {

                                scrollLeftMaxPointsReads[0]++;

                                /*
                                 * Stock tests:
                                 *   liveStartIdx - maxPoints >= 0
                                 * but then moves by shiftPoints.
                                 *
                                 * With maxPoints=1000, shiftPoints=500 this
                                 * prevents 500 -> 0. Use shiftPoints in the
                                 * guard as intended.
                                 */
                                super.visitFieldInsn(
                                    GETFIELD,
                                    OWNER,
                                    "shiftPoints",
                                    "I"
                                );
                                scrollLeftGuardPatched[0]++;
                                return;
                            }

                            if ("shiftPoints".equals(fieldName) &&
                                "I".equals(fieldDescriptor)) {
                                scrollLeftShiftReads[0]++;
                            }
                        }

                        super.visitFieldInsn(
                            opcode,
                            owner,
                            fieldName,
                            fieldDescriptor
                        );
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String methodName,
                            String methodDescriptor,
                            boolean isInterface) {

                        if (displayGraph &&
                            opcode == INVOKEVIRTUAL &&
                            OWNER.equals(owner) &&
                            "setAmpSeriesRange".equals(methodName) &&
                            "()V".equals(methodDescriptor)) {

                            staleAmpRangeCalls[0]++;

                            /*
                             * Stack is [this]. Stock computes AMP range from
                             * the OLD page before clearGraphData()/replot.
                             * Drop only that call and keep stack balanced.
                             */
                            super.visitInsn(POP);
                            staleAmpRangeRemoved[0]++;
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

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        super.visitVarInsn(opcode, var);

                        /*
                         * Stock displayGraph:
                         *   slot 2 = DataManager
                         *   slot 7 = vendor-selected ScopeData/GraphData List
                         *
                         * Replace only the local List reference.  For a stopped
                         * LIVE capture GraphStopSupport supplies the immutable
                         * STOP snapshot; imported/file paths pass through.
                         */
                        if (displayGraph &&
                            opcode == ASTORE &&
                            var == 7) {

                            super.visitVarInsn(ALOAD, 2);
                            super.visitVarInsn(ALOAD, 7);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                STOP_SUPPORT,
                                "chooseGraphData",
                                "(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;",
                                false
                            );
                            super.visitVarInsn(ASTORE, 7);
                            displayStoppedDataInserted[0]++;
                        }

                        /*
                         * Stock refreshGraph:
                         *   slot 1 = DataManager
                         *   slot 2 = vendor-selected ScopeData/GraphData List
                         *
                         * checkbox redraw and the two graph-scroll buttons all
                         * arrive here, so they receive the same stable snapshot
                         * after STOP.
                         */
                        if (refreshGraph &&
                            opcode == ASTORE &&
                            var == 2) {

                            super.visitVarInsn(ALOAD, 1);
                            super.visitVarInsn(ALOAD, 2);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                STOP_SUPPORT,
                                "chooseGraphData",
                                "(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;",
                                false
                            );
                            super.visitVarInsn(ASTORE, 2);
                            refreshStoppedDataInserted[0]++;
                        }

                        if (displayGraph &&
                            opcode == LSTORE &&
                            var == 8) {

                            timeStoresSlot8[0]++;

                            /*
                             * slot 7 = scopeData, slot 8/9 = selected PD time.
                             * Snap to a real graph sample timestamp so the
                             * stock 1000-point page lookup cannot fall into a
                             * sample/page gap and abandon the selection.
                             */
                            super.visitVarInsn(ALOAD, 7);
                            super.visitVarInsn(LLOAD, 8);
                            super.visitMethodInsn(
                                INVOKESTATIC,
                                SUPPORT,
                                "snapSelectionTime",
                                "(Ljava/util/List;J)J",
                                false
                            );
                            super.visitVarInsn(LSTORE, 8);
                            snappedTimeInserted[0]++;
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (displayGraph && opcode == RETURN) {
                            returnOrdinal++;
                            displayReturns[0]++;

                            /*
                             * Stock displayGraph has exactly four RETURNs.
                             * The fourth is the successful end of the method,
                             * after the selected page has been repopulated.
                             */
                            if (returnOrdinal == 4) {
                                super.visitVarInsn(ALOAD, 0);
                                super.visitFieldInsn(
                                    GETFIELD,
                                    OWNER,
                                    "lineChart",
                                    "Lorg/jfree/chart/JFreeChart;"
                                );
                                super.visitVarInsn(ALOAD, 0);
                                super.visitFieldInsn(
                                    GETFIELD,
                                    OWNER,
                                    "usbPacket",
                                    "L" + USB_PACKET + ";"
                                );
                                super.visitMethodInsn(
                                    INVOKESTATIC,
                                    SUPPORT,
                                    "normalizeAfterSelection",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    false
                                );
                                normalizeInserted[0]++;
                            }
                        }

                        super.visitInsn(opcode);
                    }
                };
            }
        };

        reader.accept(visitor, 0);

        if (displayMethods[0] != 1) {
            fail("displayGraph methods", 1, displayMethods[0]);
        }
        if (refreshMethods[0] != 1) {
            fail("refreshGraph methods", 1, refreshMethods[0]);
        }
        if (scrollLeftMethods[0] != 1) {
            fail("scrollLeft methods", 1, scrollLeftMethods[0]);
        }
        if (usbPacketStores[0] != 1) {
            fail("usbPacket stores", 1, usbPacketStores[0]);
        }
        if (markerClearInserted[0] != 1) {
            fail("clearMarkers inserts", 1, markerClearInserted[0]);
        }
        if (staleAmpRangeCalls[0] != 1) {
            fail(
                "setAmpSeriesRange calls in displayGraph",
                1,
                staleAmpRangeCalls[0]
            );
        }
        if (staleAmpRangeRemoved[0] != 1) {
            fail(
                "removed stale AMP range calls",
                1,
                staleAmpRangeRemoved[0]
            );
        }
        if (timeStoresSlot8[0] != 1) {
            fail("LSTORE slot 8 in displayGraph", 1, timeStoresSlot8[0]);
        }
        if (snappedTimeInserted[0] != 1) {
            fail("snapSelectionTime inserts", 1, snappedTimeInserted[0]);
        }
        if (displayReturns[0] != 4) {
            fail("displayGraph RETURN count", 4, displayReturns[0]);
        }
        if (normalizeInserted[0] != 1) {
            fail(
                "normalizeAfterSelection inserts",
                1,
                normalizeInserted[0]
            );
        }
        if (displayStoppedDataInserted[0] != 1) {
            fail(
                "stopped GraphData hook in displayGraph",
                1,
                displayStoppedDataInserted[0]
            );
        }
        if (refreshStoppedDataInserted[0] != 1) {
            fail(
                "stopped GraphData hook in refreshGraph",
                1,
                refreshStoppedDataInserted[0]
            );
        }
        if (scrollLeftMaxPointsReads[0] != 1) {
            fail(
                "scrollLeft maxPoints reads",
                1,
                scrollLeftMaxPointsReads[0]
            );
        }
        if (scrollLeftShiftReads[0] != 1) {
            fail(
                "stock scrollLeft shiftPoints reads",
                1,
                scrollLeftShiftReads[0]
            );
        }
        if (scrollLeftGuardPatched[0] != 1) {
            fail(
                "scrollLeft guard patches",
                1,
                scrollLeftGuardPatched[0]
            );
        }

        byte[] patched = writer.toByteArray();
        Files.write(output, patched);

        System.out.println(
            "Inserted clearMarkers()         : " +
            markerClearInserted[0]
        );
        System.out.println(
            "Removed stale AMP range call    : " +
            staleAmpRangeRemoved[0]
        );
        System.out.println(
            "Inserted timestamp snap         : " +
            snappedTimeInserted[0]
        );
        System.out.println(
            "Inserted post-selection normalize: " +
            normalizeInserted[0]
        );
        System.out.println(
            "Stable STOP data in displayGraph: " +
            displayStoppedDataInserted[0]
        );
        System.out.println(
            "Stable STOP data in refreshGraph: " +
            refreshStoppedDataInserted[0]
        );
        System.out.println(
            "Fixed scrollLeft guard          : " +
            scrollLeftGuardPatched[0]
        );
        System.out.println("Output: " + output);
        System.out.println("Patched SHA256: " + sha256(patched));
    }

    private static void fail(
            String label,
            int expected,
            int actual) {

        throw new IllegalStateException(
            label + ": expected " + expected +
            ", found " + actual
        );
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md =
            MessageDigest.getInstance("SHA-256");

        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();

        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xFF));
        }

        return sb.toString();
    }
}
