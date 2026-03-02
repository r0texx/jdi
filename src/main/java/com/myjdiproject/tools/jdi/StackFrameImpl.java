/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.myjdiproject.jdi.AbsentInformationException;
import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.IncompatibleThreadStateException;
import com.myjdiproject.jdi.InternalException;
import com.myjdiproject.jdi.InvalidStackFrameException;
import com.myjdiproject.jdi.InvalidTypeException;
import com.myjdiproject.jdi.LocalVariable;
import com.myjdiproject.jdi.Location;
import com.myjdiproject.jdi.NativeMethodException;
import com.myjdiproject.jdi.ObjectReference;
import com.myjdiproject.jdi.OpaqueFrameException;
import com.myjdiproject.jdi.StackFrame;
import com.myjdiproject.jdi.ThreadReference;
import com.myjdiproject.jdi.Value;
import com.myjdiproject.jdi.VirtualMachine;

public class StackFrameImpl extends MirrorImpl implements StackFrame, ThreadListener {
    private boolean isValid = true;

    private final ThreadReferenceImpl thread;
    private final long id;
    private final Location location;
    private Map<String, LocalVariable> visibleVariables =  null;
    private ObjectReference thisObject = null;

    StackFrameImpl(VirtualMachine vm, ThreadReferenceImpl thread, long id, Location location) {
        super(vm);
        this.thread = thread;
        this.id = id;
        this.location = location;
        thread.addListener(this);
    }

    public boolean threadResumable(ThreadAction action) {
        synchronized (vm.state()) {
            if (isValid) {
                isValid = false;
                return false;
            } else {
                throw new InternalException("Invalid stack frame thread listener");
            }
        }
    }

    void validateStackFrame() {
        if (!isValid) {
            throw new InvalidStackFrameException("Thread has been resumed");
        }
    }

    public Location location() {
        return location;
    }

    public ThreadReference thread() {
        return thread;
    }

    public boolean equals(Object obj) {
        if (obj instanceof StackFrameImpl other) {
            return id == other.id
                    && thread().equals(other.thread())
                    && location().equals(other.location())
                    && super.equals(obj);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return (thread().hashCode() << 4) + ((int) id);
    }

    public ObjectReference thisObject() {
        MethodImpl currentMethod = (MethodImpl)location.method();
        if (currentMethod.isStatic() || currentMethod.isNative()) {
            return null;
        } else {
            if (thisObject == null) {
                PacketStream ps;
                synchronized (vm.state()) {
                    ps = JDWP.StackFrame.ThisObject.enqueueCommand(vm, thread, id);
                }

                try {
                    thisObject = JDWP.StackFrame.ThisObject.waitForReply(ps).objectThis;
                } catch (JDWPException exc) {
                    switch (exc.errorCode()) {
                    case JDWP.Error.INVALID_FRAMEID:
                    case JDWP.Error.THREAD_NOT_SUSPENDED:
                    case JDWP.Error.INVALID_THREAD:
                        throw new InvalidStackFrameException();
                    default:
                        throw exc.toJDIException();
                    }
                }
            }
        }
        return thisObject;
    }

    private void createVisibleVariables() throws AbsentInformationException {
        if (visibleVariables == null) {
            List<LocalVariable> allVariables = location.method().variables();
            Map<String, LocalVariable> map = new HashMap<>(allVariables.size());

            for (LocalVariable variable : allVariables) {
                String name = variable.name();
                if (variable.isVisible(this)) {
                    LocalVariable existing = map.get(name);
                    if ((existing == null) ||
                        ((LocalVariableImpl)variable).hides(existing)) {
                        map.put(name, variable);
                    }
                }
            }
            visibleVariables = map;
        }
    }

    public List<LocalVariable> visibleVariables() throws AbsentInformationException {
        createVisibleVariables();
        List<LocalVariable> mapAsList = new ArrayList<>(visibleVariables.values());
        Collections.sort(mapAsList);
        return mapAsList;
    }

    public LocalVariable visibleVariableByName(String name) throws AbsentInformationException  {
        createVisibleVariables();
        return visibleVariables.get(name);
    }

    public Value getValue(LocalVariable variable) {
        List<LocalVariable> list = new ArrayList<>(1);
        list.add(variable);
        return getValues(list).get(variable);
    }

    public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> variables) {
        int count = variables.size();
        List<JDWP.StackFrame.GetValues.SlotInfo> slots = new ArrayList<>(count);
        for (LocalVariable localVariable : variables) {
            LocalVariableImpl variable = (LocalVariableImpl) localVariable;
            if (!variable.isVisible(this)) {
                throw new IllegalArgumentException(variable.name() + " is not valid at this frame location");
            }
            slots.add(new JDWP.StackFrame.GetValues.SlotInfo(variable.slot(), (byte) variable.signature().charAt(0)));
        }

        PacketStream ps;
        synchronized (vm.state()) {
            ps = JDWP.StackFrame.GetValues.enqueueCommand(vm, thread, id, slots);
        }

        List<Value> values;
        try {
            values = JDWP.StackFrame.GetValues.waitForReply(ps).values;
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
                case JDWP.Error.INVALID_FRAMEID:
                case JDWP.Error.THREAD_NOT_SUSPENDED:
                case JDWP.Error.INVALID_THREAD:
                    throw new InvalidStackFrameException();
                default:
                    throw exc.toJDIException();
            }
        }

        Map<LocalVariable, Value> map = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            LocalVariableImpl variable = (LocalVariableImpl) variables.get(i);
            map.put(variable, values.get(i));
        }
        return map;
    }

    public void setValue(LocalVariable variableIntf, Value value) throws InvalidTypeException, ClassNotLoadedException {
        LocalVariableImpl variable = (LocalVariableImpl) variableIntf;
        if (!variable.isVisible(this)) {
            throw new IllegalArgumentException(variable.name() + " is not valid at this frame location");
        }

        try {
            value = ValueImpl.prepareForAssignment(value, variable);

            List<JDWP.StackFrame.SetValues.SlotInfo> slotVals = new ArrayList<>(1);
            slotVals.add(new JDWP.StackFrame.SetValues.SlotInfo(variable.slot(), value));

            PacketStream ps;
            synchronized (vm.state()) {
                ps = JDWP.StackFrame.SetValues.enqueueCommand(vm, thread, id, slotVals);
            }

            try {
                JDWP.StackFrame.SetValues.waitForReply(ps);
            } catch (JDWPException exc) {
                switch (exc.errorCode()) {
                case JDWP.Error.INVALID_FRAMEID:
                case JDWP.Error.THREAD_NOT_SUSPENDED:
                case JDWP.Error.INVALID_THREAD:
                    throw new InvalidStackFrameException();
                default:
                    throw exc.toJDIException();
                }
            }
        } catch (ClassNotLoadedException e) {
            if (value != null) {
                throw e;
            }
        }
    }

    public List<Value> getArgumentValues() {
        MethodImpl mmm = (MethodImpl) location.method();
        List<String> argSigs = mmm.argumentSignatures();
        int count = argSigs.size();
        List<JDWP.StackFrame.GetValues.SlotInfo> slots = new ArrayList<>(count);

        int slot;
        if (mmm.isStatic()) {
            slot = 0;
        } else {
            slot = 1;
        }
        for (String argSig : argSigs) {
            char sigChar = argSig.charAt(0);
            slots.add(new JDWP.StackFrame.GetValues.SlotInfo(slot++, (byte) sigChar));
            if (sigChar == 'J' || sigChar == 'D') {
                slot++;
            }
        }

        PacketStream ps;
        synchronized (vm.state()) {
            ps = JDWP.StackFrame.GetValues.enqueueCommand(vm, thread, id, slots);
        }

        List<Value> values;
        try {
            values = JDWP.StackFrame.GetValues.waitForReply(ps).values;
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
                case JDWP.Error.INVALID_FRAMEID:
                case JDWP.Error.THREAD_NOT_SUSPENDED:
                case JDWP.Error.INVALID_THREAD:
                    throw new InvalidStackFrameException();
                default:
                    throw exc.toJDIException();
            }
        }
        return values;
    }

    void pop() throws IncompatibleThreadStateException {
        CommandSender sender = () -> JDWP.StackFrame.PopFrames.enqueueCommand(vm, thread, id);
        try {
            PacketStream stream = thread.sendResumingCommand(sender);
            JDWP.StackFrame.PopFrames.waitForReply(stream);
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.OPAQUE_FRAME:
                if (thread.isVirtual()) {
                    for (int i = 0; i < 2; i++) {
                        StackFrameImpl sf;
                        try {
                            sf = (StackFrameImpl)thread.frame(i);
                        } catch (IndexOutOfBoundsException e) {
                            break;
                        }
                        MethodImpl meth = (MethodImpl) sf.location().method();
                        if (meth.isNative()) {
                            throw new NativeMethodException();
                        }
                    }
                    throw new OpaqueFrameException();
                } else {
                    throw new NativeMethodException();
                }
            case JDWP.Error.THREAD_NOT_SUSPENDED:
                throw new IncompatibleThreadStateException("Thread not current or suspended");
            case JDWP.Error.INVALID_THREAD:   /* zombie */
                throw new IncompatibleThreadStateException("zombie");
            case JDWP.Error.NO_MORE_FRAMES:
                throw new InvalidStackFrameException("No more frames on the stack");
            default:
                throw exc.toJDIException();
            }
        }
        vm.state().freeze();
    }

    public String toString() {
       return location.toString() + " in thread " + thread.toString();
    }
}
