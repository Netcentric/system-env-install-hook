/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook.sources;

import java.util.HashMap;

import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource;

/** Loads the env from OS env variables. */
public class OsEnvVarsSource extends VariablesSource {

    public static final String NAME = "OsEnvVars";

    public OsEnvVarsSource() {
        super(NAME, new HashMap<String, String>(System.getenv()));
    }

    @Override
    public NamedValue get(String varName) {
        // there cannot be "." in env vars... if the files in the package have dots as it is typically done, the env var can be provided
        // with _ instead of dot
        String effectiveName = varName.replaceAll("\\.", "_");

        return super.get(effectiveName);
    }

}
