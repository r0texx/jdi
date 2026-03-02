/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.myjdiproject.jdi.request;

import java.util.List;

import com.myjdiproject.jdi.*;

public interface EventRequestManager extends Mirror {
    ClassPrepareRequest createClassPrepareRequest();
    ClassUnloadRequest createClassUnloadRequest();
    ThreadStartRequest createThreadStartRequest();
    ThreadDeathRequest createThreadDeathRequest();
    ExceptionRequest createExceptionRequest(ReferenceType refType, boolean notifyCaught, boolean notifyUncaught);
    MethodEntryRequest createMethodEntryRequest();
    MethodExitRequest createMethodExitRequest();
    MonitorContendedEnterRequest createMonitorContendedEnterRequest();
    MonitorContendedEnteredRequest createMonitorContendedEnteredRequest();
    MonitorWaitRequest createMonitorWaitRequest();
    MonitorWaitedRequest createMonitorWaitedRequest();
    StepRequest createStepRequest(ThreadReference thread, int size, int depth);
    BreakpointRequest createBreakpointRequest(Location location);
    AccessWatchpointRequest createAccessWatchpointRequest(Field field);
    ModificationWatchpointRequest createModificationWatchpointRequest(Field field);
    VMDeathRequest createVMDeathRequest();
    void deleteEventRequest(EventRequest eventRequest);
    void deleteEventRequests(List<? extends EventRequest> eventRequests);
    void deleteAllBreakpoints();
    List<StepRequest> stepRequests();
    List<ClassPrepareRequest> classPrepareRequests();
    List<ClassUnloadRequest> classUnloadRequests();
    List<ThreadStartRequest> threadStartRequests();
    List<ThreadDeathRequest> threadDeathRequests();
    List<ExceptionRequest> exceptionRequests();
    List<BreakpointRequest> breakpointRequests();
    List<AccessWatchpointRequest> accessWatchpointRequests();
    List<ModificationWatchpointRequest> modificationWatchpointRequests();
    List<MethodEntryRequest> methodEntryRequests();
    List<MethodExitRequest> methodExitRequests();
    List<MonitorContendedEnterRequest> monitorContendedEnterRequests();
    List<MonitorContendedEnteredRequest> monitorContendedEnteredRequests();
    List<MonitorWaitRequest> monitorWaitRequests();
    List<MonitorWaitedRequest> monitorWaitedRequests();
    List<VMDeathRequest> vmDeathRequests();

    // SCANNER ADDED
    MethodEntryRequest createMethodEntryRequest(Method method);
    MethodExitRequest createMethodExitRequest(Method method);
}
