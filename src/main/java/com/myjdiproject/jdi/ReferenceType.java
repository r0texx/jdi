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

package com.myjdiproject.jdi;

import java.util.List;
import java.util.Map;

public interface ReferenceType extends Type, Comparable<ReferenceType>, Accessible {
    String name();
    String genericSignature();
    ClassLoaderReference classLoader();
    default ModuleReference module() {
        throw new java.lang.UnsupportedOperationException("The method module() must be implemented");
    }
    String sourceName() throws AbsentInformationException;
    List<String> sourceNames(String stratum) throws AbsentInformationException;
    List<String> sourcePaths(String stratum) throws AbsentInformationException;
    String sourceDebugExtension() throws AbsentInformationException;
    boolean isStatic();
    boolean isAbstract();
    boolean isFinal();
    boolean isPrepared();
    boolean isVerified();
    boolean isInitialized();
    boolean failedToInitialize();
    List<Field> fields();
    List<Field> visibleFields();
    List<Field> allFields();
    Field fieldByName(String fieldName);
    List<Method> methods();
    List<Method> visibleMethods();
    List<Method> allMethods();
    List<Method> methodsByName(String name);
    List<Method> methodsByName(String name, String signature);
    List<ReferenceType> nestedTypes();
    Value getValue(Field field);
    Map<Field,Value> getValues(List<? extends Field> fields);
    ClassObjectReference classObject();
    List<Location> allLineLocations() throws AbsentInformationException;
    List<Location> allLineLocations(String stratum, String sourceName) throws AbsentInformationException;
    List<Location> locationsOfLine(int lineNumber) throws AbsentInformationException;
    List<Location> locationsOfLine(String stratum, String sourceName, int lineNumber) throws AbsentInformationException;
    List<String> availableStrata();
    String defaultStratum();
    List<ObjectReference> instances(long maxInstances);
    boolean equals(Object obj);
    int hashCode();
    int majorVersion();
    int minorVersion();
    int constantPoolCount();
    byte[] constantPool();
    long ref();
}
