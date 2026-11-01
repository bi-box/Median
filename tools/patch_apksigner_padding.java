import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Creates a build-local apksigner whose v2 block has no mandatory 4 KiB pre-padding. */
public final class patch_apksigner_padding {
    private static final String TARGET = "com/android/apksig/internal/apk/ApkSigningBlockUtils.class";
    private static final String METHOD = "generateApkSigningBlockPadding";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        try (JarFile source = new JarFile(input.toFile());
             OutputStream stream = Files.newOutputStream(output);
             JarOutputStream destination = new JarOutputStream(stream)) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                JarEntry copy = new JarEntry(entry.getName());
                copy.setTime(0L);
                destination.putNextEntry(copy);
                try (InputStream data = source.getInputStream(entry)) {
                    byte[] bytes = data.readAllBytes();
                    if (TARGET.equals(entry.getName())) bytes = patch(bytes);
                    destination.write(bytes);
                }
                destination.closeEntry();
            }
        }
    }

    private static byte[] patch(byte[] input) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(input).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!METHOD.equals(method.name) ||
                    !"(Lcom/android/apksig/util/DataSource;Z)Lcom/android/apksig/internal/util/Pair;".equals(method.desc)) continue;
            InsnList instructions = new InsnList();
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            instructions.add(new InsnNode(Opcodes.ICONST_0));
            instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                    "(I)Ljava/lang/Integer;", false));
            instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "com/android/apksig/internal/util/Pair", "of",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Lcom/android/apksig/internal/util/Pair;", false));
            instructions.add(new InsnNode(Opcodes.ARETURN));
            method.instructions = instructions;
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.maxStack = 2;
            method.maxLocals = 2;
            changed = true;
        }
        if (!changed) throw new IllegalStateException("apksigner padding method not found");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private patch_apksigner_padding() {}
}
