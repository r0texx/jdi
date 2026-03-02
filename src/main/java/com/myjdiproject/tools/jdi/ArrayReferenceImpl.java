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

import com.myjdiproject.jdi.ArrayReference;
import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.InvalidTypeException;
import com.myjdiproject.jdi.Method;
import com.myjdiproject.jdi.Type;
import com.myjdiproject.jdi.Value;
import com.myjdiproject.jdi.VirtualMachine;

public class ArrayReferenceImpl extends ObjectReferenceImpl implements ArrayReference {
    int length = -1;

    ArrayReferenceImpl(VirtualMachine aVm, long aRef) {
        super(aVm, aRef);
    }

    protected ClassTypeImpl invokableReferenceType(Method method) {
        return (ClassTypeImpl) method.declaringType();
    }

    ArrayTypeImpl arrayType() {
        return (ArrayTypeImpl)type();
    }

    public int length() {
        if (length == -1) {
            try {
                length = JDWP.ArrayReference.Length.process(vm, this).arrayLength;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return length;
    }

    public Value getValue(int index) {
        List<Value> list = getValues(index, 1);
        return list.getFirst();
    }

    public List<Value> getValues() {
        return getValues(0, -1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object x) {
        return (T)x;
    }

    public List<Value> getValues(int index, int length) {
        if (length == -1) { // -1 means the rest of the array
           length = length() - index;
        }
        if (length == 0) {
            return new ArrayList<>();
        }

        List<Value> vals;
        try {
            vals = cast(JDWP.ArrayReference.GetValues.process(vm, this, index, length).values);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }

        return vals;
    }

    public void setValue(int index, Value value) throws InvalidTypeException, ClassNotLoadedException {
        List<Value> list = new ArrayList<>(1);
        list.add(value);
        setValues(index, list, 0, 1);
    }

    public void setValues(List<? extends Value> values) throws InvalidTypeException, ClassNotLoadedException {
        setValues(0, values, 0, -1);
    }

    public void setValues(int index, List<? extends Value> values, int srcIndex, int length) throws InvalidTypeException, ClassNotLoadedException {
        if (length == 0) {
            return;
        }
        if (length == -1) { // -1 means the rest of the array
            length = Math.min(length() - index, values.size() - srcIndex);
        }

        List<Value> setValues = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            ValueImpl value = (ValueImpl) values.get(srcIndex + i);
            setValues.add(ValueImpl.prepareForAssignment(value, new Component()));
        }
        try {
            JDWP.ArrayReference.SetValues.process(vm, this, index, setValues);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public String toString() {
        return "instance of " + arrayType().componentTypeName() + "[" + length() + "] (id=" + uniqueID() + ")";
    }

    byte typeValueKey() {
        return JDWP.Tag.ARRAY;
    }

    class Component implements ValueContainer {
        public Type type() throws ClassNotLoadedException {
            return arrayType().componentType();
        }
        public String typeName() {
            return arrayType().componentTypeName();
        }
        public String signature() {
            return arrayType().componentSignature();
        }
        public Type findType(String signature) throws ClassNotLoadedException {
            return arrayType().findType(signature);
        }
    }
}
