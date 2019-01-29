/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.slf4j.Logger;

public class VariablesMergerTest {

    @Spy
    private InstallHookLogger logger = new InstallHookLogger();

    @Before
    public void setup() {
        initMocks(this);
        doNothing().when(logger).log(anyString());
        doNothing().when(logger).log(any(Logger.class), anyString());
    }

    @Test
    public void testApplyEnvVars() {

        VariablesMerger variablesMerger = Mockito.spy(new VariablesMerger(logger));


        VariablesSource varSource = new VariablesSource("testsource",
                new HashMap<String, String>() {
                    {
                        put("var1", "val1");
                        put("prefix.var2", "val2");
                        put("urlConfig", "http://www.example.com");
                        put("var.special.chars", "val=3 with\"\\");
                    }
                }) {
        };

        
        assertEquals("no variables", variablesMerger.applyEnvVars("no variables", varSource, "/test"));
        assertEquals("val1", variablesMerger.applyEnvVars("${var1}", varSource, "/test"));

        assertEquals("val\\=3\\ with\\\"\\\\", variablesMerger.applyEnvVars("${var.special.chars}", varSource, "/test.config"));
        assertEquals("x-val\\=3\\ with\\\"\\\\-x", variablesMerger.applyEnvVars("x-${var.special.chars}-x", varSource, "/test.config"));

        assertEquals("val=3 with\"\\", variablesMerger.applyEnvVars("${var.special.chars}", varSource, "/test.xml"));
        assertEquals("x-val=3 with\"\\-x", variablesMerger.applyEnvVars("x-${var.special.chars}-x", varSource, "/test.xml"));

        assertEquals("x-val1-x", variablesMerger.applyEnvVars("x-${var1}-x", varSource, "/test"));
        assertEquals("x-val2-x", variablesMerger.applyEnvVars("x-${prefix.var2}-x", varSource, "/test"));
        assertEquals("x-val2-x-val1", variablesMerger.applyEnvVars("x-${prefix.var2}-x-${var1}", varSource, "/test"));

        assertEquals("x-val1-x", variablesMerger.applyEnvVars("x-${var1:defaultVal}-x", varSource, "/test"));
        assertEquals("x-defaultVal-x", variablesMerger.applyEnvVars("x-${varNotInEnv:defaultVal}-x", varSource, "/test"));
        assertEquals("x-${varNotInEnv}-x", variablesMerger.applyEnvVars("x-${varNotInEnv}-x", varSource, "/test"));
        assertEquals("x--x", variablesMerger.applyEnvVars("x-${varNotInEnv:}-x", varSource, "/test"));

        assertEquals("x-http://www.example.com-x", variablesMerger.applyEnvVars("x-${urlConfig}-x", varSource, "/test"));
        assertEquals("x-http://www.example.com-x",
                variablesMerger.applyEnvVars("x-${urlConfig:http://other.example.com}-x", varSource, "/test"));
        assertEquals("x-http://other.example.com-x",
                variablesMerger.applyEnvVars("x-${urlConfigVarNotSet:http://other.example.com}-x", varSource, "/test"));

    }

}
