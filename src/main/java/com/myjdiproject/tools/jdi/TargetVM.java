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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.myjdiproject.jdi.VMDisconnectedException;
import com.myjdiproject.jdi.connect.spi.Connection;
import com.myjdiproject.jdi.event.EventQueue;
import com.myjdiproject.jdi.event.EventSet;

public class TargetVM implements Runnable {
    private final Map<String, Packet> waitingQueue = new HashMap<>(32,0.75f);
    private volatile boolean shouldListen = true;
    private final List<EventQueue> eventQueues = Collections.synchronizedList(new ArrayList<>(2));
    private final VirtualMachineImpl vm;
    private final Connection connection;
    private final Thread readerThread;
    private EventController eventController = null;
    private boolean eventsHeld = false;

    private static final int OVERLOADED_QUEUE = 10000;
    private static final int UNDERLOADED_QUEUE = 100;

    TargetVM(VirtualMachineImpl vm, Connection connection) {
        this.vm = vm;
        this.connection = connection;
        this.readerThread = new Thread(vm.threadGroupForJDI(), this, "JDI Target VM Interface");
        this.readerThread.setDaemon(true);
    }

    void start() {
        readerThread.start();
    }

    public void run() {
        try {
            Packet p = null, p2;
            String idString;

            while (shouldListen) {
                boolean done = false;
                try {
                    byte[] b = connection.readPacket();
                    if (b.length == 0) {
                        done = true;
                    }
                    p = Packet.fromByteArray(b);
                } catch (IOException e) {
                    done = true;
                }

                if (done) {
                    shouldListen = false;
                    try {
                        connection.close();
                    } catch (IOException ioe) {
                    }
                    break;
                }

                if ((p.flags & Packet.Reply) == 0) {
                    handleVMCommand(p);
                } else {
                    vm.state().notifyCommandComplete(p.id);
                    idString = String.valueOf(p.id);

                    synchronized (waitingQueue) {
                        p2 = waitingQueue.get(idString);

                        if (p2 != null) {
                            waitingQueue.remove(idString);
                        }
                    }

                    if (p2 == null) {
                        System.err.println("Received reply with no sender!");
                        continue;
                    }
                    p2.errorCode = p.errorCode;
                    p2.data = p.data;
                    p2.replied = true;

                    synchronized (p2) {
                        p2.notify();
                    }
                }
            }

            vm.vmManager.disposeVirtualMachine(vm);
            if (eventController != null) {
                eventController.release();
            }

            synchronized (eventQueues) {
                for (EventQueue eventQueue : eventQueues) {
                    ((EventQueueImpl) eventQueue).close();
                }
            }

            synchronized (waitingQueue) {
                for (Packet packet : waitingQueue.values()) {
                    synchronized (packet) {
                        packet.notify();
                    }
                }
                waitingQueue.clear();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    protected void handleVMCommand(Packet p) {
        switch (p.cmdSet) {
            case JDWP.Event.COMMAND_SET:
                handleEventCmdSet(p);
                break;
            default:
                System.err.println("Ignoring cmd " + p.id + "/" + p.cmdSet + "/" + p.cmd + " from the VM");
        }
    }

    protected void handleEventCmdSet(Packet p) {
        EventSet eventSet = new EventSetImpl(vm, p);
        queueEventSet(eventSet);
    }

    private EventController eventController() {
        if (eventController == null) {
            eventController = new EventController();
        }
        return eventController;
    }

    private synchronized void controlEventFlow(int maxQueueSize) {
        if (!eventsHeld && (maxQueueSize > OVERLOADED_QUEUE)) {
            eventController().hold();
            eventsHeld = true;
        } else if (eventsHeld && (maxQueueSize < UNDERLOADED_QUEUE)) {
            eventController().release();
            eventsHeld = false;
        }
    }

    void notifyDequeueEventSet() {
        int maxQueueSize = 0;
        synchronized(eventQueues) {
            for (EventQueue eventQueue : eventQueues) {
                EventQueueImpl queue = (EventQueueImpl)eventQueue;
                maxQueueSize = Math.max(maxQueueSize, queue.size());
            }
        }
        controlEventFlow(maxQueueSize);
    }

    private void queueEventSet(EventSet eventSet) {
        int maxQueueSize = 0;

        synchronized(eventQueues) {
            for (EventQueue eventQueue : eventQueues) {
                EventQueueImpl queue = (EventQueueImpl)eventQueue;
                queue.enqueue(eventSet);
                maxQueueSize = Math.max(maxQueueSize, queue.size());
            }
        }

        controlEventFlow(maxQueueSize);
    }

    void send(Packet packet) {
        String id = String.valueOf(packet.id);

        synchronized(waitingQueue) {
            waitingQueue.put(id, packet);
        }

        try {
            connection.writePacket(packet.toByteArray());
        } catch (IOException e) {
            throw new VMDisconnectedException(e.getMessage());
        }
    }

    void waitForReply(Packet packet) {
        synchronized (packet) {
            while ((!packet.replied) && shouldListen) {
                try {
                    packet.wait();
                } catch (InterruptedException e) {
                }
            }

            if (!packet.replied) {
                throw new VMDisconnectedException();
            }
        }
    }

    void addEventQueue(EventQueueImpl queue) {
        eventQueues.add(queue);
    }

    void stopListening() {
        shouldListen = false;
        try {
            connection.close();
        } catch (IOException ioe) {
        }
    }

    private class EventController extends Thread {
        int controlRequest = 0;

        EventController() {
            super(vm.threadGroupForJDI(), "JDI Event Control Thread");
            setDaemon(true);
            setPriority((MAX_PRIORITY + NORM_PRIORITY)/2);
            super.start();
        }

        synchronized void hold() {
            controlRequest++;
            notifyAll();
        }

        synchronized void release() {
            controlRequest--;
            notifyAll();
        }

        public void run() {
            while(true) {
                int currentRequest;
                synchronized(this) {
                    while (controlRequest == 0) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                        }
                        if (!shouldListen) {
                           return;
                        }
                    }
                    currentRequest = controlRequest;
                    controlRequest = 0;
                }
                try {
                    if (currentRequest > 0) {
                        JDWP.VirtualMachine.HoldEvents.process(vm);
                    } else {
                        JDWP.VirtualMachine.ReleaseEvents.process(vm);
                    }
                } catch (JDWPException e) {
                    e.toJDIException().printStackTrace(System.err);
                }
            }
        }
    }
}
