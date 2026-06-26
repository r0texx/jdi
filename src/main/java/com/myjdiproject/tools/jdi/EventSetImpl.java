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

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;

import com.myjdiproject.jdi.Field;
import com.myjdiproject.jdi.InternalException;
import com.myjdiproject.jdi.Locatable;
import com.myjdiproject.jdi.Location;
import com.myjdiproject.jdi.Method;
import com.myjdiproject.jdi.ObjectReference;
import com.myjdiproject.jdi.ReferenceType;
import com.myjdiproject.jdi.ThreadReference;
import com.myjdiproject.jdi.Value;
import com.myjdiproject.jdi.VirtualMachine;
import com.myjdiproject.jdi.event.AccessWatchpointEvent;
import com.myjdiproject.jdi.event.BreakpointEvent;
import com.myjdiproject.jdi.event.ClassPrepareEvent;
import com.myjdiproject.jdi.event.ClassUnloadEvent;
import com.myjdiproject.jdi.event.Event;
import com.myjdiproject.jdi.event.EventIterator;
import com.myjdiproject.jdi.event.EventSet;
import com.myjdiproject.jdi.event.ExceptionEvent;
import com.myjdiproject.jdi.event.IgnoredClassesEvent;
import com.myjdiproject.jdi.event.MethodEntryEvent;
import com.myjdiproject.jdi.event.MethodExitEvent;
import com.myjdiproject.jdi.event.ModificationWatchpointEvent;
import com.myjdiproject.jdi.event.MonitorContendedEnterEvent;
import com.myjdiproject.jdi.event.MonitorContendedEnteredEvent;
import com.myjdiproject.jdi.event.MonitorWaitEvent;
import com.myjdiproject.jdi.event.MonitorWaitedEvent;
import com.myjdiproject.jdi.event.StepEvent;
import com.myjdiproject.jdi.event.ThreadDeathEvent;
import com.myjdiproject.jdi.event.ThreadStartEvent;
import com.myjdiproject.jdi.event.VMDeathEvent;
import com.myjdiproject.jdi.event.VMDisconnectEvent;
import com.myjdiproject.jdi.event.VMStartEvent;
import com.myjdiproject.jdi.event.WatchpointEvent;
import com.myjdiproject.jdi.request.EventRequest;

enum EventDestination {
    UNKNOWN_EVENT,
    INTERNAL_EVENT,
    CLIENT_EVENT
}

public class EventSetImpl extends ArrayList<Event> implements EventSet {
    @Serial
    private static final long serialVersionUID = -4857338819787924570L;

    private final VirtualMachineImpl vm;
    private Packet pkt;
    private byte suspendPolicy;
    private EventSetImpl internalEventSet;

    public String toString() {
        StringBuilder sb = new StringBuilder("event set, policy:" + suspendPolicy + ", count:" + size() + " = {");
        boolean first = true;
        for (Event event : this) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append(event.toString());
        }
        sb.append("}");
        return sb.toString();
    }

    abstract static class EventImpl extends MirrorImpl implements Event {
        private final int requestID;
        private final EventRequest request;

        protected EventImpl(VirtualMachineImpl vm, int requestID) {
            super(vm);
            this.requestID = requestID;
            this.request =  vm.eventRequestManagerImpl().request(eventKind(), requestID);
        }

        protected EventImpl(VirtualMachineImpl vm) {
            super(vm);
            this.requestID = 0;
            this.request = null;
        }

        public boolean equals(Object obj) {
            return this == obj;
        }

        public int hashCode() {
            return System.identityHashCode(this);
        }

        public EventRequest request() {
            return request;
        }

        int requestID() {
            return requestID;
        }

        EventDestination destination() {
            if (requestID == 0) {
                return EventDestination.CLIENT_EVENT;
            }

            if (request == null) {
                return EventDestination.INTERNAL_EVENT;
            }

            if (request.isEnabled()) {
                return EventDestination.CLIENT_EVENT;
            }
            return EventDestination.UNKNOWN_EVENT;
        }

        abstract byte eventKind();

        abstract String eventName();

        public String toString() {
            return eventName();
        }
    }

    abstract static class ThreadedEventImpl extends EventImpl {
        private final ThreadReference thread;

        ThreadedEventImpl(VirtualMachineImpl vm, int requestID, ThreadReference thread) {
            super(vm, requestID);
            this.thread = thread;
        }

        public ThreadReference thread() {
            return thread;
        }

        public String toString() {
            return eventName() + " in thread " + thread.name();
        }
    }

    abstract static class LocatableEventImpl extends ThreadedEventImpl implements Locatable {
        private final Location location;

        LocatableEventImpl(VirtualMachineImpl vm, int requestID, ThreadReference thread, Location location) {
            super(vm, requestID, thread);
            this.location = location;
        }

        public Location location() {
            return location;
        }

        public Method method() {
            return location.method();
        }

        public String toString() {
            return eventName() + "@" + location() + " in thread " + thread().name();
        }
    }

    public static class BreakpointEventImpl extends LocatableEventImpl implements BreakpointEvent {
        BreakpointEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, Location location) {
            super(vm, requestID, thread, location);
        }

        public BreakpointEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readLocation());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.BREAKPOINT;
        }

        @Override
        String eventName() {
            return "BreakpointEvent";
        }
    }

    static class StepEventImpl extends LocatableEventImpl implements StepEvent {
        StepEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, Location location) {
            super(vm, requestID, thread, location);
        }

        StepEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readLocation());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.SINGLE_STEP;
        }

        @Override
        String eventName() {
            return "StepEvent";
        }
    }

    static class MethodEntryEventImpl extends LocatableEventImpl implements MethodEntryEvent {
        MethodEntryEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, Location location) {
            super(vm, requestID, thread, location);
        }

        MethodEntryEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readLocation());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.METHOD_ENTRY;
        }

        @Override
        String eventName() {
            return "MethodEntryEvent";
        }
    }

    static class MethodExitEventImpl extends LocatableEventImpl implements MethodExitEvent {
        private final Value returnVal;

        MethodExitEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, Location location, ValueImpl value) {
            super(vm, requestID, thread, location);
            returnVal = value;
        }

        MethodExitEventImpl(PacketStream ps, VirtualMachineImpl vm, boolean withReturnValue) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readLocation(), withReturnValue ? ps.readValue() : null);
        }

        @Override
        public Value returnValue() {
            return returnVal;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.METHOD_EXIT_WITH_RETURN_VALUE;
        }

        @Override
        String eventName() {
            return "MethodExitEvent";
        }
    }

    static class MonitorContendedEnterEventImpl extends LocatableEventImpl implements MonitorContendedEnterEvent {
        private final ObjectReference monitor;

        MonitorContendedEnterEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, ObjectReferenceImpl object, Location location) {
            super(vm, requestID, thread, location);
            this.monitor = object;
        }

        MonitorContendedEnterEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readTaggedObjectReference(), ps.readLocation());
        }

        public ObjectReference monitor() {
            return monitor;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.MONITOR_CONTENDED_ENTER;
        }

        @Override
        String eventName() {
            return "MonitorContendedEnter";
        }
    }

    static class MonitorContendedEnteredEventImpl extends LocatableEventImpl implements MonitorContendedEnteredEvent {
        private final ObjectReference monitor;

        MonitorContendedEnteredEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread,
                                         ObjectReferenceImpl object, Location location) {

            super(vm, requestID, thread, location);
            this.monitor = object;
        }

        MonitorContendedEnteredEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readTaggedObjectReference(), ps.readLocation());
        }

        public ObjectReference  monitor() {
            return monitor;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.MONITOR_CONTENDED_ENTERED;
        }

        @Override
        String eventName() {
            return "MonitorContendedEntered";
        }
    }

    static class MonitorWaitEventImpl extends LocatableEventImpl implements MonitorWaitEvent {
        private final ObjectReference monitor;
        private final long timeout;

        MonitorWaitEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, ObjectReferenceImpl object,
                             Location location, long timeout) {

            super(vm, requestID, thread, location);
            this.monitor = object;
            this.timeout = timeout;
        }

        MonitorWaitEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readTaggedObjectReference(), ps.readLocation(), ps.readLong());
        }

        public ObjectReference  monitor() {
            return monitor;
        }

        public long timeout() {
            return timeout;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.MONITOR_WAIT;
        }

        @Override
        String eventName() {
            return "MonitorWait";
        }
    }

    static class MonitorWaitedEventImpl extends LocatableEventImpl implements MonitorWaitedEvent {
        private final ObjectReference monitor;
        private final boolean timedOut;

        MonitorWaitedEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, ObjectReferenceImpl object,
                               Location location, boolean timedOut) {

            super(vm, requestID, thread, location);
            this.monitor = object;
            this.timedOut = timedOut;
        }

        MonitorWaitedEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readTaggedObjectReference(), ps.readLocation(), ps.readBoolean());
        }

        public ObjectReference  monitor() {
            return monitor;
        }

        public boolean timedOut() {
            return timedOut;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.MONITOR_WAITED;
        }

        @Override
        String eventName() {
            return "MonitorWaited";
        }
    }

    static class ClassPrepareEventImpl extends ThreadedEventImpl implements ClassPrepareEvent {
        private final ReferenceTypeImpl referenceType;

        ClassPrepareEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, byte refTypeTag,
                              long typeID, String signature, int status) {

            super(vm, requestID, thread);
            referenceType = vm.referenceType(typeID, refTypeTag, signature);
            referenceType.setStatus(status);
        }

        ClassPrepareEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readByte(), ps.readClassRef(), ps.readString(), ps.readInt());
        }

        public ReferenceType referenceType() {
            return referenceType;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.CLASS_PREPARE;
        }

        @Override
        String eventName() {
            return "ClassPrepareEvent";
        }
    }

    static class ClassUnloadEventImpl extends EventImpl implements ClassUnloadEvent {
        private final String classSignature;

        ClassUnloadEventImpl(VirtualMachineImpl vm, int requestID, String signature) {
            super(vm, requestID);
            this.classSignature = signature;
        }

        ClassUnloadEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readString());
        }

        public String className() {
            return JNITypeParser.convertSignatureToClassname(classSignature);
        }

        public String classSignature() {
            return classSignature;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.CLASS_UNLOAD;
        }

        @Override
        String eventName() {
            return "ClassUnloadEvent";
        }
    }

    static class ExceptionEventImpl extends LocatableEventImpl implements ExceptionEvent {
        private final ObjectReference exception;
        private final Location catchLocation;

        ExceptionEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, Location location,
                           ObjectReferenceImpl exception, Location catchLocation) {

            super(vm, requestID, thread, location);
            this.exception = exception;
            this.catchLocation = catchLocation;
        }

        ExceptionEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readLocation(), ps.readTaggedObjectReference(), ps.readLocation());
        }

        public ObjectReference exception() {
            return exception;
        }

        public Location catchLocation() {
            return catchLocation;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.EXCEPTION;
        }

        @Override
        String eventName() {
            return "ExceptionEvent";
        }
    }

    static class ThreadDeathEventImpl extends ThreadedEventImpl implements ThreadDeathEvent {
        ThreadDeathEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread) {
            super(vm, requestID, thread);
        }

        ThreadDeathEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.THREAD_DEATH;
        }

        @Override
        String eventName() {
            return "ThreadDeathEvent";
        }
    }

    static class ThreadStartEventImpl extends ThreadedEventImpl implements ThreadStartEvent {
        ThreadStartEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread) {
            super(vm, requestID, thread);
        }

        ThreadStartEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.THREAD_START;
        }

        @Override
        String eventName() {
            return "ThreadStartEvent";
        }
    }

    static class VMStartEventImpl extends ThreadedEventImpl implements VMStartEvent {
        VMStartEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread) {
            super(vm, requestID, thread);
        }

        VMStartEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.VM_START;
        }

        @Override
        String eventName() {
            return "VMStartEvent";
        }
    }

    static class VMDeathEventImpl extends EventImpl implements VMDeathEvent {
        VMDeathEventImpl(VirtualMachineImpl vm, int requestID) {
            super(vm, requestID);
        }

        VMDeathEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.VM_DEATH;
        }

        @Override
        String eventName() {
            return "VMDeathEvent";
        }
    }

    // SCANNER ADDED
    static class IgnoredClassesEventImpl extends EventImpl implements IgnoredClassesEvent {
        private final List<String> classNames;

        IgnoredClassesEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            super(vm);
            ps.readInt(); // requestID, always 0 (unsolicited client event)
            int count = ps.readInt();
            List<String> names = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                names.add(ps.readString());
            }
            this.classNames = names;
        }

        public List<String> classNames() {
            return classNames;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.IGNORED_CLASSES;
        }

        @Override
        String eventName() {
            return "IgnoredClassesEvent";
        }
    }

    static class VMDisconnectEventImpl extends EventImpl implements VMDisconnectEvent {
        VMDisconnectEventImpl(VirtualMachineImpl vm) {
            super(vm);
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.VM_DISCONNECTED;
        }

        @Override
        String eventName() {
            return "VMDisconnectEvent";
        }
    }

    static abstract class WatchpointEventImpl extends LocatableEventImpl implements WatchpointEvent {
        private final ReferenceTypeImpl refType;
        private final long fieldID;
        private final ObjectReference object;
        private Field field = null;

        WatchpointEventImpl(VirtualMachineImpl vm, int requestID, ThreadReference thread, Location location,
                            byte refTypeTag, long typeID, long fieldID, ObjectReference object) {

            super(vm, requestID, thread, location);
            this.refType = vm.referenceType(typeID, refTypeTag);
            this.fieldID = fieldID;
            this.object = object;
        }

        public ReferenceType referenceType() {
            return refType;
        }

        public Field field() {
            if (field == null) {
                field = refType.getFieldMirror(fieldID);
            }
            return field;
        }

        public ObjectReference object() {
            return object;
        }

        public Value valueCurrent() {
            if (object == null) {
                return refType.getValue(field());
            } else {
                return object.getValue(field());
            }
        }
    }

    static class AccessWatchpointEventImpl extends WatchpointEventImpl implements AccessWatchpointEvent {
        AccessWatchpointEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, Location location,
                                  byte refTypeTag, long typeID, long fieldID, ObjectReferenceImpl object) {

            super(vm, requestID, thread, location, refTypeTag, typeID, fieldID, object);
        }

        AccessWatchpointEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readLocation(), ps.readByte(),
                    ps.readClassRef(), ps.readFieldRef(), ps.readTaggedObjectReference());
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.FIELD_ACCESS;
        }

        @Override
        String eventName() {
            return "AccessWatchpoint";
        }
    }

    static class ModificationWatchpointEventImpl extends WatchpointEventImpl implements ModificationWatchpointEvent {
        final Value newValue;

        ModificationWatchpointEventImpl(VirtualMachineImpl vm, int requestID, ThreadReferenceImpl thread, Location location,
                                        byte refTypeTag, long typeID, long fieldID, ObjectReferenceImpl object, ValueImpl valueToBe) {

            super(vm, requestID, thread, location, refTypeTag, typeID, fieldID, object);
            this.newValue = valueToBe;
        }

        ModificationWatchpointEventImpl(PacketStream ps, VirtualMachineImpl vm) {
            this(vm, ps.readInt(), ps.readThreadReference(), ps.readLocation(), ps.readByte(), ps.readClassRef(),
                    ps.readFieldRef(), ps.readTaggedObjectReference(), ps.readValue());
        }

        public Value valueToBe() {
            return newValue;
        }

        @Override
        byte eventKind() {
            return JDWP.EventKind.FIELD_MODIFICATION;
        }

        @Override
        String eventName() {
            return "ModificationWatchpoint";
        }
    }

    EventSetImpl(VirtualMachine aVm, Packet pkt) {
        super();
        vm = (VirtualMachineImpl) aVm;
        this.pkt = pkt;
    }

    EventSetImpl(VirtualMachine aVm, VMDisconnectEventImpl event) {
        this(aVm, (Packet) null);
        suspendPolicy = JDWP.SuspendPolicy.NONE;
        addEvent(event);
    }

    private void addEvent(EventImpl evt) {
        super.add(evt);
    }

    synchronized void build() {
        if (pkt == null) {
            return;
        }
        PacketStream ps = new PacketStream(vm, pkt);
        JDWP.Event.Composite compEvt = new JDWP.Event.Composite(ps, vm);
        suspendPolicy = compEvt.suspendPolicy;

        ThreadReference fix6485605 = null;
        for (EventImpl event : compEvt.events) {
            switch (event.destination()) {
                case UNKNOWN_EVENT:
                    if (event instanceof ThreadedEventImpl && suspendPolicy == JDWP.SuspendPolicy.EVENT_THREAD) {
                        fix6485605 = ((ThreadedEventImpl) event).thread();
                    }
                    continue;
                case CLIENT_EVENT:
                    addEvent(event);
                    break;
                case INTERNAL_EVENT:
                    if (internalEventSet == null) {
                        internalEventSet = new EventSetImpl(this.vm, (Packet) null);
                    }
                    internalEventSet.addEvent(event);
                    break;
                default:
                    throw new InternalException("Invalid event destination");
            }
        }
        pkt = null; // No longer needed - free it up

        if (super.isEmpty()) {
            if (suspendPolicy == JDWP.SuspendPolicy.ALL) {
                vm.resume();
            } else if (suspendPolicy == JDWP.SuspendPolicy.EVENT_THREAD) {
                if (fix6485605 != null) {
                    fix6485605.resume();
                }
            }
            suspendPolicy = JDWP.SuspendPolicy.NONE;
        }

    }

    EventSet userFilter() {
        return this;
    }

    EventSet internalFilter() {
        return internalEventSet;
    }

    public VirtualMachine virtualMachine() {
        return vm;
    }

    public int suspendPolicy() {
        return EventRequestManagerImpl.JDWPtoJDISuspendPolicy(suspendPolicy);
    }

    private ThreadReference eventThread() {
        for (Event event : this) {
            if (event instanceof ThreadedEventImpl) {
                return ((ThreadedEventImpl) event).thread();
            }
        }
        return null;
    }

    public void resume() {
        switch (suspendPolicy()) {
            case EventRequest.SUSPEND_ALL:
                vm.resume();
                break;
            case EventRequest.SUSPEND_EVENT_THREAD:
                ThreadReference thread = eventThread();
                if (thread == null) {
                    throw new InternalException("Inconsistent suspend policy");
                }
                thread.resume();
                break;
            case EventRequest.SUSPEND_NONE:
                break;
            default:
                throw new InternalException("Invalid suspend policy");
        }
    }

    public Iterator<Event> iterator() {
        return new Itr();
    }

    public EventIterator eventIterator() {
        return new Itr();
    }

    public class Itr implements EventIterator {
        int cursor = 0;

        public boolean hasNext() {
            return cursor != size();
        }

        public Event next() {
            try {
                Event nxt = get(cursor);
                ++cursor;
                return nxt;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }

        public Event nextEvent() {
            return next();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Spliterator<Event> spliterator() {
        return Spliterators.spliterator(this, Spliterator.DISTINCT);
    }

    public boolean add(Event o){
        throw new UnsupportedOperationException();
    }
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }
    public boolean addAll(Collection<? extends Event> coll) {
        throw new UnsupportedOperationException();
    }
    public boolean removeAll(Collection<?> coll) {
        throw new UnsupportedOperationException();
    }
    public boolean retainAll(Collection<?> coll) {
        throw new UnsupportedOperationException();
    }
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
