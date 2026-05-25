package dev.veryalpha.loader.mod;

import dev.veryalpha.mod.ModLogger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class StubGenerator {

    private static final ModLogger LOGGER = new ModLogger("VAInjector");

    static class ClassInfo {
        String internalName;
        String superClass;
        final Set<String> interfaces = new HashSet<>();
        final List<MemberInfo> methods = new ArrayList<>();
        final List<MemberInfo> fields = new ArrayList<>();

        void merge(ClassInfo other) {
            if (other.superClass != null && superClass == null)
                superClass = other.superClass;
            interfaces.addAll(other.interfaces);
            methods.addAll(other.methods);
            fields.addAll(other.fields);
        }
    }

    static class MemberInfo {
        final int access;
        final String name;
        final String descriptor;
        final String signature;
        final String[] exceptions;

        MemberInfo(int access, String name, String descriptor, String signature, String[] exceptions) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
        }

        public boolean equals(Object o) {
            if (!(o instanceof MemberInfo)) return false;
            MemberInfo m = (MemberInfo) o;
            return name.equals(m.name) && descriptor.equals(m.descriptor);
        }
        public int hashCode() {
            return name.hashCode() * 31 + descriptor.hashCode();
        }
    }

    public static Map<String, byte[]> generateStubs(Map<String, byte[]> overrides) {
        Map<String, ClassInfo> refs = collectReferences(overrides);

        Map<String, ClassInfo> missing = new HashMap<>();
        for (Map.Entry<String, ClassInfo> e : refs.entrySet()) {
            String cn = e.getKey();
            if (overrides.containsKey(cn)) continue;
            if (classExists(cn)) continue;
            if (cn.startsWith("java/") || cn.startsWith("javax/")
                || cn.startsWith("jdk/") || cn.startsWith("sun/")
                || cn.startsWith("[") || cn.contains("[")) continue;
            missing.put(cn, e.getValue());
        }

        if (missing.isEmpty()) return new HashMap<>();

        LOGGER.info("Generating stubs for " + missing.size() + " missing class(es)...");
        for (String cn : missing.keySet()) {
            LOGGER.info("  Stub: " + cn.replace('/', '.'));
        }

        for (Map.Entry<String, ClassInfo> e : missing.entrySet()) {
            if (e.getValue().internalName == null) e.getValue().internalName = e.getKey();
        }

        Map<String, byte[]> stubs = new HashMap<>();
        for (Map.Entry<String, ClassInfo> e : missing.entrySet()) {
            try {
                String internalName = e.getKey();
                String dotted = internalName.replace('/', '.');
                byte[] stub = tryCopyFromGameJar(internalName);
                if (stub != null) {
                    LOGGER.info("  + Copied from " + findSourceName(internalName).replace('/', '.'));
                } else {
                    stub = generateStub(e.getValue());
                }
                stubs.put(dotted, stub);
            } catch (Exception ex) {
                LOGGER.error("Failed to generate stub for " + e.getKey() + ": " + ex.getMessage());
            }
        }

        return stubs;
    }

    private static Map<String, ClassInfo> collectReferences(Map<String, byte[]> overrides) {
        Map<String, ClassInfo> refs = new HashMap<>();

        for (byte[] bytes : overrides.values()) {
            try {
                ClassReader cr = new ClassReader(bytes);
                String thisClass = cr.getClassName();
                String superName = cr.getSuperName();
                String[] interfaces = cr.getInterfaces();

                if (superName != null && !superName.equals("java/lang/Object")) {
                    refs.computeIfAbsent(superName, k -> new ClassInfo()).superClass = superName;
                }
                for (String iface : interfaces) {
                    refs.computeIfAbsent(iface, k -> new ClassInfo()).interfaces.add(iface);
                }

                cr.accept(new ClassVisitor(Opcodes.ASM5) {

                    @Override
                    public FieldVisitor visitField(int access, String name, String desc,
                                                    String signature, Object value) {
                        addDescTypes(refs, desc);
                        return null;
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc,
                                                      String signature, String[] exceptions) {
                        addDescTypes(refs, desc);
                        if (exceptions != null) {
                            for (String ex : exceptions) addType(refs, ex);
                        }

                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mName,
                                                        String mDesc, boolean itf) {
                                ClassInfo ci = refs.computeIfAbsent(owner, k -> new ClassInfo());
                                ci.methods.add(new MemberInfo(opcode, mName, mDesc, null, null));
                                addDescTypes(refs, mDesc);
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fName,
                                                       String fDesc) {
                                ClassInfo ci = refs.computeIfAbsent(owner, k -> new ClassInfo());
                                ci.fields.add(new MemberInfo(opcode, fName, fDesc, null, null));
                                addDescTypes(refs, fDesc);
                            }

                            @Override
                            public void visitTypeInsn(int opcode, String type) {
                                if (!type.startsWith("[")) {
                                    addType(refs, type);
                                } else {
                                    Type t = Type.getType(type);
                                    if (t.getSort() == Type.ARRAY) {
                                        Type elem = t.getElementType();
                                        if (elem.getSort() == Type.OBJECT) {
                                            addType(refs, elem.getInternalName());
                                        }
                                    }
                                }
                            }

                            @Override
                            public void visitLdcInsn(Object cst) {
                                if (cst instanceof Type) {
                                    Type t = (Type) cst;
                                    if (t.getSort() == Type.OBJECT) {
                                        addType(refs, t.getInternalName());
                                    }
                                }
                            }

                            @Override
                            public void visitMultiANewArrayInsn(String desc, int dims) {
                                addDescTypes(refs, desc);
                            }
                        };
                    }
                }, 0);

            } catch (Exception e) {
                LOGGER.warn("Failed to analyze class references: " + e.getMessage());
            }
        }

        return refs;
    }

    private static void addType(Map<String, ClassInfo> refs, String internalName) {
        if (internalName == null || internalName.startsWith("[")) return;
        refs.computeIfAbsent(internalName, k -> new ClassInfo());
    }

    private static void addDescTypes(Map<String, ClassInfo> refs, String desc) {
        if (desc == null || desc.isEmpty()) return;
        try {
            Type t = Type.getType(desc);
            collectTypes(refs, t);
        } catch (Exception e) {
        }
    }

    private static void collectTypes(Map<String, ClassInfo> refs, Type t) {
        switch (t.getSort()) {
            case Type.OBJECT:
                addType(refs, t.getInternalName());
                break;
            case Type.ARRAY:
                collectTypes(refs, t.getElementType());
                break;
            case Type.METHOD:
                for (Type arg : t.getArgumentTypes()) collectTypes(refs, arg);
                collectTypes(refs, t.getReturnType());
                break;
        }
    }

    private static boolean classExists(String internalName) {
        String resourceName = internalName + ".class";
        return Thread.currentThread().getContextClassLoader().getResource(resourceName) != null;
    }

    private static byte[] generateStub(ClassInfo info) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String internalName = info.internalName;
        String superName = info.superClass != null ? info.superClass : "java/lang/Object";

        List<MemberInfo> methods = new ArrayList<>(new HashSet<>(info.methods));
        List<MemberInfo> fields  = new ArrayList<>(new HashSet<>(info.fields));

        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null,
                 superName, info.interfaces.toArray(new String[0]));

        generateDefaultCtor(cw);

        boolean hasNoArgCtor = false;
        for (MemberInfo mi : methods) {
            if (mi.name.equals("<init>")) {
                generateBody(cw, Opcodes.ACC_PUBLIC, "<init>", mi.descriptor, mi.exceptions);
                if (mi.descriptor.equals("()V")) hasNoArgCtor = true;
            }
        }

        for (MemberInfo mi : methods) {
            if (mi.name.equals("<init>")) continue;
            if (mi.name.equals("<clinit>")) continue;
            int acc = Opcodes.ACC_PUBLIC;
            if (mi.access == Opcodes.INVOKESTATIC) acc |= Opcodes.ACC_STATIC;
            generateBody(cw, acc, mi.name, mi.descriptor, mi.exceptions);
        }

        for (MemberInfo fi : fields) {
            cw.visitField(Opcodes.ACC_PUBLIC, fi.name, fi.descriptor, null, null);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void generateDefaultCtor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void generateBody(ClassWriter cw, int access, String name,
                                      String descriptor, String[] exceptions) {
        Type methodType = Type.getMethodType(descriptor);
        Type returnType = methodType.getReturnType();

        MethodVisitor mv = cw.visitMethod(access, name, descriptor, null, exceptions);
        mv.visitCode();

        if (name.equals("<init>")) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            String superName = "java/lang/Object";
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        }

        switch (returnType.getSort()) {
            case Type.VOID:
                mv.visitInsn(Opcodes.RETURN);
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
                break;
            case Type.LONG:
                mv.visitInsn(Opcodes.LCONST_0);
                mv.visitInsn(Opcodes.LRETURN);
                break;
            case Type.FLOAT:
                mv.visitInsn(Opcodes.FCONST_0);
                mv.visitInsn(Opcodes.FRETURN);
                break;
            case Type.DOUBLE:
                mv.visitInsn(Opcodes.DCONST_0);
                mv.visitInsn(Opcodes.DRETURN);
                break;
            default:
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
                break;
        }

        mv.visitMaxs(1, computeLocals(descriptor));
        mv.visitEnd();
    }

    private static int computeLocals(String methodDescriptor) {
        Type[] args = Type.getMethodType(methodDescriptor).getArgumentTypes();
        int count = 1;
        for (Type arg : args) {
            count++;
            if (arg.getSort() == Type.LONG || arg.getSort() == Type.DOUBLE) count++;
        }
        return count;
    }

    private static byte[] tryCopyFromGameJar(String internalName) {
        for (String candidate : generateCandidates(internalName)) {
            if (candidate.equals(internalName)) continue;
            String resourcePath = candidate + ".class";
            java.net.URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
            if (url == null) continue;
            try {
                byte[] sourceBytes = readUrlBytes(url);
                LOGGER.info("  Borrowing bytecode from " + candidate.replace('/', '.'));
                return renameClassBytecode(sourceBytes, candidate, internalName);
            } catch (Exception e) {
                LOGGER.warn("  Failed to borrow from " + candidate.replace('/', '.') + ": " + e.getMessage());
            }
        }
        return null;
    }

    private static String findSourceName(String internalName) {
        for (String candidate : generateCandidates(internalName)) {
            if (candidate.equals(internalName)) continue;
            String resourcePath = candidate + ".class";
            java.net.URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
            if (url != null) return candidate;
        }
        return internalName;
    }

    private static List<String> generateCandidates(String internalName) {
        List<String> candidates = new ArrayList<>();
        int lastSlash = internalName.lastIndexOf('/');
        String pkg = lastSlash >= 0 ? internalName.substring(0, lastSlash) : "";
        String simpleName = lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;

        StringBuilder deduped = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            boolean consonant = "aeiouAEIOU".indexOf(c) < 0;
            if (i > 0 && c == simpleName.charAt(i - 1) && consonant) continue;
            deduped.append(c);
        }
        String de = deduped.toString();
        if (!de.equals(simpleName) && !pkg.isEmpty()) {
            candidates.add(pkg + "/" + de);
        }

        candidates.add(internalName);
        return candidates;
    }

    private static byte[] readUrlBytes(java.net.URL url) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream is = url.openStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) >= 0) baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static byte[] renameClassBytecode(byte[] sourceBytes,
                                               String sourceInternal,
                                               String targetInternal) {
        ClassReader cr = new ClassReader(sourceBytes);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                String n = name.equals(sourceInternal) ? targetInternal : name;
                String s = superName != null && superName.equals(sourceInternal)
                           ? targetInternal : superName;
                String[] ifs = renameArray(interfaces);
                super.visit(version, access, n, signature, s, ifs);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc,
                                            String signature, Object value) {
                return super.visitField(access, name, rdesc(desc), signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(
                    access, name, rdesc(desc), signature, renameArray(exceptions));
                if (mv == null) return null;
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override public void visitTypeInsn(int op, String t) {
                        super.visitTypeInsn(op, t.equals(sourceInternal) ? targetInternal : t);
                    }
                    @Override public void visitMethodInsn(int op, String owner, String mn,
                                                           String md, boolean itf) {
                        super.visitMethodInsn(op,
                            owner.equals(sourceInternal) ? targetInternal : owner,
                            mn, rdesc(md), itf);
                    }
                    @Override public void visitFieldInsn(int op, String owner, String fn,
                                                          String fd) {
                        super.visitFieldInsn(op,
                            owner.equals(sourceInternal) ? targetInternal : owner,
                            fn, rdesc(fd));
                    }
                    @Override public void visitLdcInsn(Object cst) {
                        if (cst instanceof Type) {
                            Type t = (Type) cst;
                            if (t.getSort() == Type.OBJECT
                                && t.getInternalName().equals(sourceInternal)) {
                                super.visitLdcInsn(Type.getObjectType(targetInternal));
                                return;
                            }
                        }
                        super.visitLdcInsn(cst);
                    }
                    @Override public void visitMultiANewArrayInsn(String d, int dims) {
                        super.visitMultiANewArrayInsn(rdesc(d), dims);
                    }
                    @Override public void visitLocalVariable(String vn, String d, String sig,
                                                              org.objectweb.asm.Label s,
                                                              org.objectweb.asm.Label e, int idx) {
                        super.visitLocalVariable(vn, rdesc(d), sig, s, e, idx);
                    }
                };
            }

            private String[] renameArray(String[] arr) {
                if (arr == null) return null;
                for (int i = 0; i < arr.length; i++) {
                    if (sourceInternal.equals(arr[i])) arr[i] = targetInternal;
                }
                return arr;
            }

            private String rdesc(String desc) {
                if (desc == null || desc.indexOf('L') < 0) return desc;
                StringBuilder sb = new StringBuilder();
                int i = 0;
                while (i < desc.length()) {
                    char c = desc.charAt(i);
                    if (c == 'L') {
                        int end = desc.indexOf(';', i);
                        String in = desc.substring(i + 1, end);
                        sb.append('L');
                        sb.append(sourceInternal.equals(in) ? targetInternal : in);
                        sb.append(';');
                        i = end + 1;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                return sb.toString();
            }
        }, 0);

        return cw.toByteArray();
    }

    public static void installStubs(Map<String, byte[]> stubs, Instrumentation inst) {
        File tempJar = null;
        try {
            tempJar = File.createTempFile("vastubs-", ".jar");
            tempJar.deleteOnExit();

            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar))) {
                for (Map.Entry<String, byte[]> entry : stubs.entrySet()) {
                    String entryName = entry.getKey().replace('.', '/') + ".class";
                    jos.putNextEntry(new JarEntry(entryName));
                    jos.write(entry.getValue());
                    jos.closeEntry();
                }
            }

            try (JarFile jf = new JarFile(tempJar)) {
                inst.appendToSystemClassLoaderSearch(jf);
            }

            LOGGER.info("Stub JAR injected: " + tempJar.getAbsolutePath() + " (" + tempJar.length() + " bytes)");
        } catch (IOException e) {
            LOGGER.error("Failed to inject stub JAR: " + e.getMessage());
            if (tempJar != null) tempJar.delete();
        }
    }
}
