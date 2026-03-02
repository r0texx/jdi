/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.myjdiproject.jdi.AbsentInformationException;
import com.myjdiproject.jdi.ClassLoaderReference;
import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.ClassObjectReference;
import com.myjdiproject.jdi.Field;
import com.myjdiproject.jdi.InterfaceType;
import com.myjdiproject.jdi.InternalException;
import com.myjdiproject.jdi.Location;
import com.myjdiproject.jdi.Method;
import com.myjdiproject.jdi.ModuleReference;
import com.myjdiproject.jdi.ObjectReference;
import com.myjdiproject.jdi.ReferenceType;
import com.myjdiproject.jdi.Type;
import com.myjdiproject.jdi.Value;
import com.myjdiproject.jdi.VirtualMachine;

public abstract class ReferenceTypeImpl extends TypeImpl implements ReferenceType {
    private static final String ABSENT_BASE_SOURCE_NAME = "**ABSENT_BASE_SOURCE_NAME**";
    private static final int INITIALIZED_OR_FAILED = JDWP.ClassStatus.INITIALIZED | JDWP.ClassStatus.ERROR;

    protected final long ref;
    private String signature = null;
    private String genericSignature = null;
    private boolean genericSignatureGotten;
    private String baseSourceName = null;
    private String baseSourceDir = null;
    private String baseSourcePath = null;
    protected int modifiers = -1;
    private SoftReference<List<Field>> fieldsRef = null;
    private SoftReference<List<Method>> methodsRef = null;
    private SoftReference<SDE> sdeRef = null;

    private boolean isClassLoaderCached = false;
    private ClassLoaderReference classLoader = null;
    private ClassObjectReference classObject = null;
    private ModuleReference module = null;

    private int status = 0;
    private boolean isPrepared = false;

    private boolean versionNumberGotten = false;
    private int majorVersion;
    private int minorVersion;

    private boolean constantPoolInfoGotten = false;
    private int constantPoolCount;
    private SoftReference<byte[]> constantPoolBytesRef = null;

    static final SDE NO_SDE_INFO_MARK = new SDE();

    protected ReferenceTypeImpl(VirtualMachine aVm, long aRef) {
        super(aVm);
        ref = aRef;
        genericSignatureGotten = false;
    }

    void noticeRedefineClass() {
        baseSourceName = null;
        baseSourcePath = null;
        modifiers = -1;
        fieldsRef = null;
        methodsRef = null;
        sdeRef = null;
        versionNumberGotten = false;
        constantPoolInfoGotten = false;
    }

    Method getMethodMirror(long ref) {
        if (ref == 0) {
            return new ObsoleteMethodImpl(vm, this);
        }
        for (Method m : methods()) {
            MethodImpl method = (MethodImpl) m;
            if (method.ref() == ref) {
                return method;
            }
        }
        throw new IllegalArgumentException("Invalid method id: " + ref);
    }

    Field getFieldMirror(long ref) {
        for (Field f : fields()) {
            if (f.ref() == ref) {
                return f;
            }
        }
        throw new IllegalArgumentException("Invalid field id: " + ref);
    }

    public boolean equals(Object obj) {
        if (obj instanceof ReferenceTypeImpl other) {
            return ref == other.ref && vm.equals(other.virtualMachine());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ref());
    }

    public int compareTo(ReferenceType object) {
        ReferenceTypeImpl other = (ReferenceTypeImpl)object;
        int comp = name().compareTo(other.name());
        if (comp == 0) {
            long rf1 = ref();
            long rf2 = other.ref();
            if (rf1 == rf2) {
                comp = vm.sequenceNumber - ((VirtualMachineImpl)(other.virtualMachine())).sequenceNumber;
            } else {
                comp = (rf1 < rf2)? -1 : 1;
            }
        }
        return comp;
    }

    public String signature() {
        if (signature == null) {
            if (vm.canGet1_5LanguageFeatures()) {
                genericSignature();
            } else {
                try {
                    signature = JDWP.ReferenceType.Signature.process(vm, this).signature;
                } catch (JDWPException exc) {
                    throw exc.toJDIException();
                }
            }
        }
        return signature;
    }

    public String genericSignature() {
        if (vm.canGet1_5LanguageFeatures() && !genericSignatureGotten) {
            JDWP.ReferenceType.SignatureWithGeneric result;
            try {
                result = JDWP.ReferenceType.SignatureWithGeneric.process(vm, this);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
            signature = result.signature;
            setGenericSignature(result.genericSignature);
        }
        return genericSignature;
    }

    public ClassLoaderReference classLoader() {
        if (!isClassLoaderCached) {
            try {
                classLoader = JDWP.ReferenceType.ClassLoader.process(vm, this).classLoader;
                isClassLoaderCached = true;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return classLoader;
    }

    public ModuleReference module() {
        if (module != null) {
            return module;
        }
        try {
            ModuleReferenceImpl m = JDWP.ReferenceType.Module.process(vm, this).module;
            module = vm.getModule(m.ref());
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return module;
    }

    public boolean isPublic() {
        getModifiers();
        return (modifiers & VMModifiers.PUBLIC) > 0;
    }

    public boolean isProtected() {
        getModifiers();
        return (modifiers & VMModifiers.PROTECTED) > 0;
    }

    public boolean isPrivate() {
        getModifiers();
        return (modifiers & VMModifiers.PRIVATE) > 0;
    }

    public boolean isPackagePrivate() {
        return !isPublic() && !isPrivate() && !isProtected();
    }

    public boolean isAbstract() {
        getModifiers();
        return (modifiers & VMModifiers.ABSTRACT) > 0;
    }

    public boolean isFinal() {
        getModifiers();
        return (modifiers & VMModifiers.FINAL) > 0;
    }

    public boolean isStatic() {
        getModifiers();
        return (modifiers & VMModifiers.STATIC) > 0;
    }

    public boolean isPrepared() {
        if (status == 0) {
            updateStatus();
        }
        return isPrepared;
    }

    public boolean isVerified() {
        if ((status & JDWP.ClassStatus.VERIFIED) == 0) {
            updateStatus();
        }
        return (status & JDWP.ClassStatus.VERIFIED) != 0;
    }

    public boolean isInitialized() {
        if ((status & INITIALIZED_OR_FAILED) == 0) {
            updateStatus();
        }
        return (status & JDWP.ClassStatus.INITIALIZED) != 0;
    }

    public boolean failedToInitialize() {
        if ((status & INITIALIZED_OR_FAILED) == 0) {
            updateStatus();
        }
        return (status & JDWP.ClassStatus.ERROR) != 0;
    }

    public List<Field> fields() {
        List<Field> fields = (fieldsRef == null) ? null : fieldsRef.get();
        if (fields == null) {
            if (vm.canGet1_5LanguageFeatures()) {
                try {
                    fields = JDWP.ReferenceType.FieldsWithGeneric.process(vm, this).fields;
                } catch (JDWPException exc) {
                    throw exc.toJDIException();
                }
            } else {
                try {
                    fields = JDWP.ReferenceType.Fields.process(vm, this).fields;
                } catch (JDWPException exc) {
                    throw exc.toJDIException();
                }
            }
            fields = Collections.unmodifiableList(fields);
            fieldsRef = new SoftReference<>(fields);
        }
        return fields;
    }

    abstract List<? extends ReferenceType> inheritedTypes();

    void addVisibleFields(List<Field> visibleList, Map<String, Field> visibleTable, List<String> ambiguousNames) {
        for (Field field : visibleFields()) {
            String name = field.name();
            if (!ambiguousNames.contains(name)) {
                Field duplicate = visibleTable.get(name);
                if (duplicate == null) {
                    visibleList.add(field);
                    visibleTable.put(name, field);
                } else if (!field.equals(duplicate)) {
                    ambiguousNames.add(name);
                    visibleTable.remove(name);
                    visibleList.remove(duplicate);
                }
            }
        }
    }

    public List<Field> visibleFields() {
        List<Field> visibleList = new ArrayList<>();
        Map<String, Field>  visibleTable = new HashMap<>();
        List<String> ambiguousNames = new ArrayList<>();
        List<? extends ReferenceType> types = inheritedTypes();
        for (ReferenceType referenceType : types) {
            ReferenceTypeImpl type = (ReferenceTypeImpl)referenceType;
            type.addVisibleFields(visibleList, visibleTable, ambiguousNames);
        }

        List<Field> retList = new ArrayList<>(fields());
        for (Field field : retList) {
            Field hidden = visibleTable.get(field.name());
            if (hidden != null) {
                visibleList.remove(hidden);
            }
        }
        retList.addAll(visibleList);
        return retList;
    }

    void addAllFields(List<Field> fieldList, Set<ReferenceType> typeSet) {
        if (!typeSet.contains(this)) {
            typeSet.add(this);
            fieldList.addAll(fields());

            List<? extends ReferenceType> types = inheritedTypes();
            for (ReferenceType referenceType : types) {
                ReferenceTypeImpl type = (ReferenceTypeImpl)referenceType;
                type.addAllFields(fieldList, typeSet);
            }
        }
    }
    public List<Field> allFields() {
        List<Field> fieldList = new ArrayList<>();
        Set<ReferenceType> typeSet = new HashSet<>();
        addAllFields(fieldList, typeSet);
        return fieldList;
    }

    public Field fieldByName(String fieldName) {
        for (Field f : visibleFields()) {
            if (f.name().equals(fieldName)) {
                return f;
            }
        }
        return null;
    }

    public List<Method> methods() {
        List<Method> methods = (methodsRef == null) ? null : methodsRef.get();
        if (methods == null) {
            try {
                if (vm.canGet1_5LanguageFeatures()) {
                    methods = JDWP.ReferenceType.MethodsWithGeneric.process(vm, this).methods;
                } else {
                    methods = JDWP.ReferenceType.Methods.process(vm, this).methods;
                }
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
            methods = Collections.unmodifiableList(methods);
            methodsRef = new SoftReference<>(methods);
        }
        return methods;
    }

    void addToMethodMap(Map<String, Method> methodMap, List<Method> methodList) {
        for (Method method : methodList)
            methodMap.put(method.name().concat(method.signature()), method);
        }

    abstract void addVisibleMethods(Map<String, Method> methodMap, Set<InterfaceType> seenInterfaces);

    public List<Method> visibleMethods() {
        Map<String, Method> map = new HashMap<>();
        addVisibleMethods(map, new HashSet<>());
        List<Method> list = allMethods();
        list.retainAll(new HashSet<>(map.values()));
        return list;
    }

    public abstract List<Method> allMethods();

    public List<Method> methodsByName(String name) {
        List<Method> methods = visibleMethods();
        ArrayList<Method> retList = new ArrayList<>(methods.size());
        for (Method candidate : methods) {
            if (candidate.name().equals(name)) {
                retList.add(candidate);
            }
        }
        retList.trimToSize();
        return retList;
    }

    public List<Method> methodsByName(String name, String signature) {
        List<Method> methods = visibleMethods();
        ArrayList<Method> retList = new ArrayList<>(methods.size());
        for (Method candidate : methods) {
            if (candidate.name().equals(name) &&
                candidate.signature().equals(signature)) {
                retList.add(candidate);
            }
        }
        retList.trimToSize();
        return retList;
    }

    List<InterfaceType> getInterfaces() {
        try {
            return JDWP.ReferenceType.Interfaces.process(vm, this).interfaces;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public List<ReferenceType> nestedTypes() {
        List<ReferenceType> nested = new ArrayList<>();
        String outername = name();
        int outerlen = outername.length();
        vm.forEachClass(refType -> {
            String name = refType.name();
            int len = name.length();
            if (len > outerlen && name.startsWith(outername)) {
                char c = name.charAt(outerlen);
                if (c == '$' || c == '#') {
                    nested.add(refType);
                }
            }
        });
        return nested;
    }

    public Value getValue(Field sig) {
        List<Field> list = new ArrayList<>(1);
        list.add(sig);
        Map<Field, Value> map = getValues(list);
        return map.get(sig);
    }

    public Map<Field,Value> getValues(List<? extends Field> theFields) {
        validateMirrors(theFields);

        int size = theFields.size();
        Map<Field, Value> map = new HashMap<>(size);

        List<Value> values;
        try {
            values = JDWP.ReferenceType.GetValues.process(vm, this, theFields).values;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }

        if (size != values.size()) {
            throw new InternalException("Wrong number of values returned from target VM");
        }
        for (int i = 0; i < size; i++) {
            FieldImpl field = (FieldImpl) theFields.get(i);
            map.put(field, values.get(i));
        }

        return map;
    }

    public ClassObjectReference classObject() {
        if (classObject == null) {
            try {
                classObject = JDWP.ReferenceType.ClassObject.process(vm, this).classObject;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return classObject;
    }

    SDE.Stratum stratum(String stratumID) {
        SDE sde = sourceDebugExtensionInfo();
        if (!sde.isValid()) {
            sde = NO_SDE_INFO_MARK;
        }
        return sde.stratum(stratumID);
    }

    public String sourceName() throws AbsentInformationException {
        return sourceNames(vm.getDefaultStratum()).getFirst();
    }

    public List<String> sourceNames(String stratumID) throws AbsentInformationException {
        SDE.Stratum stratum = stratum(stratumID);
        if (stratum.isJava()) {
            List<String> result = new ArrayList<>(1);
            result.add(baseSourceName());
            return result;
        }
        return stratum.sourceNames(this);
    }

    public List<String> sourcePaths(String stratumID) throws AbsentInformationException {
        SDE.Stratum stratum = stratum(stratumID);
        if (stratum.isJava()) {
            List<String> result = new ArrayList<>(1);
            result.add(baseSourceDir() + baseSourceName());
            return result;
        }
        return stratum.sourcePaths(this);
    }

    String baseSourceName() throws AbsentInformationException {
        if (baseSourceName == null) {
            try {
                baseSourceName = JDWP.ReferenceType.SourceFile.process(vm, this).sourceFile;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return baseSourceName;
    }

    String baseSourcePath() throws AbsentInformationException {
        if (baseSourcePath == null) {
            baseSourcePath = baseSourceDir() + baseSourceName();
        }
        return baseSourcePath;
    }

    String baseSourceDir() {
        if (baseSourceDir == null) {
            String typeName = name();
            StringBuilder sb = new StringBuilder(typeName.length() + 10);
            int index = 0;
            int nextIndex;

            while ((nextIndex = typeName.indexOf('.', index)) > 0) {
                sb.append(typeName, index, nextIndex);
                sb.append(java.io.File.separatorChar);
                index = nextIndex + 1;
            }
            baseSourceDir = sb.toString();
        }
        return baseSourceDir;
    }

    public String sourceDebugExtension() throws AbsentInformationException {
        if (!vm.canGetSourceDebugExtension()) {
            throw new UnsupportedOperationException();
        }
        SDE sde = sourceDebugExtensionInfo();
        if (sde == NO_SDE_INFO_MARK) {
            throw new AbsentInformationException();
        }
        return sde.sourceDebugExtension;
    }

    private SDE sourceDebugExtensionInfo() {
        if (!vm.canGetSourceDebugExtension()) {
            return NO_SDE_INFO_MARK;
        }
        SDE sde = (sdeRef == null) ?  null : sdeRef.get();
        if (sde == null) {
            String extension = null;
            try {
                extension = JDWP.ReferenceType.SourceDebugExtension.process(vm, this).extension;
            } catch (JDWPException exc) {
                if (exc.errorCode() != JDWP.Error.ABSENT_INFORMATION) {
                    sdeRef = new SoftReference<>(NO_SDE_INFO_MARK);
                    throw exc.toJDIException();
                }
            }
            if (extension == null) {
                sde = NO_SDE_INFO_MARK;
            } else {
                sde = new SDE(extension);
            }
            sdeRef = new SoftReference<>(sde);
        }
        return sde;
    }

    public List<String> availableStrata() {
        SDE sde = sourceDebugExtensionInfo();
        if (sde.isValid()) {
            return sde.availableStrata();
        } else {
            List<String> strata = new ArrayList<>();
            strata.add(SDE.BASE_STRATUM_NAME);
            return strata;
        }
    }

    public String defaultStratum() {
        SDE sdei = sourceDebugExtensionInfo();
        if (sdei.isValid()) {
            return sdei.defaultStratumId;
        } else {
            return SDE.BASE_STRATUM_NAME;
        }
    }

    public int modifiers() {
        getModifiers();
        return modifiers;
    }

    public List<Location> allLineLocations() throws AbsentInformationException {
        return allLineLocations(vm.getDefaultStratum(), null);
    }

    public List<Location> allLineLocations(String stratumID, String sourceName) throws AbsentInformationException {
        boolean someAbsent = false; // A method that should have info, didn't
        SDE.Stratum stratum = stratum(stratumID);
        List<Location> list = new ArrayList<>();  // location list

        for (Method value : methods()) {
            MethodImpl method = (MethodImpl) value;
            try {
                list.addAll(method.allLineLocations(stratum, sourceName));
            } catch (AbsentInformationException exc) {
                someAbsent = true;
            }
        }

        if (someAbsent && list.isEmpty()) {
            throw new AbsentInformationException();
        }
        return list;
    }

    public List<Location> locationsOfLine(int lineNumber) throws AbsentInformationException {
        return locationsOfLine(vm.getDefaultStratum(), null, lineNumber);
    }

    public List<Location> locationsOfLine(String stratumID, String sourceName, int lineNumber) throws AbsentInformationException {
        boolean someAbsent = false;
        boolean somePresent = false;
        List<Method> methods = methods();
        SDE.Stratum stratum = stratum(stratumID);

        List<Location> list = new ArrayList<>();
        for (Method m : methods) {
            MethodImpl method = (MethodImpl) m;
            if (!method.isAbstract() &&
                !method.isNative()) {
                try {
                    list.addAll(method.locationsOfLine(stratum, sourceName, lineNumber));
                    somePresent = true;
                } catch (AbsentInformationException exc) {
                    someAbsent = true;
                }
            }
        }
        if (someAbsent && !somePresent) {
            throw new AbsentInformationException();
        }
        return list;
    }

    public List<ObjectReference> instances(long maxInstances) {
        if (!vm.canGetInstanceInfo()) {
            throw new UnsupportedOperationException("target does not support getting instances");
        }

        if (maxInstances < 0) {
            throw new IllegalArgumentException("maxInstances is less than zero: " + maxInstances);
        }
        int intMax = maxInstances > Integer.MAX_VALUE ? Integer.MAX_VALUE: (int) maxInstances;
        try {
            return JDWP.ReferenceType.Instances.process(vm, this, intMax).instances;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    private void getClassFileVersion() {
        if (!vm.canGetClassFileVersion()) {
            throw new UnsupportedOperationException();
        }
        JDWP.ReferenceType.ClassFileVersion classFileVersion;
        if (!versionNumberGotten) {
            try {
                classFileVersion = JDWP.ReferenceType.ClassFileVersion.process(vm, this);
            } catch (JDWPException exc) {
                if (exc.errorCode() == JDWP.Error.ABSENT_INFORMATION) {
                    majorVersion = 0;
                    minorVersion = 0;
                    versionNumberGotten = true;
                    return;
                } else {
                    throw exc.toJDIException();
                }
            }
            majorVersion = classFileVersion.majorVersion;
            minorVersion = classFileVersion.minorVersion;
            versionNumberGotten = true;
        }
    }

    public int majorVersion() {
        getClassFileVersion();
        return majorVersion;
    }

    public int minorVersion() {
        getClassFileVersion();
        return minorVersion;
    }

    private byte[] getConstantPoolInfo() {
        JDWP.ReferenceType.ConstantPool jdwpCPool;
        if (!vm.canGetConstantPool()) {
            throw new UnsupportedOperationException();
        }
        if (constantPoolInfoGotten) {
            if (constantPoolBytesRef == null) {
                return null;
            }
            byte[] cpbytes = constantPoolBytesRef.get();
            if (cpbytes != null) {
                return cpbytes;
            }
        }

        try {
            jdwpCPool = JDWP.ReferenceType.ConstantPool.process(vm, this);
        } catch (JDWPException exc) {
            if (exc.errorCode() == JDWP.Error.ABSENT_INFORMATION) {
                constantPoolCount = 0;
                constantPoolBytesRef = null;
                constantPoolInfoGotten = true;
                return null;
            } else {
                throw exc.toJDIException();
            }
        }
        byte[] cpBytes;
        constantPoolCount = jdwpCPool.count;
        cpBytes = jdwpCPool.bytes;
        constantPoolBytesRef = new SoftReference<>(cpBytes);
        constantPoolInfoGotten = true;
        return cpBytes;
    }

    public int constantPoolCount() {
        getConstantPoolInfo();
        return constantPoolCount;
    }

    public byte[] constantPool() {
        byte[] cpBytes = getConstantPoolInfo();
        if (cpBytes != null) {
            return cpBytes.clone();
        } else {
            return null;
        }
    }

    void getModifiers() {
        if (modifiers != -1) {
            return;
        }
        try {
            modifiers = JDWP.ReferenceType.Modifiers.process(vm, this).modBits;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    void decodeStatus(int status) {
        this.status = status;
        if ((status & JDWP.ClassStatus.PREPARED) != 0) {
            isPrepared = true;
        }
    }

    void updateStatus() {
        try {
            decodeStatus(JDWP.ReferenceType.Status.process(vm, this).status);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    void markPrepared() {
        isPrepared = true;
    }

    public long ref() {
        return ref;
    }

    int indexOf(Method method) {
        return methods().indexOf(method);
    }

    int indexOf(Field field) {
        return fields().indexOf(field);
    }

    abstract boolean isAssignableTo(ReferenceType type);

    boolean isAssignableFrom(ReferenceType type) {
        return ((ReferenceTypeImpl) type).isAssignableTo(this);
    }

    boolean isAssignableFrom(ObjectReference object) {
        return object == null || isAssignableFrom(object.referenceType());
    }

    void setStatus(int status) {
        decodeStatus(status);
    }

    void setSignature(String signature) {
        this.signature = signature;
    }

    void setGenericSignature(String signature) {
        if (signature != null && signature.isEmpty()) {
            this.genericSignature = null;
        } else {
            this.genericSignature = signature;
        }
        this.genericSignatureGotten = true;
    }

    private static boolean isOneDimensionalPrimitiveArray(String signature) {
        JNITypeParser sig = new JNITypeParser(signature);
        if (sig.isArray()) {
            JNITypeParser componentSig = new JNITypeParser(sig.componentSignature());
            return componentSig.isPrimitive();
        }
        return false;
    }

    Type findType(String signature) throws ClassNotLoadedException {
        Type type;
        JNITypeParser sig = new JNITypeParser(signature);
        if (sig.isVoid()) {
            type = vm.theVoidType;
        } else if (sig.isPrimitive()) {
            type = vm.primitiveTypeMirror(sig.jdwpTag());
        } else {
            ClassLoaderReferenceImpl loader = (ClassLoaderReferenceImpl) classLoader();
            if (loader == null || isOneDimensionalPrimitiveArray(signature)) {
                type = vm.findBootType(signature);
            } else {
                type = loader.findType(signature);
            }
        }
        return type;
    }

    String loaderString() {
        if (classLoader() != null) {
            return "loaded by " + classLoader().toString();
        } else {
            return "no class loader";
        }
    }
}
