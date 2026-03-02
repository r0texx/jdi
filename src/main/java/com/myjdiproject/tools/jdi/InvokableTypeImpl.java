/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.ClassType;
import com.myjdiproject.jdi.IncompatibleThreadStateException;
import com.myjdiproject.jdi.InterfaceType;
import com.myjdiproject.jdi.InvalidTypeException;
import com.myjdiproject.jdi.InvocationException;
import com.myjdiproject.jdi.Method;
import com.myjdiproject.jdi.ReferenceType;
import com.myjdiproject.jdi.ThreadReference;
import com.myjdiproject.jdi.Value;
import com.myjdiproject.jdi.VirtualMachine;

abstract class InvokableTypeImpl extends ReferenceTypeImpl {
    interface InvocationResult {
        ObjectReferenceImpl getException();
        ValueImpl getResult();
    }

    InvokableTypeImpl(VirtualMachine aVm, long aRef) {
        super(aVm, aRef);
    }

    public final Value invokeMethod(ThreadReference threadIntf, Method methodIntf, List<? extends Value> origArguments, int options) throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {
        validateMirror(threadIntf);
        validateMirror(methodIntf);
        validateMirrorsOrNulls(origArguments);
        MethodImpl method = (MethodImpl) methodIntf;
        ThreadReferenceImpl thread = (ThreadReferenceImpl) threadIntf;
        validateMethodInvocation(method);
        List<Value> arguments = method.validateAndPrepareArgumentsForInvoke(origArguments);
        InvocationResult ret;
        try {
            PacketStream stream = sendInvokeCommand(thread, method, arguments, options);
            ret = waitForReply(stream);
        } catch (JDWPException exc) {
            if (exc.errorCode() == JDWP.Error.INVALID_THREAD) {
                throw new IncompatibleThreadStateException();
            } else {
                throw exc.toJDIException();
            }
        }

        if ((options & ClassType.INVOKE_SINGLE_THREADED) == 0) {
            vm.notifySuspend();
        }
        if (ret.getException() != null) {
            throw new InvocationException(ret.getException());
        } else {
            return ret.getResult();
        }
    }

    @Override
    boolean isAssignableTo(ReferenceType type) {
        ClassTypeImpl superclazz = (ClassTypeImpl) superclass();
        if (this.equals(type)) {
            return true;
        } else if ((superclazz != null) && superclazz.isAssignableTo(type)) {
            return true;
        } else {
            List<InterfaceType> interfaces = interfaces();
            for (InterfaceType interfaceType : interfaces) {
                InterfaceTypeImpl interfaze = (InterfaceTypeImpl) interfaceType;
                if (interfaze.isAssignableTo(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    final void addVisibleMethods(Map<String, Method> methodMap, Set<InterfaceType> seenInterfaces) {
        for (InterfaceType interfaceType : interfaces()) {
            InterfaceTypeImpl interfaze = (InterfaceTypeImpl) interfaceType;
            if (interfaze != null && !seenInterfaces.contains(interfaze)) {
                interfaze.addVisibleMethods(methodMap, seenInterfaces);
                seenInterfaces.add(interfaze);
            }
        }
        ClassTypeImpl clazz = (ClassTypeImpl) superclass();
        if (clazz != null) {
            clazz.addVisibleMethods(methodMap, seenInterfaces);
        }
        addToMethodMap(methodMap, methods());
    }

    final void addInterfaces(List<InterfaceType> list) {
        List<InterfaceType> immediate = interfaces();
        list.addAll(immediate);
        for (InterfaceType interfaceType : immediate) {
            InterfaceTypeImpl interfaze = (InterfaceTypeImpl) interfaceType;
            interfaze.addInterfaces(list);
        }
        ClassTypeImpl superclass = (ClassTypeImpl) superclass();
        if (superclass != null) {
            superclass.addInterfaces(list);
        }
    }

    final List<InterfaceType> getAllInterfaces() {
        List<InterfaceType> all = new ArrayList<>();
        addInterfaces(all);
        return all;
    }

    public final List<Method> allMethods() {
        ArrayList<Method> list = new ArrayList<>(methods());
        ClassType clazz = superclass();
        while (clazz != null) {
            list.addAll(clazz.methods());
            clazz = clazz.superclass();
        }
        for (InterfaceType interfaze : getAllInterfaces()) {
            list.addAll(interfaze.methods());
        }
        return list;
    }

    @Override
    final List<ReferenceType> inheritedTypes() {
        List<ReferenceType> inherited = new ArrayList<>();
        if (superclass() != null) {
            inherited.add(superclass());
        }
        inherited.addAll(interfaces());
        return inherited;
    }

    private PacketStream sendInvokeCommand(ThreadReferenceImpl thread, MethodImpl method, List<Value> args, int options) {
        CommandSender sender = getInvokeMethodSender(thread, method, args, options);
        PacketStream stream;
        if ((options & ClassType.INVOKE_SINGLE_THREADED) != 0) {
            stream = thread.sendResumingCommand(sender);
        } else {
            stream = vm.sendResumingCommand(sender);
        }
        return stream;
    }

    private void validateMethodInvocation(Method method) {
        if (!canInvoke(method)) {
            throw new IllegalArgumentException("Invalid method");
        }
        if (!method.isStatic()) {
            throw new IllegalArgumentException("Cannot invoke instance method on a class/interface type");
        } else if (method.isStaticInitializer()) {
            throw new IllegalArgumentException("Cannot invoke static initializer");
        }
    }

    abstract CommandSender getInvokeMethodSender(ThreadReferenceImpl thread, MethodImpl method, List<Value> args, int options);

    abstract InvocationResult waitForReply(PacketStream stream) throws JDWPException;

    abstract ClassType superclass();

    abstract List<InterfaceType> interfaces();

    abstract boolean canInvoke(Method method);
}
