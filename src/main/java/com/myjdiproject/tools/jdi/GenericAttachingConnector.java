/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.myjdiproject.jdi.Bootstrap;
import com.myjdiproject.jdi.VirtualMachine;
import com.myjdiproject.jdi.connect.AttachingConnector;
import com.myjdiproject.jdi.connect.Connector;
import com.myjdiproject.jdi.connect.IllegalConnectorArgumentsException;
import com.myjdiproject.jdi.connect.Transport;
import com.myjdiproject.jdi.connect.spi.Connection;
import com.myjdiproject.jdi.connect.spi.TransportService;

public class GenericAttachingConnector extends ConnectorImpl implements AttachingConnector {
    static final String ARG_ADDRESS = "address";
    static final String ARG_TIMEOUT = "timeout";

    final TransportService transportService;
    Transport transport;

    private GenericAttachingConnector(TransportService ts, boolean addAddressArgument) {
        transportService = ts;
        transport = transportService::name;

        if (addAddressArgument) {
            addStringArgument(
                ARG_ADDRESS,
                "",
                true);
        }

        addIntegerArgument(
                ARG_TIMEOUT,
                "",
                false,
                0, Integer.MAX_VALUE);
    }

    protected GenericAttachingConnector(TransportService ts) {
        this(ts, false);
    }

    public static GenericAttachingConnector create(TransportService ts) {
        return new GenericAttachingConnector(ts, true);
    }

    public VirtualMachine attach(String address, Map<String, ? extends Connector.Argument> args) throws IOException, IllegalConnectorArgumentsException {
        String ts  = argument(ARG_TIMEOUT, args).value();
        int timeout = 0;
        if (!ts.isEmpty()) {
            timeout = Integer.decode(ts);
        }
        Connection connection = transportService.attach(address, timeout, 0);
        return Bootstrap.virtualMachineManager().createVirtualMachine(connection);
    }

    public VirtualMachine attach(Map<String, ? extends Connector.Argument> args) throws IOException, IllegalConnectorArgumentsException {
        String address = argument(ARG_ADDRESS, args).value();
        return attach(address, args);
    }

    public String name() {
        return transport.name() + "Attach";
    }

    public String description() {
        return transportService.description();
    }

    public Transport transport() {
        return transport;
    }
}
