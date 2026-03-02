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

package com.myjdiproject.tools.jdi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.ServiceLoader;

import com.myjdiproject.jdi.VMDisconnectedException;
import com.myjdiproject.jdi.VirtualMachine;
import com.myjdiproject.jdi.VirtualMachineManager;
import com.myjdiproject.jdi.connect.AttachingConnector;
import com.myjdiproject.jdi.connect.Connector;
import com.myjdiproject.jdi.connect.ListeningConnector;
import com.myjdiproject.jdi.connect.spi.Connection;
import com.myjdiproject.jdi.connect.spi.TransportService;

public class VirtualMachineManagerImpl implements VirtualMachineManagerService {
    private final List<Connector> connectors = new ArrayList<>();
    private final List<VirtualMachine> targets = new ArrayList<>();
    private final ThreadGroup mainGroupForJDI;
    private ResourceBundle messages = null;
    private int vmSequenceNumber = 0;
    private static final int majorVersion = Runtime.version().feature();
    private static final int minorVersion = 0;

    private static final Object lock = new Object();
    private static VirtualMachineManagerImpl vmm;

    public synchronized static VirtualMachineManager virtualMachineManager() {
        if (vmm == null) {
            vmm = new VirtualMachineManagerImpl();
        }
        return vmm;
    }

    protected VirtualMachineManagerImpl() {
        ThreadGroup top = Thread.currentThread().getThreadGroup();
        ThreadGroup parent;
        while ((parent = top.getParent()) != null) {
            top = parent;
        }
        mainGroupForJDI = new ThreadGroup(top, "JDI main");

        // add connectors
        addConnector(new SocketAttachingConnector());

        // add transport
        addConnector(GenericAttachingConnector.create(new SocketTransportService()));

        if (allConnectors().isEmpty()) {
            throw new Error("no Connectors loaded");
        }
    }

    public List<AttachingConnector> attachingConnectors() {
        List<AttachingConnector> attachingConnectors = new ArrayList<>(connectors.size());
        for (Connector connector : connectors) {
            if (connector instanceof AttachingConnector attachingConnector) {
                attachingConnectors.add(attachingConnector);
            }
        }
        return Collections.unmodifiableList(attachingConnectors);
    }

    public List<ListeningConnector> listeningConnectors() {
        List<ListeningConnector> listeningConnectors = new ArrayList<>(connectors.size());
        for (Connector connector : connectors) {
            if (connector instanceof ListeningConnector listeningConnector) {
                listeningConnectors.add(listeningConnector);
            }
        }
        return Collections.unmodifiableList(listeningConnectors);
    }

    public List<Connector> allConnectors() {
        return Collections.unmodifiableList(connectors);
    }

    public List<VirtualMachine> connectedVirtualMachines() {
        return Collections.unmodifiableList(targets);
    }

    public void addConnector(Connector connector) {
        connectors.add(connector);
    }

    public void removeConnector(Connector connector) {
        connectors.remove(connector);
    }

    public synchronized VirtualMachine createVirtualMachine(Connection connection, Process process) throws IOException {
        if (!connection.isOpen()) {
            throw new IllegalStateException("connection is not open");
        }

        VirtualMachine vm;
        try {
            vm = new VirtualMachineImpl(this, connection, process, ++vmSequenceNumber);
        } catch (VMDisconnectedException e) {
            throw new IOException(e);
        }
        targets.add(vm);
        return vm;
    }

    public VirtualMachine createVirtualMachine(Connection connection) throws IOException {
        return createVirtualMachine(connection, null);
    }

    public void addVirtualMachine(VirtualMachine vm) {
        targets.add(vm);
    }

    void disposeVirtualMachine(VirtualMachine vm) {
        targets.remove(vm);
    }

    public int majorInterfaceVersion() {
        return majorVersion;
    }

    public int minorInterfaceVersion() {
        return minorVersion;
    }

    ThreadGroup mainGroupForJDI() {
        return mainGroupForJDI;
    }
}
