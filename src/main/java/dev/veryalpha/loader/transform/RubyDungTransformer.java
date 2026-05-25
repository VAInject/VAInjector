package dev.veryalpha.loader.transform;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RubyDungTransformer implements ClassFileTransformer {

    private static final String TARGET_CLASS = "com/mojang/rubydung/RubyDung";
    private static final String HOOK_OWNER = "dev/veryalpha/loader/VAInjectorAPI";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!TARGET_CLASS.equals(className)) {
            return null;
        }
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new RubyDungClassVisitor(cw), 0);
        return cw.toByteArray();
    }

    private static class RubyDungClassVisitor extends ClassVisitor {
        RubyDungClassVisitor(ClassWriter cw) {
            super(Opcodes.ASM5, cw);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (mv == null) {
                return null;
            }
            if (name.equals("init") && desc.equals("()V")) {
                return new HookMethodVisitor(mv, "onInit", true);
            }
            if (name.equals("tick") && desc.equals("()V")) {
                return new HookMethodVisitor(mv, "onTick", false);
            }
            if (name.equals("render") && desc.equals("(F)V")) {
                return new RenderMethodVisitor(mv);
            }
            if (name.equals("destroy") && desc.equals("()V")) {
                return new HookMethodVisitor(mv, "onDestroy", false);
            }
            return mv;
        }
    }

    private static class HookMethodVisitor extends MethodVisitor {
        private final String hookMethod;
        private final boolean insertBeforeReturn;

        HookMethodVisitor(MethodVisitor mv, String hookMethod, boolean insertBeforeReturn) {
            super(Opcodes.ASM5, mv);
            this.hookMethod = hookMethod;
            this.insertBeforeReturn = insertBeforeReturn;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (!insertBeforeReturn) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER, hookMethod, "()V", false);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (insertBeforeReturn && opcode == Opcodes.RETURN) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER, hookMethod, "()V", false);
            }
            super.visitInsn(opcode);
        }
    }
    private static class RenderMethodVisitor extends MethodVisitor {

        RenderMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String desc, boolean itf) {
            if (opcode == Opcodes.INVOKESTATIC
                    && owner.equals("org/lwjgl/opengl/Display")
                    && name.equals("update")
                    && desc.equals("()V")) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, "onRender", "()V", false);
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
