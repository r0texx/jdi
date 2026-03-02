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

import com.myjdiproject.jdi.AbsentInformationException;
import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.InternalException;
import com.myjdiproject.jdi.LocalVariable;
import com.myjdiproject.jdi.Location;
import com.myjdiproject.jdi.Method;
import com.myjdiproject.jdi.StackFrame;
import com.myjdiproject.jdi.Type;
import com.myjdiproject.jdi.VirtualMachine;

public class LocalVariableImpl extends MirrorImpl implements LocalVariable, ValueContainer {
    private final Method method;
    private final int slot;
    private final Location scopeStart;
    private final Location scopeEnd;
    private final String name;
    private final String signature;
    private final String genericSignature;

    LocalVariableImpl(VirtualMachine vm, Method method, int slot, Location scopeStart, Location scopeEnd, String name, String signature, String genericSignature) {
        super(vm);
        this.method = method;
        this.slot = slot;
        this.scopeStart = scopeStart;
        this.scopeEnd = scopeEnd;
        this.name = name;
        this.signature = signature;
        if (genericSignature != null && !genericSignature.isEmpty()) {
            this.genericSignature = genericSignature;
        } else {
            this.genericSignature = null;
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof LocalVariableImpl other) {
            return slot() == other.slot() && scopeStart != null && scopeStart.equals(other.scopeStart) && super.equals(obj);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return (scopeStart.hashCode() << 4) + slot();
    }

    public int compareTo(LocalVariable object) {
        LocalVariableImpl other = (LocalVariableImpl) object;
        int rc = scopeStart.compareTo(other.scopeStart);
        if (rc == 0) {
            rc = slot() - other.slot();
        }
        return rc;
    }

    public String name() {
        return name;
    }

    public String typeName() {
        JNITypeParser parser = new JNITypeParser(signature);
        return parser.typeName();
    }

    public Type type() throws ClassNotLoadedException {
        return findType(signature());
    }

    public Type findType(String signature) throws ClassNotLoadedException {
        ReferenceTypeImpl enclosing = (ReferenceTypeImpl) method.declaringType();
        return enclosing.findType(signature);
    }

    public String signature() {
        return signature;
    }

    public String genericSignature() {
        return genericSignature;
    }

    public boolean isVisible(StackFrame frame) {
        validateMirror(frame);
        Method frameMethod = frame.location().method();

        if (!frameMethod.equals(method)) {
            throw new IllegalArgumentException("frame method different than variable's method");
        }

        if (frameMethod.isNative()) {
            return false;
        }

        return scopeStart.compareTo(frame.location()) <= 0 && scopeEnd.compareTo(frame.location()) >= 0;
    }

    public boolean isArgument() {
        try {
            MethodImpl method = (MethodImpl) scopeStart.method();
            return slot < method.argSlotCount();
        } catch (AbsentInformationException e) {
            throw new InternalException();
        }
    }

    int slot() {
        return slot;
    }

    boolean hides(LocalVariable other) {
        LocalVariableImpl otherImpl = (LocalVariableImpl) other;
        if (!method.equals(otherImpl.method) || !name.equals(otherImpl.name)) {
            return false;
        } else {
            return scopeStart.compareTo(otherImpl.scopeStart) > 0;
        }
    }

    public String toString() {
       return name() + " in " + method.toString() + "@" + scopeStart.toString();
    }
}
