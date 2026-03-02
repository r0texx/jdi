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

package com.myjdiproject.jdi;

import java.util.List;

public interface ThreadReference extends ObjectReference {
    int THREAD_STATUS_UNKNOWN  =-1;
    int THREAD_STATUS_ZOMBIE = 0;
    int THREAD_STATUS_RUNNING = 1;
    int THREAD_STATUS_SLEEPING = 2;
    int THREAD_STATUS_MONITOR = 3;
    int THREAD_STATUS_WAIT = 4;
    int THREAD_STATUS_NOT_STARTED = 5;

    String name();
    void suspend();
    void resume();
    int suspendCount();
    void stop(ObjectReference throwable) throws InvalidTypeException;
    void interrupt();
    int status();
    boolean isSuspended();
    boolean isAtBreakpoint();
    ThreadGroupReference threadGroup();
    int frameCount() throws IncompatibleThreadStateException;
    List<StackFrame> frames() throws IncompatibleThreadStateException;
    StackFrame frame(int index) throws IncompatibleThreadStateException;
    List<StackFrame> frames(int start, int length) throws IncompatibleThreadStateException;
    List<ObjectReference> ownedMonitors() throws IncompatibleThreadStateException;
    List<MonitorInfo> ownedMonitorsAndFrames() throws IncompatibleThreadStateException;
    ObjectReference currentContendedMonitor() throws IncompatibleThreadStateException;
    void popFrames(StackFrame frame) throws IncompatibleThreadStateException;
    void forceEarlyReturn(Value value) throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException;
    default boolean isVirtual() {
        throw new UnsupportedOperationException("Method not implemented");
    }
}
