/*
 * Copyright (c) 1998, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serial;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import com.myjdiproject.jdi.InternalException;
import com.myjdiproject.jdi.connect.Connector;
import com.myjdiproject.jdi.connect.IllegalConnectorArgumentsException;

abstract class ConnectorImpl implements Connector {
    final Map<String, Argument> defaultArguments = new LinkedHashMap<>();

    static String trueString = null;
    static String falseString;

    public Map<String,Argument> defaultArguments() {
        Map<String,Argument> defaults = new LinkedHashMap<>();
        Collection<Argument> values = defaultArguments.values();

        for (Argument a : values) {
            ArgumentImpl argument = (ArgumentImpl)a;
            defaults.put(argument.name(), (Argument)argument.clone());
        }
        return defaults;
    }

    void addStringArgument(String name, String defaultValue, boolean mustSpecify) {
        defaultArguments.put(name, new StringArgumentImpl(name, defaultValue, mustSpecify));
    }

    void addBooleanArgument(String name, boolean defaultValue, boolean mustSpecify) {
        defaultArguments.put(name, new BooleanArgumentImpl(name, defaultValue, mustSpecify));
    }

    void addIntegerArgument(String name, String defaultValue, boolean mustSpecify, int min, int max) {
        defaultArguments.put(name, new IntegerArgumentImpl(name, defaultValue, mustSpecify, min, max));
    }

    void addSelectedArgument(String name, String defaultValue, boolean mustSpecify, List<String> list) {
        defaultArguments.put(name, new SelectedArgumentImpl(name, defaultValue, mustSpecify, list));
    }

    ArgumentImpl argument(String name, Map<String, ? extends Argument> arguments) throws IllegalConnectorArgumentsException {
        ArgumentImpl argument = (ArgumentImpl)arguments.get(name);
        if (argument == null) {
            throw new IllegalConnectorArgumentsException("Argument missing", name);
        }
        String value = argument.value();
        if (value == null || value.isEmpty()) {
            if (argument.mustSpecify()) {
                throw new IllegalConnectorArgumentsException("Argument unspecified", name);
            }
        } else if (!argument.isValid(value)) {
            throw new IllegalConnectorArgumentsException("Argument invalid", name);
        }
        return argument;
    }


    private ResourceBundle messages = null;

    public String toString() {
        String string = name() + " (defaults: ";
        Iterator<Argument> iter = defaultArguments().values().iterator();
        boolean first = true;
        while (iter.hasNext()) {
            ArgumentImpl argument = (ArgumentImpl) iter.next();
            if (!first) {
                string += ", ";
            }
            string += argument.toString();
            first = false;
        }
        string += ")";
        return string;
    }

    abstract static class ArgumentImpl implements Connector.Argument, Cloneable {
        private final String name;
        private String value;
        private final boolean mustSpecify;

        ArgumentImpl(String name, String value, boolean mustSpecify) {
            this.name = name;
            this.value = value;
            this.mustSpecify = mustSpecify;
        }

        public abstract boolean isValid(String value);

        public String name() {
            return name;
        }

        public String value() {
            return value;
        }

        public void setValue(String value) {
            if (value == null) {
                throw new NullPointerException("Can't set null value");
            }
            this.value = value;
        }

        public boolean mustSpecify() {
            return mustSpecify;
        }

        public boolean equals(Object obj) {
            if (obj instanceof Argument other) {
                return (name().equals(other.name())) &&
                       (mustSpecify() == other.mustSpecify()) &&
                       (value().equals(other.value()));
            } else {
                return false;
            }
        }

        public int hashCode() {
            return name().hashCode();
        }

        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                throw new InternalException();
            }
        }

        public String toString() {
            return name() + "=" + value();
        }
    }

    class BooleanArgumentImpl extends ConnectorImpl.ArgumentImpl implements Connector.BooleanArgument {
        @Serial
        private static final long serialVersionUID = 1624542968639361316L;
        BooleanArgumentImpl(String name, boolean value, boolean mustSpecify) {
            super(name, null, mustSpecify);
            if (trueString == null) {
                trueString = "true";
                falseString = "false";
            }
            setValue(value);
        }

        public void setValue(boolean value) {
            setValue(stringValueOf(value));
        }

        public boolean isValid(String value) {
            return value.equals(trueString) || value.equals(falseString);
        }

        public String stringValueOf(boolean value) {
            return value? trueString : falseString;
        }

        public boolean booleanValue() {
            return value().equals(trueString);
        }
    }

    static class IntegerArgumentImpl extends ConnectorImpl.ArgumentImpl implements Connector.IntegerArgument {
        @Serial
        private static final long serialVersionUID = 763286081923797770L;
        private final int min;
        private final int max;

        IntegerArgumentImpl(String name, String value, boolean mustSpecify, int min, int max) {
            super(name, value, mustSpecify);
            this.min = min;
            this.max = max;
        }

        public void setValue(int value) {
            setValue(stringValueOf(value));
        }

        public boolean isValid(String value) {
            if (value == null) {
                return false;
            }
            try {
                return isValid(Integer.decode(value));
            } catch (NumberFormatException exc) {
                return false;
            }
        }

        public boolean isValid(int value) {
            return min <= value && value <= max;
        }

        public String stringValueOf(int value) {
            return "" + value;
        }

        public int intValue() {
            if (value() == null) {
                return 0;
            }
            try {
                return Integer.decode(value());
            } catch(NumberFormatException exc) {
                return 0;
            }
        }

        public int max() {
            return max;
        }

        public int min() {
            return min;
        }
    }

    static class StringArgumentImpl extends ConnectorImpl.ArgumentImpl implements Connector.StringArgument {
        @Serial
        private static final long serialVersionUID = 7500484902692107464L;
        StringArgumentImpl(String name, String value, boolean mustSpecify) {
            super(name, value, mustSpecify);
        }

        public boolean isValid(String value) {
            return true;
        }
    }

    static class SelectedArgumentImpl extends ConnectorImpl.ArgumentImpl implements Connector.SelectedArgument {
        @Serial
        private static final long serialVersionUID = -5689584530908382517L;

        private final List<String> choices;

        SelectedArgumentImpl(String name, String value, boolean mustSpecify, List<String> choices) {
            super(name, value, mustSpecify);
            this.choices = List.copyOf(choices);
        }

        public List<String> choices() {
            return choices;
        }

        public boolean isValid(String value) {
            return choices.contains(value);
        }
    }
}
