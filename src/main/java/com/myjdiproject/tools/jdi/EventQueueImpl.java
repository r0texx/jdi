/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;

import com.myjdiproject.jdi.VMDisconnectedException;
import com.myjdiproject.jdi.VirtualMachine;
import com.myjdiproject.jdi.event.EventQueue;
import com.myjdiproject.jdi.event.EventSet;

public class EventQueueImpl extends MirrorImpl implements EventQueue {
    final List<EventSet> eventSets = new LinkedList<>();
    final TargetVM target;
    boolean closed = false;

    EventQueueImpl(VirtualMachine vm, TargetVM target) {
        super(vm);
        this.target = target;
        target.addEventQueue(this);
    }

    synchronized void enqueue(EventSet eventSet) {
        eventSets.add(eventSet);
        notifyAll();
    }

    synchronized int size() {
        return eventSets.size();
    }

    synchronized void close() {
        if (!closed) {
            closed = true;
            enqueue(new EventSetImpl(vm, new EventSetImpl.VMDisconnectEventImpl(vm)));
        }
    }

    public EventSet remove() throws InterruptedException {
        return remove(0);
    }

    public EventSet remove(long timeout) throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }

        EventSet eventSet;
        while (true) {
            EventSetImpl fullEventSet = removeUnfiltered(timeout);
            if (fullEventSet == null) {
                eventSet = null;  // timeout
                break;
            }

            eventSet = fullEventSet.userFilter();
            if (!eventSet.isEmpty()) {
                break;
            }
        }

        if ((eventSet != null) && (eventSet.suspendPolicy() == JDWP.SuspendPolicy.ALL)) {
            vm.notifySuspend();
        }

        return eventSet;
    }

    EventSet removeInternal() throws InterruptedException {
        EventSet eventSet;
        do {
            eventSet = removeUnfiltered(0).internalFilter();
        } while (eventSet == null || eventSet.isEmpty());
        return eventSet;
    }

    private TimerThread startTimerThread(long timeout) {
        TimerThread thread = new TimerThread(timeout);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private boolean shouldWait(TimerThread timerThread) {
        return !closed && eventSets.isEmpty() && (timerThread == null || !timerThread.timedOut());
    }

    private EventSetImpl removeUnfiltered(long timeout) throws InterruptedException {
        EventSetImpl eventSet = null;

        vm.waitInitCompletion();

        synchronized (this) {
            if (!eventSets.isEmpty()) {
                eventSet = (EventSetImpl) eventSets.removeFirst();
            } else {
                TimerThread timerThread = null;
                try {
                    if (timeout > 0) {
                        timerThread = startTimerThread(timeout);
                    }

                    while (shouldWait(timerThread)) {
                        this.wait();
                    }
                } finally {
                    if ((timerThread != null) && !timerThread.timedOut()) {
                        timerThread.interrupt();
                    }
                }

                if (eventSets.isEmpty()) {
                    if (closed) {
                        throw new VMDisconnectedException();
                    }
                } else {
                    eventSet = (EventSetImpl) eventSets.removeFirst();
                }
            }
        }

        if (eventSet != null) {
            target.notifyDequeueEventSet();
            eventSet.build();
        }
        return eventSet;
    }

    private class TimerThread extends Thread {
        private boolean timedOut = false;
        private final long timeout;

        TimerThread(long timeout) {
            super(vm.threadGroupForJDI(), "JDI Event Queue Timer");
            this.timeout = timeout;
        }

        boolean timedOut() {
            return timedOut;
        }

        public void run() {
            try {
                Thread.sleep(timeout);
                EventQueueImpl queue = EventQueueImpl.this;
                synchronized (queue) {
                    timedOut = true;
                    queue.notifyAll();
                }
            } catch (InterruptedException e) {
            }
        }
    }
}
