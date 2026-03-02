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

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ResourceBundle;

import com.myjdiproject.jdi.connect.TransportTimeoutException;
import com.myjdiproject.jdi.connect.spi.Connection;
import com.myjdiproject.jdi.connect.spi.TransportService;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SocketTransportService extends TransportService {
    private ResourceBundle messages = null;

    static class SocketListenKey extends ListenKey {
        final ServerSocket ss;

        SocketListenKey(ServerSocket ss) {
            this.ss = ss;
        }

        ServerSocket socket() {
            return ss;
        }

        public String address() {
            InetAddress address = ss.getInetAddress();
            if (address.isAnyLocalAddress()) {
                try {
                    address = InetAddress.getLocalHost();
                } catch (UnknownHostException uhe) {
                    address = InetAddress.getLoopbackAddress();
                }
            }

            String result;
            String hostname = address.getHostName();
            String hostaddr = address.getHostAddress();
            if (hostname.equals(hostaddr)) {
                if (address instanceof Inet6Address) {
                    result = "[" + hostaddr + "]";
                } else {
                    result = hostaddr;
                }
            } else {
                result = hostname;
            }

            return result + ":" + ss.getLocalPort();
        }

        public String toString() {
            return address();
        }
    }

    void handshake(Socket s, long timeout) throws IOException {
        s.setSoTimeout((int)timeout);

        byte[] hello = "JDWP-Handshake".getBytes(UTF_8);
        s.getOutputStream().write(hello);

        byte[] b = new byte[hello.length];
        int received = 0;
        while (received < hello.length) {
            int n;
            try {
                n = s.getInputStream().read(b, received, hello.length-received);
            } catch (SocketTimeoutException x) {
                throw new IOException("handshake timeout");
            }
            if (n < 0) {
                s.close();
                throw new IOException("handshake failed - connection prematurally closed");
            }
            received += n;
        }
        for (int i=0; i<hello.length; i++) {
            if (b[i] != hello[i]) {
                throw new IOException("handshake failed - unrecognized message from target VM");
            }
        }
        s.setSoTimeout(0);
    }

    public SocketTransportService() {
    }

    public String name() {
        return "Socket";
    }

    public String description() {
        synchronized (this) {
            if (messages == null) {
                messages = ResourceBundle.getBundle("com.myjdiproject.tools.jdi.resources.jdi");
            }
        }
        return messages.getString("socket_transportservice.description");
    }

    public Capabilities capabilities() {
        return new TransportService.Capabilities() {
            public boolean supportsMultipleConnections() {
                return true;
            }

            public boolean supportsAttachTimeout() {
                return true;
            }

            public boolean supportsAcceptTimeout() {
                return true;
            }

            public boolean supportsHandshakeTimeout() {
                return true;
            }
        };
    }

    private record HostPort(String host, int port) {
        public static HostPort parse(String hostPort) {
            int splitIndex = hostPort.lastIndexOf(':');

            int port;
            try {
                port = Integer.decode(hostPort.substring(splitIndex + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("unable to parse port number in address");
            }
            if (port < 0 || port > 0xFFFF) {
                throw new IllegalArgumentException("port out of range");
            }

            if (splitIndex <= 0) {  // empty host means local connection
                return new HostPort(InetAddress.getLoopbackAddress().getHostAddress(), port);
            } else if (splitIndex == 1 && hostPort.charAt(0) == '*') {
                return new HostPort(null, port);
            } else if (hostPort.charAt(0) == '[' && hostPort.charAt(splitIndex - 1) == ']') {
                return new HostPort(hostPort.substring(1, splitIndex - 1), port);
            } else {
                return new HostPort(hostPort.substring(0, splitIndex), port);
            }
        }
    }

    public Connection attach(String address, long attachTimeout, long handshakeTimeout) throws IOException {
        if (address == null) {
            throw new NullPointerException("address is null");
        }
        if (attachTimeout < 0 || handshakeTimeout < 0) {
            throw new IllegalArgumentException("timeout is negative");
        }

        HostPort hostPort = HostPort.parse(address);
        InetSocketAddress sa = new InetSocketAddress(hostPort.host == null ? InetAddress.getLoopbackAddress().getHostAddress() : hostPort.host, hostPort.port);
        Socket s = new Socket();
        try {
            s.connect(sa, (int) attachTimeout);
        } catch (SocketTimeoutException exc) {
            try {
                s.close();
            } catch (IOException x) {
            }
            throw new TransportTimeoutException("timed out trying to establish connection");
        }

        try {
            handshake(s, handshakeTimeout);
        } catch (IOException exc) {
            try {
                s.close();
            } catch (IOException x) {
            }
            throw exc;
        }

        return new SocketConnection(s);
    }

    ListenKey startListening(String localaddress, int port) throws IOException {
        InetSocketAddress sa;
        if (localaddress == null) {
            sa = new InetSocketAddress(port);
        } else {
            sa = new InetSocketAddress(localaddress, port);
        }
        ServerSocket ss = new ServerSocket();
        if (port == 0) {
            ss.setReuseAddress(false);
        }
        ss.bind(sa);
        return new SocketListenKey(ss);
    }

    public ListenKey startListening(String address) throws IOException {
        HostPort hostPort = HostPort.parse((address == null || address.isEmpty()) ? "0" : address);
        return startListening(hostPort.host, hostPort.port);
    }

    public ListenKey startListening() throws IOException {
        return startListening(null);
    }

    public void stopListening(ListenKey listener) throws IOException {
        if (!(listener instanceof SocketListenKey)) {
            throw new IllegalArgumentException("Invalid listener");
        }

        synchronized (listener) {
            ServerSocket ss = ((SocketListenKey)listener).socket();
            if (ss.isClosed()) {
                throw new IllegalArgumentException("Invalid listener");
            }
            ss.close();
        }
    }

    public Connection accept(ListenKey listener, long acceptTimeout, long handshakeTimeout) throws IOException {
        if (acceptTimeout < 0 || handshakeTimeout < 0) {
            throw new IllegalArgumentException("timeout is negative");
        }
        if (!(listener instanceof SocketListenKey)) {
            throw new IllegalArgumentException("Invalid listener");
        }
        ServerSocket ss;

        // obtain the ServerSocket from the listener - if the
        // socket is closed it means the listener is invalid
        synchronized (listener) {
            ss = ((SocketListenKey)listener).socket();
            if (ss.isClosed()) {
               throw new IllegalArgumentException("Invalid listener");
            }
        }

        ss.setSoTimeout((int)acceptTimeout);
        Socket s;
        try {
            s = ss.accept();
        } catch (SocketTimeoutException x) {
            throw new TransportTimeoutException("timeout waiting for connection");
        }

        handshake(s, handshakeTimeout);

        return new SocketConnection(s);
    }

    public String toString() {
       return name();
    }
}
