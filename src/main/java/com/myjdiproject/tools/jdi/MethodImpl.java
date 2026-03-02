/*
 * Copyright (c) 1998, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.myjdiproject.tools.jdi;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

import com.myjdiproject.jdi.*;

public abstract class MethodImpl extends TypeComponentImpl implements Method {
    private final JNITypeParser signatureParser;
    private SoftReference<List<Type>> argumentTypesRef;
    private SoftReference<List<ReferenceType>> annotationTypesRef;

    abstract int argSlotCount() throws AbsentInformationException;

    abstract List<Location> allLineLocations(SDE.Stratum stratum, String sourceName) throws AbsentInformationException;

    abstract List<Location> locationsOfLine(SDE.Stratum stratum, String sourceName, int lineNumber) throws AbsentInformationException;

    public Location firstLineLocation() {
        return new LocationImpl(vm, this, 0L);
    }

    MethodImpl(VirtualMachine vm, ReferenceTypeImpl declaringType, long ref, String name, String signature, String genericSignature, int modifiers) {
        super(vm, declaringType, ref, name, signature, genericSignature, modifiers);
        signatureParser = new JNITypeParser(signature);
    }

    static MethodImpl createMethodImpl(VirtualMachine vm, ReferenceTypeImpl declaringType, long ref, String name, String signature, String genericSignature, int modifiers) {
        if ((modifiers & (VMModifiers.NATIVE | VMModifiers.ABSTRACT)) != 0) {
            return new NonConcreteMethodImpl(vm, declaringType, ref, name, signature, genericSignature, modifiers);
        } else {
            return new ConcreteMethodImpl(vm, declaringType, ref, name, signature, genericSignature, modifiers);
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof MethodImpl other) {
            return declaringType().equals(other.declaringType())
                    && ref() == other.ref()
                    && super.equals(obj);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ref());
    }

    public final List<Location> allLineLocations() throws AbsentInformationException {
        return allLineLocations(vm.getDefaultStratum(), null);
    }

    public List<Location> allLineLocations(String stratumID, String sourceName) throws AbsentInformationException {
        return allLineLocations(declaringType.stratum(stratumID), sourceName);
    }

    public final List<Location> locationsOfLine(int lineNumber) throws AbsentInformationException {
        return locationsOfLine(vm.getDefaultStratum(), null, lineNumber);
    }

    public List<Location> locationsOfLine(String stratumID, String sourceName, int lineNumber) throws AbsentInformationException {
        return locationsOfLine(declaringType.stratum(stratumID), sourceName, lineNumber);
    }

    LineInfo codeIndexToLineInfo(SDE.Stratum stratum, long codeIndex) {
        if (stratum.isJava()) {
            return new BaseLineInfo(-1, declaringType);
        } else {
            return new StratumLineInfo(stratum.id(), -1, null, null);
        }
    }

    public String returnTypeName() {
        return signatureParser.typeName();
    }

    private String returnSignature() {
        return signatureParser.signature();
    }

    public Type returnType() throws ClassNotLoadedException {
        return findType(returnSignature());
    }

    public Type findType(String signature) throws ClassNotLoadedException {
        ReferenceTypeImpl enclosing = (ReferenceTypeImpl) declaringType();
        return enclosing.findType(signature);
    }

    public List<String> argumentTypeNames() {
        return signatureParser.argumentTypeNames();
    }

    public List<String> argumentSignatures() {
        return signatureParser.argumentSignatures();
    }

    Type argumentType(int index) throws ClassNotLoadedException {
        ReferenceTypeImpl enclosing = (ReferenceTypeImpl) declaringType();
        String signature = argumentSignatures().get(index);
        return enclosing.findType(signature);
    }

    public List<Type> argumentTypes() throws ClassNotLoadedException {
        List<Type> types = argumentTypesRef != null ? argumentTypesRef.get() : null;
        if (types == null) {
            int size = argumentSignatures().size();
            types = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Type type = argumentType(i);
                types.add(type);
            }
            argumentTypesRef = new SoftReference<>(types);
        }
        return types;
    }

    public int compareTo(Method method) {
        ReferenceTypeImpl declaringType = (ReferenceTypeImpl)declaringType();
        int rc = declaringType.compareTo(method.declaringType());
        if (rc == 0) {
            rc = declaringType.indexOf(this) - declaringType.indexOf(method);
        }
        return rc;
    }

    public boolean isAbstract() {
        return isModifierSet(VMModifiers.ABSTRACT);
    }

    public boolean isDefault() {
        return !isModifierSet(VMModifiers.ABSTRACT)
                && !isModifierSet(VMModifiers.STATIC)
                && !isModifierSet(VMModifiers.PRIVATE)
                && declaringType() instanceof InterfaceType;
    }

    public boolean isSynchronized() {
        return isModifierSet(VMModifiers.SYNCHRONIZED);
    }

    public boolean isNative() {
        return isModifierSet(VMModifiers.NATIVE);
    }

    public boolean isVarArgs() {
        return isModifierSet(VMModifiers.VARARGS);
    }

    public boolean isBridge() {
        return isModifierSet(VMModifiers.BRIDGE);
    }

    public boolean isConstructor() {
        return "<init>".equals(name());
    }

    public boolean isStaticInitializer() {
        return "<clinit>".equals(name());
    }

    public boolean isObsolete() {
        try {
            return JDWP.Method.IsObsolete.process(vm, declaringType, ref).isObsolete;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    class ReturnContainer implements ValueContainer {
        ReturnContainer() {
        }
        public Type type() throws ClassNotLoadedException {
            return returnType();
        }
        public String typeName(){
            return returnTypeName();
        }
        public String signature() {
            return returnSignature(); //type().signature();
        }
        public Type findType(String signature) throws ClassNotLoadedException {
            return MethodImpl.this.findType(signature);
        }
    }
    ReturnContainer retValContainer = null;
    ReturnContainer getReturnValueContainer() {
        if (retValContainer == null) {
            retValContainer = new ReturnContainer();
        }
        return retValContainer;
    }

    class ArgumentContainer implements ValueContainer {
        final int index;

        ArgumentContainer(int index) {
            this.index = index;
        }
        public Type type() throws ClassNotLoadedException {
            return argumentType(index);
        }
        public String typeName(){
            return argumentTypeNames().get(index);
        }
        public String signature() {
            return argumentSignatures().get(index);
        }
        public Type findType(String signature) throws ClassNotLoadedException {
            return MethodImpl.this.findType(signature);
        }
    }

    void handleVarArgs(List<Value> arguments) throws ClassNotLoadedException, InvalidTypeException {
        List<Type> paramTypes = this.argumentTypes();
        ArrayType lastParamType = (ArrayType) paramTypes.getLast();
        int argCount = arguments.size();
        int paramCount = paramTypes.size();
        if (argCount < paramCount - 1) {
            return;
        }
        if (argCount == paramCount - 1) {
            ArrayReference argArray = lastParamType.newInstance(0);
            arguments.add(argArray);
            return;
        }
        Value nthArgValue = arguments.get(paramCount - 1);
        if (nthArgValue == null && argCount == paramCount) {
            return;
        }

        Type nthArgType = (nthArgValue == null) ? null : nthArgValue.type();
        if (nthArgType instanceof ArrayTypeImpl) {
            if (argCount == paramCount && ((ArrayTypeImpl) nthArgType).isAssignableTo(lastParamType)) {
                return;
            }
        }

        int count = argCount - paramCount + 1;
        ArrayReference argArray = lastParamType.newInstance(count);

        argArray.setValues(0, arguments, paramCount - 1, count);
        arguments.set(paramCount - 1, argArray);

        for (int ii = paramCount; ii < argCount; ii++) {
            arguments.remove(paramCount);
        }
    }

    List<Value> validateAndPrepareArgumentsForInvoke(List<? extends Value> origArguments) throws ClassNotLoadedException, InvalidTypeException {
        List<Value> arguments = new ArrayList<>(origArguments);
        if (isVarArgs()) {
            handleVarArgs(arguments);
        }

        int argSize = arguments.size();
        for (int i = 0; i < argSize; i++) {
            Value value = arguments.get(i);
            value = ValueImpl.prepareForAssignment(value, new ArgumentContainer(i));
            arguments.set(i, value);
        }
        return arguments;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(declaringType().name());
        sb.append(".");
        sb.append(name());
        sb.append("(");
        boolean first = true;
        for (String name : argumentTypeNames()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(name);
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    // SCANNER ADDED
    @Override
    public List<ReferenceType> annotationTypes() {
        if (annotationTypesRef == null || annotationTypesRef.get() == null) {
            List<ReferenceType> types;
            try {
                types = JDWP.Method.AnnotationTypes.process(vm, declaringType, ref).types;
            } catch (JDWPException e) {
                throw e.toJDIException();
            }
            annotationTypesRef = new SoftReference<>(types);
        }
        return annotationTypesRef.get();
    }
}
