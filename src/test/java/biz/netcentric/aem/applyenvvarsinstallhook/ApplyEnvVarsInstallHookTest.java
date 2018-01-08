/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applyenvvarsinstallhook;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ApplyEnvVarsInstallHookTest {

    @Test
    public void testApplyEnvVars() {
        ApplyEnvVarsInstallHook hook = new ApplyEnvVarsInstallHook();
        
        Map<String, String> env = new HashMap<String,String>();
        env.put("var1", "val1");
        env.put("prefix_var2", "val2");
        env.put("urlConfig", "http://www.example.com");
        
        assertEquals("no variables", hook.applyEnvVars("no variables", env));
        assertEquals("val1", hook.applyEnvVars("${var1}", env));

        assertEquals("x-val1-x", hook.applyEnvVars("x-${var1}-x", env));
        assertEquals("x-val2-x", hook.applyEnvVars("x-${prefix.var2}-x", env));
        assertEquals("x-val2-x-val1", hook.applyEnvVars("x-${prefix.var2}-x-${var1}", env));

        assertEquals("x-val1-x", hook.applyEnvVars("x-${var1:defaultVal}-x", env));
        assertEquals("x-defaultVal-x", hook.applyEnvVars("x-${varNotInEnv:defaultVal}-x", env));
        assertEquals("x-${varNotInEnv}-x", hook.applyEnvVars("x-${varNotInEnv}-x", env));
        assertEquals("x--x", hook.applyEnvVars("x-${varNotInEnv:}-x", env));

        assertEquals("x-http://www.example.com-x", hook.applyEnvVars("x-${urlConfig}-x", env));
        assertEquals("x-http://www.example.com-x", hook.applyEnvVars("x-${urlConfig:http://other.example.com}-x", env));
        assertEquals("x-http://other.example.com-x", hook.applyEnvVars("x-${urlConfigVarNotSet:http://other.example.com}-x", env));

    }

}
