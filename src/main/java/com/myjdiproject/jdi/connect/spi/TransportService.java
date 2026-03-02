/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.myjdiproject.jdi.connect.spi;

import java.io.IOException;

public abstract class TransportService {
    public TransportService() {}

    public abstract String name();
    public abstract String description();

    public abstract static class Capabilities {
        public Capabilities() {}

        public abstract boolean supportsMultipleConnections();
        public abstract boolean supportsAttachTimeout();
        public abstract boolean supportsAcceptTimeout();
        public abstract boolean supportsHandshakeTimeout();
    }

    public abstract Capabilities capabilities();
    public abstract Connection attach(String address, long attachTimeout, long handshakeTimeout) throws IOException;

    public abstract static class ListenKey {
        public ListenKey() {}

        public abstract String address();
    }

    public abstract ListenKey startListening(String address) throws IOException;
    public abstract ListenKey startListening() throws IOException;
    public abstract void stopListening(ListenKey listenKey) throws IOException;
    public abstract Connection accept(ListenKey listenKey, long acceptTimeout, long handshakeTimeout) throws IOException;
}
