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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.IncompatibleThreadStateException;
import com.myjdiproject.jdi.InternalException;
import com.myjdiproject.jdi.InvalidStackFrameException;
import com.myjdiproject.jdi.InvalidTypeException;
import com.myjdiproject.jdi.Location;
import com.myjdiproject.jdi.MonitorInfo;
import com.myjdiproject.jdi.NativeMethodException;
import com.myjdiproject.jdi.ObjectReference;
import com.myjdiproject.jdi.OpaqueFrameException;
import com.myjdiproject.jdi.ReferenceType;
import com.myjdiproject.jdi.StackFrame;
import com.myjdiproject.jdi.ThreadGroupReference;
import com.myjdiproject.jdi.ThreadReference;
import com.myjdiproject.jdi.Value;
import com.myjdiproject.jdi.VirtualMachine;
import com.myjdiproject.jdi.request.BreakpointRequest;

public class ThreadReferenceImpl extends ObjectReferenceImpl implements ThreadReference {
    static final int SUSPEND_STATUS_SUSPENDED = 0x1;
    static final int SUSPEND_STATUS_BREAK = 0x2;

    private int suspendedZombieCount = 0;

    private ThreadGroupReference threadGroup;
    private volatile boolean isVirtual;
    private volatile boolean isVirtualCached;

    private static class LocalCache {
        JDWP.ThreadReference.Status status = null;
        List<StackFrame> frames = null;
        int framesStart = -1;
        int framesLength = 0;
        int frameCount = -1;
        List<ObjectReference> ownedMonitors = null;
        List<MonitorInfo> ownedMonitorsInfo = null;
        ObjectReference contendedMonitor = null;
        boolean triedCurrentContended = false;
    }

    private LocalCache localCache;

    private void resetLocalCache() {
        localCache = new LocalCache();
    }

    private static class Cache extends ObjectReferenceImpl.Cache {
        String name = null;
    }
    protected ObjectReferenceImpl.Cache newCache() {
        return new Cache();
    }

    private final List<WeakReference<ThreadListener>> listeners = new ArrayList<>();

    ThreadReferenceImpl(VirtualMachine aVm, long aRef) {
        super(aVm, aRef);
        resetLocalCache();
        vm.state().addListener(this);
    }

    protected String description() {
        return "ThreadReference " + uniqueID();
    }

    public boolean vmNotSuspended(VMAction action) {
        if (action.resumingThread() == null) {
            synchronized (vm.state()) {
                processThreadAction(new ThreadAction(this, ThreadAction.THREAD_RESUMABLE));
            }
        }
        return super.vmNotSuspended(action);
    }

    public String name() {
        String name = null;
        try {
            Cache local = (Cache) getCache();

            if (local != null) {
                name = local.name;
            }
            if (name == null) {
                name = JDWP.ThreadReference.Name.process(vm, this).threadName;
                if (local != null) {
                    local.name = name;
                }
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return name;
    }

    PacketStream sendResumingCommand(CommandSender sender) {
        synchronized (vm.state()) {
            processThreadAction(new ThreadAction(this, ThreadAction.THREAD_RESUMABLE));
            return sender.send();
        }
    }

    public void suspend() {
        try {
            JDWP.ThreadReference.Suspend.process(vm, this);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public void resume() {
        if (suspendedZombieCount > 0) {
            suspendedZombieCount--;
            return;
        }

        PacketStream stream;
        synchronized (vm.state()) {
            processThreadAction(new ThreadAction(this, ThreadAction.THREAD_RESUMABLE));
            stream = JDWP.ThreadReference.Resume.enqueueCommand(vm, this);
        }
        try {
            JDWP.ThreadReference.Resume.waitForReply(stream);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public int suspendCount() {
        if (suspendedZombieCount > 0) {
            return suspendedZombieCount;
        }

        try {
            return JDWP.ThreadReference.SuspendCount.process(vm, this).suspendCount;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public void stop(ObjectReference throwable) {
        try {
            JDWP.ThreadReference.Stop.process(vm, this, (ObjectReferenceImpl) throwable);
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.OPAQUE_FRAME:
                assert isVirtual(); // can only happen with virtual threads
                throw new OpaqueFrameException();
            case JDWP.Error.THREAD_NOT_SUSPENDED:
                assert isVirtual(); // can only happen with virtual threads
                throw new IllegalThreadStateException("virtual thread not suspended");
            case JDWP.Error.INVALID_THREAD:
                throw new IllegalThreadStateException("thread has terminated");
            default:
                throw exc.toJDIException();
            }
        }
    }

    public void interrupt() {
        try {
            JDWP.ThreadReference.Interrupt.process(vm, this);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    private JDWP.ThreadReference.Status jdwpStatus() {
        LocalCache snapshot = localCache;
        JDWP.ThreadReference.Status myStatus = snapshot.status;
        try {
             if (myStatus == null) {
                 myStatus = JDWP.ThreadReference.Status.process(vm, this);
                if ((myStatus.suspendStatus & SUSPEND_STATUS_SUSPENDED) != 0) {
                    snapshot.status = myStatus;
                }
            }
         } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return myStatus;
    }

    public int status() {
        return jdwpStatus().threadStatus;
    }

    public boolean isSuspended() {
        return ((suspendedZombieCount > 0) || ((jdwpStatus().suspendStatus & SUSPEND_STATUS_SUSPENDED) != 0));
    }

    public boolean isAtBreakpoint() {
        try {
            StackFrame frame = frame(0);
            Location location = frame.location();
            List<BreakpointRequest> requests = vm.eventRequestManager().breakpointRequests();
            for (BreakpointRequest request : requests) {
                if (location.equals(request.location())) {
                    return true;
                }
            }
            return false;
        } catch (IndexOutOfBoundsException iobe) {
            return false;
        }
    }

    @Override
    public ThreadGroupReference threadGroup() {
        if (threadGroup == null) {
            try {
                threadGroup = JDWP.ThreadReference.ThreadGroup.process(vm, this).group;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return threadGroup;
    }

    @Override
    public int frameCount() {
        LocalCache snapshot = localCache;
        try {
            if (snapshot.frameCount == -1) {
                snapshot.frameCount = JDWP.ThreadReference.FrameCount.process(vm, this).frameCount;
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return snapshot.frameCount;
    }

    @Override
    public List<StackFrame> frames() {
        return privateFrames(0, -1);
    }

    @Override
    public StackFrame frame(int index) {
        List<StackFrame> list = privateFrames(index, 1);
        return list.getFirst();
    }

    private boolean isSubrange(LocalCache snapshot, int start, int length) {
        if (start < snapshot.framesStart) {
            return false;
        }
        if (length == -1) {
            return (snapshot.framesLength == -1);
        }
        if (snapshot.framesLength == -1) {
            if ((start + length) > (snapshot.framesStart + snapshot.frames.size())) {
                throw new IndexOutOfBoundsException();
            }
            return true;
        }
        return ((start + length) <= (snapshot.framesStart + snapshot.framesLength));
    }

    @Override
    public List<StackFrame> frames(int start, int length) {
        return privateFrames(start, length);
    }

    private synchronized List<StackFrame> privateFrames(int start, int length) {
        LocalCache snapshot = localCache;
        try {
            if (snapshot.frames == null || !isSubrange(snapshot, start, length)) {
                snapshot.frames = JDWP.ThreadReference.Frames.process(vm, this, start, length).frames;
                snapshot.framesStart = start;
                snapshot.framesLength = length;
                return Collections.unmodifiableList(snapshot.frames);
            } else {
                int fromIndex = start - snapshot.framesStart;
                int toIndex;
                if (length == -1) {
                    toIndex = snapshot.frames.size() - fromIndex;
                } else {
                    toIndex = fromIndex + length;
                }
                return Collections.unmodifiableList(snapshot.frames.subList(fromIndex, toIndex));
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    @Override
    public List<ObjectReference> ownedMonitors() {
        LocalCache snapshot = localCache;
        try {
            if (snapshot.ownedMonitors == null) {
                snapshot.ownedMonitors = JDWP.ThreadReference.OwnedMonitors.process(vm, this).owned;
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return snapshot.ownedMonitors;
    }

    @Override
    public ObjectReference currentContendedMonitor() {
        LocalCache snapshot = localCache;
        try {
            if (snapshot.contendedMonitor == null && !snapshot.triedCurrentContended) {
                snapshot.contendedMonitor = JDWP.ThreadReference.CurrentContendedMonitor.process(vm, this).monitor;
                snapshot.triedCurrentContended = true;
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return snapshot.contendedMonitor;
    }

    @Override
    public List<MonitorInfo> ownedMonitorsAndFrames() {
        LocalCache snapshot = localCache;
        try {
            if (snapshot.ownedMonitorsInfo == null) {
                snapshot.ownedMonitorsInfo = JDWP.ThreadReference.OwnedMonitorsStackDepthInfo.process(vm, this).owned;
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return snapshot.ownedMonitorsInfo;
    }

    @Override
    public void popFrames(StackFrame frame) throws IncompatibleThreadStateException {
        if (!frame.thread().equals(this)) {
            throw new IllegalArgumentException("frame does not belong to this thread");
        }
        if (!vm.canPopFrames()) {
            throw new UnsupportedOperationException("target does not support popping frames");
        }
        ((StackFrameImpl) frame).pop();
    }

    @Override
    public void forceEarlyReturn(Value returnValue) throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException {
        if (!vm.canForceEarlyReturn()) {
            throw new UnsupportedOperationException("target does not support the forcing of a method to return early");
        }

        StackFrameImpl sf;
        try {
           sf = (StackFrameImpl) frame(0);
        } catch (IndexOutOfBoundsException exc) {
           throw new InvalidStackFrameException("No more frames on the stack");
        }
        MethodImpl meth = (MethodImpl)sf.location().method();
        ValueImpl convertedValue  = ValueImpl.prepareForAssignment(returnValue, meth.getReturnValueContainer());

        try {
            JDWP.ThreadReference.ForceEarlyReturn.process(vm, this, convertedValue);
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.OPAQUE_FRAME:
                if (isVirtual() && !meth.isNative()) {
                    throw new OpaqueFrameException();
                } else {
                    throw new NativeMethodException();
                }
            case JDWP.Error.THREAD_NOT_SUSPENDED:
                throw new IncompatibleThreadStateException("Thread not suspended");
            case JDWP.Error.NO_MORE_FRAMES:
                throw new InvalidStackFrameException("No more frames on the stack");
            default:
                throw exc.toJDIException();
            }
        }
    }

    @Override
    public boolean isVirtual() {
        if (isVirtualCached) {
            return isVirtual;
        }
        boolean result = false;
        if (vm.mayCreateVirtualThreads()) {
            try {
                result = JDWP.ThreadReference.IsVirtual.process(vm, this).isVirtual;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        isVirtual = result;
        isVirtualCached = true;
        return result;
    }

    @Override
    public String toString() {
        return "instance of " + referenceType().name() + "(name='" + name() + "', " + "id=" + uniqueID() + ")";
    }

    byte typeValueKey() {
        return JDWP.Tag.THREAD;
    }

    void addListener(ThreadListener listener) {
        synchronized (vm.state()) {
            listeners.add(new WeakReference<>(listener));
        }
    }

    private void processThreadAction(ThreadAction action) {
        synchronized (vm.state()) {
            Iterator<WeakReference<ThreadListener>> iter = listeners.iterator();
            while (iter.hasNext()) {
                WeakReference<ThreadListener> ref = iter.next();
                ThreadListener listener = ref.get();
                if (listener != null) {
                    switch (action.id()) {
                        case ThreadAction.THREAD_RESUMABLE:
                            if (!listener.threadResumable(action)) {
                                iter.remove();
                            }
                            break;
                    }
                } else {
                    iter.remove();
                }
            }
            resetLocalCache();
        }
    }
}
