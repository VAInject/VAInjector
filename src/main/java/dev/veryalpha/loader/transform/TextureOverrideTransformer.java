package dev.veryalpha.loader.transform;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TextureOverrideTransformer implements ClassFileTransformer {

    private static final String TARGET_CLASS = "com/mojang/rubydung/Textures";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!TARGET_CLASS.equals(className)) {
            return null;
        }
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new TexturesClassVisitor(cw), 0);
        return cw.toByteArray();
    }

    private static class TexturesClassVisitor extends ClassVisitor {
        TexturesClassVisitor(ClassWriter cw) {
            super(Opcodes.ASM5, cw);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("loadTexture") && desc.equals("(Ljava/lang/String;I)I")) {
                return new LoadTextureMethodVisitor(mv);
            }
            return mv;
        }
    }

    private static class LoadTextureMethodVisitor extends MethodVisitor {
        private boolean pendingReplace = false;

        LoadTextureMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && owner.equals("java/lang/Class")
                    && name.equals("getResourceAsStream")) {
                pendingReplace = true;
                super.visitInsn(Opcodes.SWAP);
                super.visitInsn(Opcodes.POP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "dev/veryalpha/loader/ResourceOverride",
                        "loadImage",
                        "(Ljava/lang/String;)Ljava/awt/image/BufferedImage;",
                        false);
                return;
            }
            if (pendingReplace
                    && opcode == Opcodes.INVOKESTATIC
                    && owner.equals("javax/imageio/ImageIO")
                    && name.equals("read")) {
                pendingReplace = false;
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
