/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import com.myjdiproject.jdi.event.EventQueue;
import com.myjdiproject.jdi.request.EventRequestManager;

public interface VirtualMachine extends Mirror {
    default List<ModuleReference> allModules() {
        throw new java.lang.UnsupportedOperationException("The method allModules() must be implemented");
    }

    List<ReferenceType> classesByName(String className);
    List<ReferenceType> allClasses();
    void redefineClasses(Map<? extends ReferenceType,byte[]> classToBytes);
    List<ThreadReference> allThreads();
    void suspend();
    void resume();

    void loadRuleIndex(RuleIndexData index);

    List<ThreadGroupReference> topLevelThreadGroups();
    EventQueue eventQueue();
    EventRequestManager eventRequestManager();
    BooleanValue mirrorOf(boolean value);
    ByteValue mirrorOf(byte value);
    CharValue mirrorOf(char value);
    ShortValue mirrorOf(short value);
    IntegerValue mirrorOf(int value);
    LongValue mirrorOf(long value);
    FloatValue mirrorOf(float value);
    DoubleValue mirrorOf(double value);
    StringReference mirrorOf(String value);
    VoidValue mirrorOfVoid();
    Process process();
    void dispose();
    void exit(int exitCode);
    boolean canWatchFieldModification();
    boolean canWatchFieldAccess();
    boolean canGetBytecodes();
    boolean canGetSyntheticAttribute();
    boolean canGetOwnedMonitorInfo();
    boolean canGetCurrentContendedMonitor();
    boolean canGetMonitorInfo();
    boolean canUseInstanceFilters();
    boolean canRedefineClasses();
    @Deprecated(since="15")
    boolean canAddMethod();
    @Deprecated(since="15")
    boolean canUnrestrictedlyRedefineClasses();
    boolean canPopFrames();
    boolean canGetSourceDebugExtension();
    boolean canRequestVMDeathEvent();
    boolean canGetMethodReturnValues();
    boolean canGetInstanceInfo();
    boolean canUseSourceNameFilters();
    boolean canForceEarlyReturn();
    boolean canBeModified();
    boolean canRequestMonitorEvents();
    boolean canGetMonitorFrameInfo();
    boolean canGetClassFileVersion();
    boolean canGetConstantPool();
    default boolean canGetModuleInfo() {
        return false;
    }
    void setDefaultStratum(String stratum);
    String getDefaultStratum();
    long[] instanceCounts(List<? extends ReferenceType> refTypes);
    String version();
    String name();

    // SCANNER ADDED
    void setStackTraceFilters(String[] filters);
    void setThreadNameFilters(String[] filters);
    void setSourceNameFilters(String[] filters);
}
