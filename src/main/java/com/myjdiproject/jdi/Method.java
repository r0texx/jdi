/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

public interface Method extends TypeComponent, Locatable, Comparable<Method> {
    String returnTypeName();
    Type returnType() throws ClassNotLoadedException;
    List<String> argumentTypeNames();
    List<Type> argumentTypes() throws ClassNotLoadedException;
    boolean isAbstract();
    default boolean isDefault() {
        throw new UnsupportedOperationException();
    }
    boolean isSynchronized();
    boolean isNative();
    boolean isVarArgs();
    boolean isBridge();
    boolean isConstructor();
    boolean isStaticInitializer();
    boolean isObsolete();
    List<Location> allLineLocations() throws AbsentInformationException;
    List<Location> allLineLocations(String stratum, String sourceName) throws AbsentInformationException;
    List<Location> locationsOfLine(int lineNumber) throws AbsentInformationException;
    List<Location> locationsOfLine(String stratum, String sourceName, int lineNumber) throws AbsentInformationException;
    Location locationOfCodeIndex(long codeIndex);
    List<LocalVariable> variables() throws AbsentInformationException;
    List<LocalVariable> variablesByName(String name) throws AbsentInformationException;
    List<LocalVariable> arguments() throws AbsentInformationException;
    byte[] bytecodes();
    Location location();
    Location firstLineLocation();
    boolean equals(Object obj);
    int hashCode();
    long ref();

    // SCANNER ADDED
    List<ReferenceType> annotationTypes() throws ClassNotLoadedException;
}
