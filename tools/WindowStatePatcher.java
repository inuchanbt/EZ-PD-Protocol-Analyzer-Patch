import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;

/** Removes only the vendor post-open Shell.setMaximized(true) override. */
public final class WindowStatePatcher {
  private static final String OWNER="com/cypress/ezpdanalyzer/appln/ApplicationWorkbenchWindowAdvisor";
  public static void main(String[] a) throws Exception {
    if(a.length!=2) throw new IllegalArgumentException("usage: WindowStatePatcher in out");
    ClassReader r=new ClassReader(Files.readAllBytes(Path.of(a[0])));
    if(!OWNER.equals(r.getClassName())) throw new IllegalStateException("Unexpected class: "+r.getClassName());
    final int[] removed={0}; ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);
    r.accept(new ClassVisitor(Opcodes.ASM8,w){ public MethodVisitor visitMethod(int x,String n,String d,String s,String[] e){
      MethodVisitor v=super.visitMethod(x,n,d,s,e); if(!"postWindowOpen".equals(n)||!"()V".equals(d))return v;
      return new MethodVisitor(Opcodes.ASM8,v){ public void visitMethodInsn(int op,String o,String name,String desc,boolean itf){
        if(op==Opcodes.INVOKEVIRTUAL&&"org/eclipse/swt/widgets/Shell".equals(o)&&"setMaximized".equals(name)&&"(Z)V".equals(desc)){super.visitInsn(Opcodes.POP2);removed[0]++;}else super.visitMethodInsn(op,o,name,desc,itf);
      }};
    }},0);
    if(removed[0]!=1)throw new IllegalStateException("Expected one forced maximize call, found "+removed[0]);
    Files.write(Path.of(a[1]),w.toByteArray()); System.out.println("Removed forced maximize override.");
  }
}
