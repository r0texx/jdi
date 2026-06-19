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

import com.myjdiproject.jdi.*;
import com.myjdiproject.jdi.request.*;

import java.util.*;

class EventRequestManagerImpl extends MirrorImpl implements EventRequestManager {
    private final List<EventRequest>[] requestLists;
    private static int methodExitEventCmd = 0;

    static int JDWPtoJDISuspendPolicy(byte jdwpPolicy) {
        return switch (jdwpPolicy) {
            case JDWP.SuspendPolicy.ALL -> EventRequest.SUSPEND_ALL;
            case JDWP.SuspendPolicy.EVENT_THREAD -> EventRequest.SUSPEND_EVENT_THREAD;
            case JDWP.SuspendPolicy.NONE -> EventRequest.SUSPEND_NONE;
            default -> throw new IllegalArgumentException("Illegal policy constant: " + jdwpPolicy);
        };
    }

    static byte JDItoJDWPSuspendPolicy(int jdiPolicy) {
        return switch (jdiPolicy) {
            case EventRequest.SUSPEND_ALL -> JDWP.SuspendPolicy.ALL;
            case EventRequest.SUSPEND_EVENT_THREAD -> JDWP.SuspendPolicy.EVENT_THREAD;
            case EventRequest.SUSPEND_NONE -> JDWP.SuspendPolicy.NONE;
            default -> throw new IllegalArgumentException("Illegal policy constant: " + jdiPolicy);
        };
    }

    public boolean equals(Object obj) {
        return this == obj;
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }

    private abstract class EventRequestImpl extends MirrorImpl implements EventRequest {
        int id;
        final List<JDWP.EventRequest.Set.Modifier> filters = new ArrayList<>();
        boolean isEnabled = false;
        boolean deleted = false;
        byte suspendPolicy = JDWP.SuspendPolicy.ALL;
        private Map<Object, Object> clientProperties = null;

        EventRequestImpl() {
            super(EventRequestManagerImpl.this.vm);
        }

        public boolean equals(Object obj) {
            return this == obj;
        }

        public int hashCode() {
            return System.identityHashCode(this);
        }

        abstract int eventCmd();

        InvalidRequestStateException invalidState() {
            return new InvalidRequestStateException(toString());
        }

        List<EventRequest> requestList() {
            return EventRequestManagerImpl.this.requestList(eventCmd());
        }

        public void delete() {
            if (!deleted) {
                requestList().remove(this);
                disable();
                deleted = true;
            }
        }

        public boolean isEnabled() {
            return isEnabled;
        }

        public void enable() {
            setEnabled(true);
        }

        public void disable() {
            setEnabled(false);
        }

        public synchronized void setEnabled(boolean val) {
            if (deleted) {
                throw invalidState();
            } else {
                if (val != isEnabled) {
                    if (isEnabled) {
                        clear();
                    } else {
                        set();
                    }
                }
            }
        }

        public synchronized void addCountFilter(int count) {
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            if (count < 1) {
                throw new IllegalArgumentException("count is less than one");
            }
            filters.add(JDWP.EventRequest.Set.Modifier.Count.create(count));
        }

        public void setSuspendPolicy(int policy) {
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            suspendPolicy = JDItoJDWPSuspendPolicy(policy);
        }

        public int suspendPolicy() {
            return JDWPtoJDISuspendPolicy(suspendPolicy);
        }

        synchronized void set() {
            try {
                id = JDWP.EventRequest.Set.process(vm, (byte) eventCmd(), suspendPolicy, filters).requestID;
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
            isEnabled = true;
        }

        synchronized void clear() {
            try {
                JDWP.EventRequest.Clear.process(vm, (byte) eventCmd(), id);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
            isEnabled = false;
        }

        private Map<Object, Object> getProperties() {
            if (clientProperties == null) {
                clientProperties = new HashMap<>(2);
            }
            return clientProperties;
        }

        public final Object getProperty(Object key) {
            if (clientProperties == null) {
                return null;
            } else {
                return getProperties().get(key);
            }
        }

        public final void putProperty(Object key, Object value) {
            if (value != null) {
                getProperties().put(key, value);
            } else {
                getProperties().remove(key);
            }
        }
    }

    abstract class ThreadVisibleEventRequestImpl extends EventRequestImpl {
        public synchronized void addThreadFilter(ThreadReference thread) {
            validateMirror(thread);
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            filters.add(JDWP.EventRequest.Set.Modifier.ThreadOnly.create((ThreadReferenceImpl) thread));
        }
    }

    abstract class ThreadLifecycleEventRequestImpl extends ThreadVisibleEventRequestImpl {
        public synchronized void addPlatformThreadsOnlyFilter() {
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            if (vm.mayCreateVirtualThreads()) {
                filters.add(JDWP.EventRequest.Set.Modifier.PlatformThreadsOnly.create());
            }
        }
    }

    abstract class ClassVisibleEventRequestImpl extends ThreadVisibleEventRequestImpl {
        public synchronized void addClassFilter(ReferenceType clazz) {
            validateMirror(clazz);
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            filters.add(JDWP.EventRequest.Set.Modifier.ClassOnly.create((ReferenceTypeImpl) clazz));
        }

        public synchronized void addClassFilter(String classPattern) {
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            if (classPattern == null) {
                throw new NullPointerException();
            }
            filters.add(JDWP.EventRequest.Set.Modifier.ClassMatch.create(classPattern));
        }

        public synchronized void addClassExclusionFilter(String classPattern) {
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            if (classPattern == null) {
                throw new NullPointerException();
            }
            filters.add(JDWP.EventRequest.Set.Modifier.ClassExclude.create(classPattern));
        }

        public synchronized void addClassSetExclusionFilter(java.util.Set<String> classNames) {
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            if (classNames == null) {
                throw new NullPointerException();
            }
            if (!classNames.isEmpty()) {
                filters.add(JDWP.EventRequest.Set.Modifier.ClassSetExclude.create(classNames));
            }
        }

        public synchronized void addInstanceFilter(ObjectReference instance) {
            validateMirror(instance);
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            if (!vm.canUseInstanceFilters()) {
                throw new UnsupportedOperationException("target does not support instance filters");
            }
            filters.add(JDWP.EventRequest.Set.Modifier.InstanceOnly.create((ObjectReferenceImpl) instance));
        }
    }

    class BreakpointRequestImpl extends ClassVisibleEventRequestImpl implements BreakpointRequest {
        private final Location location;

        BreakpointRequestImpl(Location location) {
            this.location = location;
            filters.addFirst(JDWP.EventRequest.Set.Modifier.LocationOnly.create(location));
            requestList().add(this);
        }

        public Location location() {
            return location;
        }

        int eventCmd() {
            return JDWP.EventKind.BREAKPOINT;
        }
    }

    class ClassPrepareRequestImpl extends ClassVisibleEventRequestImpl implements ClassPrepareRequest {
        ClassPrepareRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.CLASS_PREPARE;
        }

        public synchronized void addSourceNameFilter(String sourceNamePattern) {
            if (!vm.canUseSourceNameFilters()) {
                throw new UnsupportedOperationException("target does not support source name filters");
            }
            if (isEnabled() || deleted) {
                throw invalidState();
            }
            if (sourceNamePattern == null) {
                throw new NullPointerException();
            }
            filters.add(JDWP.EventRequest.Set.Modifier.SourceNameMatch.create(sourceNamePattern));
        }
    }

    class ClassUnloadRequestImpl extends ClassVisibleEventRequestImpl implements ClassUnloadRequest {
        ClassUnloadRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.CLASS_UNLOAD;
        }
    }

    class ExceptionRequestImpl extends ClassVisibleEventRequestImpl implements ExceptionRequest {
        final ReferenceType exception;
        final boolean caught;
        final boolean uncaught;

        ExceptionRequestImpl(ReferenceType refType, boolean notifyCaught, boolean notifyUncaught) {
            exception = refType;
            caught = notifyCaught;
            uncaught = notifyUncaught;
            {
                ReferenceTypeImpl exc;
                if (exception == null) {
                    exc = new ClassTypeImpl(vm, 0);
                } else {
                    exc = (ReferenceTypeImpl) exception;
                }
                filters.add(JDWP.EventRequest.Set.Modifier.ExceptionOnly.create(exc, caught, uncaught));
            }
            requestList().add(this);
        }

        public ReferenceType exception() {
            return exception;
        }

        public boolean notifyCaught() {
            return caught;
        }

        public boolean notifyUncaught() {
            return uncaught;
        }

        int eventCmd() {
            return JDWP.EventKind.EXCEPTION;
        }
    }

    class MethodEntryRequestImpl extends ClassVisibleEventRequestImpl implements MethodEntryRequest {
        MethodEntryRequestImpl(Method method) {
            if (method != null) {
                filters.addFirst(JDWP.EventRequest.Set.Modifier.MethodOnly.create(method));
            }
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.METHOD_ENTRY;
        }
    }

    class MethodExitRequestImpl extends ClassVisibleEventRequestImpl implements MethodExitRequest {
        MethodExitRequestImpl(Method method) {
            if (methodExitEventCmd == 0) {
                if (vm.canGetMethodReturnValues()) {
                    methodExitEventCmd = JDWP.EventKind.METHOD_EXIT_WITH_RETURN_VALUE;
                } else {
                    methodExitEventCmd = JDWP.EventKind.METHOD_EXIT;
                }
            }
            if (method != null) {
                filters.addFirst(JDWP.EventRequest.Set.Modifier.MethodOnly.create(method));
            }
            requestList().add(this);
        }

        int eventCmd() {
            return EventRequestManagerImpl.methodExitEventCmd;
        }
    }

    class MonitorContendedEnterRequestImpl extends ClassVisibleEventRequestImpl implements MonitorContendedEnterRequest {
        MonitorContendedEnterRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.MONITOR_CONTENDED_ENTER;
        }
    }

    class MonitorContendedEnteredRequestImpl extends ClassVisibleEventRequestImpl implements MonitorContendedEnteredRequest {
        MonitorContendedEnteredRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.MONITOR_CONTENDED_ENTERED;
        }
    }

    class MonitorWaitRequestImpl extends ClassVisibleEventRequestImpl implements MonitorWaitRequest {
        MonitorWaitRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.MONITOR_WAIT;
        }
    }

    class MonitorWaitedRequestImpl extends ClassVisibleEventRequestImpl implements MonitorWaitedRequest {
        MonitorWaitedRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.MONITOR_WAITED;
        }
    }

    class StepRequestImpl extends ClassVisibleEventRequestImpl implements StepRequest {
        final ThreadReferenceImpl thread;
        final int size;
        final int depth;

        StepRequestImpl(ThreadReference thread, int size, int depth) {
            this.thread = (ThreadReferenceImpl)thread;
            this.size = size;
            this.depth = depth;

            int jdwpSize = switch (size) {
                case STEP_MIN -> JDWP.StepSize.MIN;
                case STEP_LINE -> JDWP.StepSize.LINE;
                default -> throw new IllegalArgumentException("Invalid step size");
            };

            int jdwpDepth = switch (depth) {
                case STEP_INTO -> JDWP.StepDepth.INTO;
                case STEP_OVER -> JDWP.StepDepth.OVER;
                case STEP_OUT -> JDWP.StepDepth.OUT;
                default -> throw new IllegalArgumentException("Invalid step depth");
            };

            List<StepRequest> requests = stepRequests();
            for (StepRequest request : requests) {
                if (request != this && request.isEnabled() && request.thread().equals(thread)) {
                    throw new DuplicateRequestException("Only one step request allowed per thread");
                }
            }

            filters.add(JDWP.EventRequest.Set.Modifier.Step.create(this.thread, jdwpSize, jdwpDepth));
            requestList().add(this);

        }
        public int depth() {
            return depth;
        }

        public int size() {
            return size;
        }

        public ThreadReference thread() {
            return thread;
        }

        int eventCmd() {
            return JDWP.EventKind.SINGLE_STEP;
        }
    }

    class ThreadDeathRequestImpl extends ThreadLifecycleEventRequestImpl implements ThreadDeathRequest {
        ThreadDeathRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.THREAD_DEATH;
        }
    }

    class ThreadStartRequestImpl extends ThreadLifecycleEventRequestImpl implements ThreadStartRequest {
        ThreadStartRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.THREAD_START;
        }
    }

    abstract class WatchpointRequestImpl extends ClassVisibleEventRequestImpl implements WatchpointRequest {
        final Field field;

        WatchpointRequestImpl(Field field) {
            this.field = field;
            filters.addFirst(JDWP.EventRequest.Set.Modifier.FieldOnly.create((ReferenceTypeImpl) field.declaringType(), ((FieldImpl) field).ref()));
        }

        public Field field() {
            return field;
        }
    }

    class AccessWatchpointRequestImpl extends WatchpointRequestImpl implements AccessWatchpointRequest {
        AccessWatchpointRequestImpl(Field field) {
            super(field);
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.FIELD_ACCESS;
        }
    }

    class ModificationWatchpointRequestImpl extends WatchpointRequestImpl implements ModificationWatchpointRequest {
        ModificationWatchpointRequestImpl(Field field) {
            super(field);
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.FIELD_MODIFICATION;
        }
    }

    class VMDeathRequestImpl extends EventRequestImpl implements VMDeathRequest {
        VMDeathRequestImpl() {
            requestList().add(this);
        }

        int eventCmd() {
            return JDWP.EventKind.VM_DEATH;
        }
    }

    EventRequestManagerImpl(VirtualMachine vm) {
        super(vm);
        int highest = 0;
        for (java.lang.reflect.Field ekind : JDWP.EventKind.class.getDeclaredFields()) {
            int val;
            try {
                val = ekind.getInt(null);
            } catch (IllegalAccessException exc) {
                throw new RuntimeException("Got: " + exc);
            }
            if (val > highest) {
                highest = val;
            }
        }
        requestLists = new List[highest + 1];
        for (int i = 0; i <= highest; i++) {
            requestLists[i] = Collections.synchronizedList(new ArrayList<>());
        }
    }

    @Override
    public ClassPrepareRequest createClassPrepareRequest() {
        return new ClassPrepareRequestImpl();
    }

    @Override
    public ClassUnloadRequest createClassUnloadRequest() {
        return new ClassUnloadRequestImpl();
    }

    @Override
    public ExceptionRequest createExceptionRequest(ReferenceType refType, boolean notifyCaught, boolean notifyUncaught) {
        validateMirrorOrNull(refType);
        return new ExceptionRequestImpl(refType, notifyCaught, notifyUncaught);
    }

    @Override
    public StepRequest createStepRequest(ThreadReference thread, int size, int depth) {
        validateMirror(thread);
        return new StepRequestImpl(thread, size, depth);
    }

    @Override
    public ThreadDeathRequest createThreadDeathRequest() {
        return new ThreadDeathRequestImpl();
    }

    @Override
    public ThreadStartRequest createThreadStartRequest() {
        return new ThreadStartRequestImpl();
    }

    @Override
    public MethodEntryRequest createMethodEntryRequest() {
        return new MethodEntryRequestImpl(null);
    }

    @Override
    public MethodExitRequest createMethodExitRequest() {
        return new MethodExitRequestImpl(null);
    }

    // SCANNER ADDED
    @Override
    public MethodEntryRequest createMethodEntryRequest(Method method) {
        return new MethodEntryRequestImpl(method);
    }

    // SCANNER ADDED
    @Override
    public MethodExitRequest createMethodExitRequest(Method method) {
        return new MethodExitRequestImpl(method);
    }

    @Override
    public MonitorContendedEnterRequest createMonitorContendedEnterRequest() {
        if (!vm.canRequestMonitorEvents()) {
            throw new UnsupportedOperationException("target VM does not support requesting Monitor events");
        }
        return new MonitorContendedEnterRequestImpl();
    }

    @Override
    public MonitorContendedEnteredRequest createMonitorContendedEnteredRequest() {
        if (!vm.canRequestMonitorEvents()) {
            throw new UnsupportedOperationException("target VM does not support requesting Monitor events");
        }
        return new MonitorContendedEnteredRequestImpl();
    }

    @Override
    public MonitorWaitRequest createMonitorWaitRequest() {
        if (!vm.canRequestMonitorEvents()) {
            throw new UnsupportedOperationException("target VM does not support requesting Monitor events");
        }
        return new MonitorWaitRequestImpl();
    }

    @Override
    public MonitorWaitedRequest createMonitorWaitedRequest() {
        if (!vm.canRequestMonitorEvents()) {
            throw new UnsupportedOperationException("target VM does not support requesting Monitor events");
        }
        return new MonitorWaitedRequestImpl();
    }

    @Override
    public BreakpointRequest createBreakpointRequest(Location location) {
        validateMirror(location);
        if (location.codeIndex() == -1) {
            throw new NativeMethodException("Cannot set breakpoints on native methods");
        }
        return new BreakpointRequestImpl(location);
    }

    @Override
    public AccessWatchpointRequest createAccessWatchpointRequest(Field field) {
        validateMirror(field);
        if (!vm.canWatchFieldAccess()) {
            throw new UnsupportedOperationException("target VM does not support access watchpoints");
        }
        return new AccessWatchpointRequestImpl(field);
    }

    @Override
    public ModificationWatchpointRequest createModificationWatchpointRequest(Field field) {
        validateMirror(field);
        if (!vm.canWatchFieldModification()) {
            throw new UnsupportedOperationException("target VM does not support modification watchpoints");
        }
        return new ModificationWatchpointRequestImpl(field);
    }

    @Override
    public VMDeathRequest createVMDeathRequest() {
        if (!vm.canRequestVMDeathEvent()) {
            throw new UnsupportedOperationException("target VM does not support requesting VM death events");
        }
        return new VMDeathRequestImpl();
    }

    @Override
    public void deleteEventRequest(EventRequest eventRequest) {
        validateMirror(eventRequest);
        eventRequest.delete();
    }

    @Override
    public void deleteEventRequests(List<? extends EventRequest> eventRequests) {
        validateMirrors(eventRequests);
        for (EventRequest eventRequest : new ArrayList<>(eventRequests)) {
            eventRequest.delete();
        }
    }

    @Override
    public void deleteAllBreakpoints() {
        requestList(JDWP.EventKind.BREAKPOINT).clear();

        try {
            JDWP.EventRequest.ClearAllBreakpoints.process(vm);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    @Override
    public List<StepRequest> stepRequests() {
        return (List<StepRequest>) unmodifiableRequestList(JDWP.EventKind.SINGLE_STEP);
    }

    @Override
    public List<ClassPrepareRequest> classPrepareRequests() {
        return (List<ClassPrepareRequest>) unmodifiableRequestList(JDWP.EventKind.CLASS_PREPARE);
    }

    @Override
    public List<ClassUnloadRequest> classUnloadRequests() {
        return (List<ClassUnloadRequest>) unmodifiableRequestList(JDWP.EventKind.CLASS_UNLOAD);
    }

    @Override
    public List<ThreadStartRequest> threadStartRequests() {
        return (List<ThreadStartRequest>) unmodifiableRequestList(JDWP.EventKind.THREAD_START);
    }

    @Override
    public List<ThreadDeathRequest> threadDeathRequests() {
        return (List<ThreadDeathRequest>) unmodifiableRequestList(JDWP.EventKind.THREAD_DEATH);
    }

    @Override
    public List<ExceptionRequest> exceptionRequests() {
        return (List<ExceptionRequest>) unmodifiableRequestList(JDWP.EventKind.EXCEPTION);
    }

    @Override
    public List<BreakpointRequest> breakpointRequests() {
        return (List<BreakpointRequest>) unmodifiableRequestList(JDWP.EventKind.BREAKPOINT);
    }

    @Override
    public List<AccessWatchpointRequest> accessWatchpointRequests() {
        return (List<AccessWatchpointRequest>) unmodifiableRequestList(JDWP.EventKind.FIELD_ACCESS);
    }

    @Override
    public List<ModificationWatchpointRequest> modificationWatchpointRequests() {
        return (List<ModificationWatchpointRequest>) unmodifiableRequestList(JDWP.EventKind.FIELD_MODIFICATION);
    }

    @Override
    public List<MethodEntryRequest> methodEntryRequests() {
        return (List<MethodEntryRequest>) unmodifiableRequestList(JDWP.EventKind.METHOD_ENTRY);
    }

    @Override
    public List<MethodExitRequest> methodExitRequests() {
        return (List<MethodExitRequest>) unmodifiableRequestList(EventRequestManagerImpl.methodExitEventCmd);
    }

    @Override
    public List<MonitorContendedEnterRequest> monitorContendedEnterRequests() {
        return (List<MonitorContendedEnterRequest>) unmodifiableRequestList(JDWP.EventKind.MONITOR_CONTENDED_ENTER);
    }

    @Override
    public List<MonitorContendedEnteredRequest> monitorContendedEnteredRequests() {
        return (List<MonitorContendedEnteredRequest>) unmodifiableRequestList(JDWP.EventKind.MONITOR_CONTENDED_ENTERED);
    }

    @Override
    public List<MonitorWaitRequest> monitorWaitRequests() {
        return (List<MonitorWaitRequest>) unmodifiableRequestList(JDWP.EventKind.MONITOR_WAIT);
    }

    @Override
    public List<MonitorWaitedRequest> monitorWaitedRequests() {
        return (List<MonitorWaitedRequest>) unmodifiableRequestList(JDWP.EventKind.MONITOR_WAITED);
    }

    @Override
    public List<VMDeathRequest> vmDeathRequests() {
        return (List<VMDeathRequest>) unmodifiableRequestList(JDWP.EventKind.VM_DEATH);
    }

    List<? extends EventRequest> unmodifiableRequestList(int eventCmd) {
        return List.copyOf(requestList(eventCmd));
    }

    EventRequest request(int eventCmd, int requestId) {
        List<? extends EventRequest> rl = requestList(eventCmd);
        synchronized (rl) {
            for (EventRequest eventRequest : rl) {
                EventRequestImpl er = (EventRequestImpl) eventRequest;
                if (er.id == requestId) {
                    return er;
                }
            }
        }
        return null;
    }

    private List<EventRequest> requestList(int eventCmd) {
        return requestLists[eventCmd];
    }
}
