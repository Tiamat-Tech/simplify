package org.cf.smalivm.smali;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.util.ReferenceUtil;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassBuilder {

    private static final Logger log = LoggerFactory.getLogger(ClassBuilder.class.getSimpleName());

    public byte[] build(ClassDef classDef) {
        log.debug("building class: " + classDef);

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        visitClass(classDef, classWriter);
        visitFields(classDef.getFields(), classWriter);
        visitMethods(classDef.getMethods(), classWriter);
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    private String buildASMSignature(Reference reference) {
        String signature = ReferenceUtil.getReferenceString(reference);

        return stripName(signature);
    }

    private String buildDescriptor(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (CharSequence parameterType : method.getParameterTypes()) {
            sb.append(parameterType);
        }
        sb.append(')');
        sb.append(method.getReturnType());

        return sb.toString();
    }

    private String[] buildExceptions(Method method) {
        if (method.getImplementation() == null) {
            return null;
        }

        Set<String> exceptionTypes = new HashSet<String>();
        for (TryBlock<? extends ExceptionHandler> tryBlock : method.getImplementation().getTryBlocks()) {
            for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                String type = handler.getExceptionType();
                if (type == null) {
                    // Type is null if it's a catchall
                    continue;
                }

                exceptionTypes.add(stripName(type));
            }
        }

        return exceptionTypes.toArray(new String[exceptionTypes.size()]);
    }

    private String[] buildInterfaces(ClassDef classDef) {
        List<String> interfaces = classDef.getInterfaces();

        return interfaces.stream().map(s -> stripName(s)).toArray(size -> new String[interfaces.size()]);
    }

    private String stripName(String internalName) {
        return internalName.substring(1, internalName.length() - 1);
    }

    private void visitClass(ClassDef classDef, ClassWriter classWriter) {
        int version = Opcodes.V1_7;
        int access = classDef.getAccessFlags();
        String name = stripName(classDef.getType());
        String signature = buildASMSignature(classDef);
        String superName = null;
        if (classDef.getSuperclass() != null) {
            superName = stripName(classDef.getSuperclass());
        }
        String[] interfaces = buildInterfaces(classDef);
        classWriter.visit(version, access, name, signature, superName, interfaces);
    }

    private void visitClInitStub(Method method, MethodVisitor mv) {
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
    }

    private void visitFields(Iterable<? extends Field> fields, ClassWriter classWriter) {
        for (Field field : fields) {
            int access = field.getAccessFlags();
            String name = field.getName();
            String desc = field.getType();
            String signature = null;
            Object value = null;
            classWriter.visitField(access, name, desc, signature, value);
        }
    }

    private void visitMethod(Method method, MethodVisitor mv) {
        mv.visitCode();

        if (method.getName().equals("<clinit>")) {
            visitClInitStub(method, mv);
        } else {
            visitMethodStub(method, mv);
        }

        // Do this at the end so ASM can calculate max stack and locals sizes
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void visitMethods(Iterable<? extends Method> methods, ClassWriter classWriter) {
        for (Method method : methods) {
            int access = method.getAccessFlags();
            String name = method.getName();
            String desc = buildDescriptor(method);
            String signature = null;
            String[] exceptions = buildExceptions(method);
            MethodVisitor mv = classWriter.visitMethod(access, name, desc, signature, exceptions);
            if (method.getImplementation() != null) {
                visitMethod(method, mv);
            }
        }
    }

    private void visitMethodStub(Method method, MethodVisitor mv) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("Stub!");
        String owner = "java/lang/RuntimeException";
        String name = "<init>";
        String desc = "(Ljava/lang/String;)V";
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, desc, false);
        mv.visitInsn(Opcodes.ATHROW);
    }

}