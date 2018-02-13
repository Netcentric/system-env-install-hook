/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook;

import java.util.Map;

/** A source of config variables. */
public abstract class VariablesSource {

    public static class NamedValue {
        final String sourceName;
        final String varName;
        final String value;

        public NamedValue(String sourceName, String varName, String value) {
            super();
            this.sourceName = sourceName;
            this.varName = varName;
            this.value = value;
        }

        public String getSourceName() {
            return sourceName;
        }

        public String getVarName() {
            return varName;
        }

        public String getValue() {
            return value;
        }

    }

    private final String name;

    protected final Map<String, String> variables;

    protected VariablesSource(String name, Map<String, String> envMap) {
        this.name = name;
        this.variables = envMap;
    }

    public NamedValue get(String varName) {
        if (variables.containsKey(varName)) {
            return new NamedValue(getName(), varName, variables.get(varName));
        } else {
            return null;
        }
    }

    public String getName() {
        return name;
    }

}
