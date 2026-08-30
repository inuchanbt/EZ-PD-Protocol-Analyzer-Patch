import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;

/** Replaces only the VBUS/AMP NumberAxis constructors with RightAlignedNumberAxis. */
public final class GraphAxisAlignmentPatcher {
  static final String OWNER="com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart";
  static final String STOCK="org/jfree/chart/axis/NumberAxis";
  static final String RIGHT="com/cypress/ezpdanalyzer/ui/jfreechart/RightAlignedNumberAxis";
  public static void main(String[] a) throws Exception {
    if(a.length!=2) throw new IllegalArgumentException("usage: GraphAxisAlignmentPatcher in out");
    ClassReader r=new ClassReader(Files.readAllBytes(Path.of(a[0])));
    if(!OWNER.equals(r.getClassName())) throw new IllegalStateException("Unexpected class: "+r.getClassName());
    final int[] methods={0}, news={0}, ctors={0};
    ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);
    r.accept(new ClassVisitor(Opcodes.ASM8,w){ public MethodVisitor visitMethod(int x,String n,String d,String s,String[] e){
      MethodVisitor v=super.visitMethod(x,n,d,s,e);
      if(!(("createVBUSAxix".equals(n)||"createAMPAxix".equals(n))&&"(Z)V".equals(d))) return v;
      methods[0]++;
      return new MethodVisitor(Opcodes.ASM8,v){
        public void visitTypeInsn(int op,String type){ if(op==Opcodes.NEW&&STOCK.equals(type)){news[0]++;super.visitTypeInsn(op,RIGHT);}else super.visitTypeInsn(op,type); }
        public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
          if(op==Opcodes.INVOKESPECIAL&&STOCK.equals(owner)&&"<init>".equals(name)&&"(Ljava/lang/String;)V".equals(desc)){ctors[0]++;super.visitMethodInsn(op,RIGHT,name,desc,false);}else super.visitMethodInsn(op,owner,name,desc,itf);
        }};
    }},0);
    if(methods[0]!=2||news[0]!=2||ctors[0]!=2) throw new IllegalStateException("Expected VBUS+AMP replacements; methods="+methods[0]+" new="+news[0]+" ctor="+ctors[0]);
    Files.write(Path.of(a[1]),w.toByteArray());
    System.out.println("Patched VBUS/AMP RightAlignedNumberAxis constructors.");
  }
}
