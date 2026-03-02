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
package com.myjdiproject.tools.jdi;

import java.io.IOException;
import java.util.Map;

import com.myjdiproject.jdi.VirtualMachine;
import com.myjdiproject.jdi.connect.Connector;
import com.myjdiproject.jdi.connect.IllegalConnectorArgumentsException;

public class SocketAttachingConnector extends GenericAttachingConnector {
    public static final String ARG_PORT = "port";
    public static final String ARG_HOST = "hostname";

    public SocketAttachingConnector() {
        super(new SocketTransportService());

        String defaultHostName = "localhost";

        addStringArgument(
            ARG_HOST,
            defaultHostName,
            false);

        addIntegerArgument(
            ARG_PORT,
            "",
            true,
            0, Integer.MAX_VALUE);

        transport = () -> "dt_socket";
    }

    public VirtualMachine attach(Map<String, ? extends Connector.Argument> arguments) throws IOException, IllegalConnectorArgumentsException {
        String host = argument(ARG_HOST, arguments).value();
        if (!host.isEmpty()) {
            host = host + ":";
        }
        String address = host + argument(ARG_PORT, arguments).value();
        return super.attach(address, arguments);
    }

    public String name() {
       return "com.myjdiproject.jdi.SocketAttach";
    }
}
