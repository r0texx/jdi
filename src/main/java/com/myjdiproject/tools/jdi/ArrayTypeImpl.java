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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.myjdiproject.jdi.ArrayReference;
import com.myjdiproject.jdi.ArrayType;
import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.InterfaceType;
import com.myjdiproject.jdi.Method;
import com.myjdiproject.jdi.PrimitiveType;
import com.myjdiproject.jdi.ReferenceType;
import com.myjdiproject.jdi.Type;
import com.myjdiproject.jdi.VirtualMachine;

public class ArrayTypeImpl extends ReferenceTypeImpl implements ArrayType {
    protected ArrayTypeImpl(VirtualMachine aVm, long aRef) {
        super(aVm, aRef);
    }

    public ArrayReference newInstance(int length) {
        try {
            return (ArrayReference) JDWP.ArrayType.NewInstance.process(vm, this, length).newArray;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public String componentSignature() {
        JNITypeParser sig = new JNITypeParser(signature());
        return sig.componentSignature();
    }

    public String componentTypeName() {
        JNITypeParser parser = new JNITypeParser(componentSignature());
        return parser.typeName();
    }

    Type type() throws ClassNotLoadedException {
        return findType(componentSignature());
    }

    @Override
    void addVisibleMethods(Map<String, Method> map, Set<InterfaceType> seenInterfaces) {
    }

    public List<Method> allMethods() {
        return new ArrayList<>(0);
    }

    public Type componentType() throws ClassNotLoadedException {
        return findType(componentSignature());
    }

    static boolean isComponentAssignable(Type destination, Type source) {
        if (source instanceof PrimitiveType) {
            return source.equals(destination);
        } else {
            if (destination instanceof PrimitiveType) {
                return false;
            }
            ReferenceTypeImpl refSource = (ReferenceTypeImpl) source;
            ReferenceTypeImpl refDestination = (ReferenceTypeImpl) destination;
            return refSource.isAssignableTo(refDestination);
        }
    }

    boolean isAssignableTo(ReferenceType destType) {
        if (destType instanceof ArrayType) {
            try {
                Type destComponentType = ((ArrayType) destType).componentType();
                return isComponentAssignable(destComponentType, componentType());
            } catch (ClassNotLoadedException e) {
                return false;
            }
        } else if (destType instanceof InterfaceType) {
            return destType.name().equals("java.lang.Cloneable");
        } else {
            return destType.name().equals("java.lang.Object");
        }
    }

    List<ReferenceType> inheritedTypes() {
        return new ArrayList<>(0);
    }

    void getModifiers() {
        if (modifiers != -1) {
            return;
        }
        try {
            Type t = componentType();
            if (t instanceof PrimitiveType) {
                modifiers = VMModifiers.FINAL | VMModifiers.PUBLIC;
            } else {
                ReferenceType rt = (ReferenceType)t;
                modifiers = rt.modifiers();
            }
        } catch (ClassNotLoadedException cnle) {
            cnle.printStackTrace();
        }
    }

    public String toString() {
       return "array class " + name() + " (" + loaderString() + ")";
    }

    public boolean isPrepared() { return true; }
    public boolean isVerified() { return true; }
    public boolean isInitialized() { return true; }
    public boolean failedToInitialize() { return false; }
    public boolean isAbstract() { return false; }
    public boolean isFinal() { return true; }
    public boolean isStatic() { return false; }
}
