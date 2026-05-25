package dev.veryalpha.loader.transform;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import dev.veryalpha.loader.mod.ModLoader;

public class ClassOverrideTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) return null;
        return ModLoader.getOverride(className.replace('/', '.'));
    }
}
