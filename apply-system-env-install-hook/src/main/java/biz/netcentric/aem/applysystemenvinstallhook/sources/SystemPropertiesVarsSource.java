/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook.sources;

import java.util.Map;

import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource;

/** Loads the system env from JRE system properties. */
public class SystemPropertiesVarsSource extends VariablesSource {

    public static final String NAME = "SystemProperties";

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public SystemPropertiesVarsSource() {
        super(NAME, (Map) System.getProperties());
    }

}
