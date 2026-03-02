/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

import com.myjdiproject.jdi.ThreadGroupReference;
import com.myjdiproject.jdi.ThreadReference;

class VMState {
    private final VirtualMachineImpl vm;
    private final List<WeakReference<VMListener>> listeners = new ArrayList<>(); // synchronized (this)
    private boolean notifyingListeners = false;
    private final Set<Integer> pendingResumeCommands = Collections.synchronizedSet(new HashSet<>());

    private static class Cache {
        List<ThreadGroupReference> groups = null;
        List<ThreadReference> threads = null;
    }

    private Cache cache = null;
    private static final Cache markerCache = new Cache();

    private void disableCache() {
        synchronized (this) {
            cache = null;
        }
    }

    private void enableCache() {
        synchronized (this) {
            cache = markerCache;
        }
    }

    private Cache getCache() {
        synchronized (this) {
            if (cache == markerCache) {
                cache = new Cache();
            }
            return cache;
        }
    }

    VMState(VirtualMachineImpl vm) {
        this.vm = vm;
    }

    boolean isSuspended() {
        return cache != null;
    }

    void notifyCommandComplete(int id) {
        pendingResumeCommands.remove(id);
    }

    synchronized void freeze() {
        if (cache == null && (pendingResumeCommands.isEmpty())) {
            processVMAction(new VMAction(vm, VMAction.VM_SUSPENDED));
            enableCache();
        }
    }

    synchronized PacketStream thawCommand(CommandSender sender) {
        PacketStream stream = sender.send();
        pendingResumeCommands.add(stream.id());
        thaw();
        return stream;
    }

    void thaw() {
        thaw(null);
    }

    synchronized void thaw(ThreadReference resumingThread) {
        if (cache != null) {
            disableCache();
        }
        processVMAction(new VMAction(vm, resumingThread, VMAction.VM_NOT_SUSPENDED));
    }

    private synchronized void processVMAction(VMAction action) {
        if (!notifyingListeners) {
            // Prevent recursion
            notifyingListeners = true;

            Iterator<WeakReference<VMListener>> iter = listeners.iterator();
            while (iter.hasNext()) {
                WeakReference<VMListener> ref = iter.next();
                VMListener listener = ref.get();
                if (listener != null) {
                    boolean keep = switch (action.id()) {
                        case VMAction.VM_SUSPENDED -> listener.vmSuspended(action);
                        case VMAction.VM_NOT_SUSPENDED -> listener.vmNotSuspended(action);
                        default -> true;
                    };
                    if (!keep) {
                        iter.remove();
                    }
                } else {
                    iter.remove();
                }
            }

            notifyingListeners = false;
        }
    }

    private final ReferenceQueue<VMListener> listenersReferenceQueue = new ReferenceQueue<>();

    private void removeUnreachableListeners() {
        if (listenersReferenceQueue.poll() == null) {
            return;
        }

        while (listenersReferenceQueue.poll() != null) {
            // read all
        }

        Iterator<WeakReference<VMListener>> iter = listeners.iterator();
        while (iter.hasNext()) {
            VMListener l = iter.next().get();
            if (l == null) {
                iter.remove();
            }
        }
    }

    synchronized void addListener(VMListener listener) {
        removeUnreachableListeners();
        listeners.add(new WeakReference<>(listener, listenersReferenceQueue));
    }

    synchronized boolean hasListener(VMListener listener) {
        for (WeakReference<VMListener> ref : listeners) {
            if (listener.equals(ref.get())) {
                return true;
            }
        }
        return false;
    }

    List<ThreadReference> allThreads() {
        List<ThreadReference> threads = null;
        try {
            Cache local = getCache();

            if (local != null) {
                threads = local.threads;
            }
            if (threads == null) {
                threads = JDWP.VirtualMachine.AllThreads.process(vm).threads;
                if (local != null) {
                    local.threads = threads;
                }
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return threads;
    }


    List<ThreadGroupReference> topLevelThreadGroups() {
        List<ThreadGroupReference> groups = null;
        try {
            Cache local = getCache();
            if (local != null) {
                groups = local.groups;
            }
            if (groups == null) {
                groups = JDWP.VirtualMachine.TopLevelThreadGroups.process(vm).groups;
                if (local != null) {
                    local.groups = groups;
                }
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        return groups;
    }
}
