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

package com.myjdiproject.jdi.connect;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public interface Connector {
    String name();
    Transport transport();
    Map<String, Connector.Argument> defaultArguments();

    interface Argument extends Serializable {
        String name();
        String value();
        void setValue(String value);
        boolean isValid(String value);
        boolean mustSpecify();
    }

    interface BooleanArgument extends Argument {
        void setValue(boolean value);
        boolean isValid(String value);
        String stringValueOf(boolean value);
        boolean booleanValue();
    }

    interface IntegerArgument extends Argument {
        void setValue(int value);
        boolean isValid(String value);
        boolean isValid(int value);
        String stringValueOf(int value);
        int intValue();
        int max();
        int min();
    }

    interface StringArgument extends Argument {
        boolean isValid(String value);
    }

    interface SelectedArgument extends Argument {
        List<String> choices();
        boolean isValid(String value);
    }
}
