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

import java.io.Serial;

class JDWPException extends Exception {
    @Serial
    private static final long serialVersionUID = -6321344442751299874L;

    final short errorCode;

    JDWPException(short errorCode) {
        super();
        this.errorCode = errorCode;
    }

    short errorCode() {
        return errorCode;
    }

    RuntimeException toJDIException() {
        return switch (errorCode) {
            case JDWP.Error.ABSENT_INFORMATION -> new AbsentInformationException();
            case JDWP.Error.INVALID_LOCATION -> new InvalidLocationException();
            case JDWP.Error.INVALID_OBJECT -> new ObjectCollectedException();
            case JDWP.Error.INVALID_MODULE -> new InvalidModuleException();
            case JDWP.Error.VM_DEAD -> new VMDisconnectedException();
            case JDWP.Error.OUT_OF_MEMORY -> new VMOutOfMemoryException();
            case JDWP.Error.CLASS_NOT_PREPARED -> new ClassNotPreparedException();
            case JDWP.Error.INVALID_FRAMEID, JDWP.Error.NOT_CURRENT_FRAME -> new InvalidStackFrameException();
            case JDWP.Error.NOT_IMPLEMENTED -> new UnsupportedOperationException();
            case JDWP.Error.INVALID_INDEX, JDWP.Error.INVALID_LENGTH -> new IndexOutOfBoundsException();
            case JDWP.Error.TYPE_MISMATCH -> new InconsistentDebugInfoException();
            case JDWP.Error.INVALID_THREAD -> new IllegalThreadStateException("Invalid thread");
            case JDWP.Error.THREAD_NOT_SUSPENDED -> new IllegalThreadStateException("Thread not suspended");
            case JDWP.Error.OPAQUE_FRAME -> new OpaqueFrameException();
            default -> new InternalException("Unexpected JDWP Error: " + errorCode, errorCode);
        };
    }
}
