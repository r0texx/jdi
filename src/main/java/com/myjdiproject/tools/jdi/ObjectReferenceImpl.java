/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.ClassType;
import com.myjdiproject.jdi.Field;
import com.myjdiproject.jdi.IncompatibleThreadStateException;
import com.myjdiproject.jdi.InterfaceType;
import com.myjdiproject.jdi.InvalidTypeException;
import com.myjdiproject.jdi.InvocationException;
import com.myjdiproject.jdi.Method;
import com.myjdiproject.jdi.ObjectReference;
import com.myjdiproject.jdi.ReferenceType;
import com.myjdiproject.jdi.ThreadReference;
import com.myjdiproject.jdi.Type;
import com.myjdiproject.jdi.Value;
import com.myjdiproject.jdi.VirtualMachine;

public class ObjectReferenceImpl extends ValueImpl implements ObjectReference, VMListener {
    protected final long ref;
    private ReferenceType type = null;
    private int gcDisableCount = 0;
    boolean addedListener = false;

    protected static class Cache {
        JDWP.ObjectReference.MonitorInfo monitorInfo = null;
    }

    private static final Cache noInitCache = new Cache();
    private static final Cache markerCache = new Cache();
    private Cache cache = noInitCache;

    private void disableCache() {
        synchronized (vm.state()) {
            cache = null;
        }
    }

    private void enableCache() {
        synchronized (vm.state()) {
            cache = markerCache;
        }
    }

    protected Cache newCache() {
        return new Cache();
    }

    protected Cache getCache() {
        synchronized (vm.state()) {
            if (cache == noInitCache) {
                if (vm.state().isSuspended()) {
                    enableCache();
                } else {
                    disableCache();
                }
            }
            if (cache == markerCache) {
                cache = newCache();
            }
            return cache;
        }
    }

    protected ClassTypeImpl invokableReferenceType(Method method) {
        return (ClassTypeImpl) referenceType();
    }

    ObjectReferenceImpl(VirtualMachine aVm, long aRef) {
        super(aVm);
        ref = aRef;
    }

    protected String description() {
        return "ObjectReference " + uniqueID();
    }

    public boolean vmSuspended(VMAction action) {
        enableCache();
        return true;
    }

    public boolean vmNotSuspended(VMAction action) {
        synchronized (vm.state()) {
            disableCache();
            if (addedListener) {
                addedListener = false;
                return false;  // false says remove
            } else {
                return true;
            }
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof ObjectReferenceImpl other) {
            return ref() == other.ref() && super.equals(obj);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Long.hashCode(ref());
    }

    public Type type() {
        return referenceType();
    }

    public ReferenceType referenceType() {
        if (type == null) {
            try {
                type = JDWP.ObjectReference.ReferenceType.process(vm, this).referenceType;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return type;
    }

    public Value getValue(Field sig) {
        List<Field> list = new ArrayList<>(1);
        list.add(sig);
        Map<Field, Value> map = getValues(list);
        return map.get(sig);
    }

    public Map<Field,Value> getValues(List<? extends Field> theFields) {
        List<Field> staticFields = new ArrayList<>(0);
        int size = theFields.size();
        List<Field> instanceFields = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            Field field = theFields.get(i);
            if (field.isStatic())
                staticFields.add(field);
            else {
                instanceFields.add(field);
            }
        }

        Map<Field, Value> map;
        if (!staticFields.isEmpty()) {
            map = referenceType().getValues(staticFields);
        } else {
            map = new HashMap<>(size);
        }

        size = instanceFields.size();

        List<Value> values;
        try {
            values = JDWP.ObjectReference.GetValues.process(vm, this, instanceFields).values;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }

        for (int i = 0; i < size; i++) {
            FieldImpl field = (FieldImpl) instanceFields.get(i);
            map.put(field, values.get(i));
        }

        return map;
    }

    public void setValue(Field field, Value value) throws InvalidTypeException, ClassNotLoadedException {
        if (field.isStatic()) {
            ReferenceType type = referenceType();
            if (type instanceof ClassType) {
                ((ClassType) type).setValue(field, value);
                return;
            } else {
                throw new IllegalArgumentException("Invalid type for static field set");
            }
        }

        try {
            List<JDWP.ObjectReference.SetValues.FieldValue> fvals = new ArrayList<>(1);
            fvals.add(new JDWP.ObjectReference.SetValues.FieldValue(field.ref(), ValueImpl.prepareForAssignment(value, (FieldImpl) field)));
            try {
                JDWP.ObjectReference.SetValues.process(vm, this, fvals);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        } catch (ClassNotLoadedException e) {
            if (value != null) {
                throw e;
            }
        }
    }

    PacketStream sendInvokeCommand(ThreadReferenceImpl thread, ClassTypeImpl refType, MethodImpl method, List<Value> args, int options) {
        CommandSender sender = () -> JDWP.ObjectReference.InvokeMethod.enqueueCommand(vm, ObjectReferenceImpl.this, thread, refType, method.ref(), args, options);
        PacketStream stream;
        if ((options & INVOKE_SINGLE_THREADED) != 0) {
            stream = thread.sendResumingCommand(sender);
        } else {
            stream = vm.sendResumingCommand(sender);
        }
        return stream;
    }

    public Value invokeMethod(ThreadReference threadIntf, Method methodIntf, List<? extends Value> origArguments, int options) throws InvalidTypeException, IncompatibleThreadStateException, InvocationException, ClassNotLoadedException {
        MethodImpl method = (MethodImpl) methodIntf;
        ThreadReferenceImpl thread = (ThreadReferenceImpl) threadIntf;

        if (method.isStatic()) {
            if (referenceType() instanceof InterfaceType refType) {
                return refType.invokeMethod(thread, method, origArguments, options);
            } else if (referenceType() instanceof ClassType refType) {
                return refType.invokeMethod(thread, method, origArguments, options);
            } else {
                throw new IllegalArgumentException("Invalid type for static method invocation");
            }
        }

        List<Value> arguments = method.validateAndPrepareArgumentsForInvoke(origArguments);

        JDWP.ObjectReference.InvokeMethod ret;
        try {
            PacketStream stream = sendInvokeCommand(thread, invokableReferenceType(method), method, arguments, options);
            ret = JDWP.ObjectReference.InvokeMethod.waitForReply(stream);
        } catch (JDWPException exc) {
            if (exc.errorCode() == JDWP.Error.INVALID_THREAD) {
                throw new IncompatibleThreadStateException();
            } else {
                throw exc.toJDIException();
            }
        }

        if ((options & INVOKE_SINGLE_THREADED) == 0) {
            vm.notifySuspend();
        }

        if (ret.exception != null) {
            throw new InvocationException(ret.exception);
        } else {
            return ret.returnValue;
        }
    }

    public synchronized void disableCollection() {
        if (gcDisableCount == 0) {
            try {
                JDWP.ObjectReference.DisableCollection.process(vm, this);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        gcDisableCount++;
    }

    public synchronized void enableCollection() {
        gcDisableCount--;
        if (gcDisableCount == 0) {
            try {
                JDWP.ObjectReference.EnableCollection.process(vm, this);
            } catch (JDWPException exc) {
                if (exc.errorCode() != JDWP.Error.INVALID_OBJECT) {
                    throw exc.toJDIException();
                }
            }
        }
    }

    public boolean isCollected() {
        try {
            return JDWP.ObjectReference.IsCollected.process(vm, this).isCollected;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public long uniqueID() {
        return ref();
    }

    JDWP.ObjectReference.MonitorInfo jdwpMonitorInfo() throws IncompatibleThreadStateException {
        JDWP.ObjectReference.MonitorInfo info = null;
        try {
            Cache local;
            synchronized (vm.state()) {
                local = getCache();

                if (local != null) {
                    info = local.monitorInfo;
                    if (info == null && !vm.state().hasListener(this)) {
                        vm.state().addListener(this);
                        addedListener = true;
                    }
                }
            }
            if (info == null) {
                info = JDWP.ObjectReference.MonitorInfo.process(vm, this);
                if (local != null) {
                    local.monitorInfo = info;
                }
            }
        } catch (JDWPException exc) {
             if (exc.errorCode() == JDWP.Error.THREAD_NOT_SUSPENDED) {
                 throw new IncompatibleThreadStateException();
             } else {
                 throw exc.toJDIException();
             }
         }
        return info;
    }

    public List<ThreadReference> waitingThreads() throws IncompatibleThreadStateException {
        return jdwpMonitorInfo().waiters;
    }

    public ThreadReference owningThread() throws IncompatibleThreadStateException {
        return jdwpMonitorInfo().owner;
    }

    public int entryCount() throws IncompatibleThreadStateException {
        return jdwpMonitorInfo().entryCount;
    }

    public List<ObjectReference> referringObjects(long maxReferrers) {
        if (!vm.canGetInstanceInfo()) {
            throw new UnsupportedOperationException("target does not support getting referring objects");
        }

        if (maxReferrers < 0) {
            throw new IllegalArgumentException("maxReferrers is less than zero: " + maxReferrers);
        }

        int intMax = (maxReferrers > Integer.MAX_VALUE) ? Integer.MAX_VALUE: (int) maxReferrers;
        try {
            return JDWP.ObjectReference.ReferringObjects.process(vm, this, intMax).referringObjects;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public long ref() {
        return ref;
    }

    boolean isClassObject() {
        return "java.lang.Class".equals(referenceType().name());
    }

    ValueImpl prepareForAssignmentTo(ValueContainer destination) {
        return this;
    }

    public String toString() {
        return "instance of " + referenceType().name() + "(id=" + uniqueID() + ")";
    }

    byte typeValueKey() {
        return JDWP.Tag.OBJECT;
    }

    private static boolean isNonVirtual(int options) {
        return (options & INVOKE_NONVIRTUAL) != 0;
    }
}
