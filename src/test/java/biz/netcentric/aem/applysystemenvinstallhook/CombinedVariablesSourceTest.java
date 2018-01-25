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
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Arrays;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;
import org.slf4j.Logger;

import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource.NamedValue;

public class CombinedVariablesSourceTest {

    @Spy
    private InstallHookLogger logger = new InstallHookLogger();

    @Before
    public void setup() {
        initMocks(this);
        doNothing().when(logger).log(anyString());
        doNothing().when(logger).log(any(Logger.class), anyString());
    }

    @Test
    public void testForSources() {

        VariablesSource varSource1 = new VariablesSource("testsource1",
                new HashMap<String, String>() {
                    {
                        put("var1", "val1");
                        put("var2", "val2");
                    }
                }) {
        };
        VariablesSource varSource2 = new VariablesSource("testsource2",
                new HashMap<String, String>() {
                    {
                        put("var2", "val2hidden");
                        put("var3", "val3");
                    }
                }) {
        };

        CombinedVariablesSource combinedVariablesSource = new CombinedVariablesSource("", Arrays.asList(varSource1, varSource2));

        NamedValue namedValue1 = combinedVariablesSource.get("var1");
        assertEquals("testsource1", namedValue1.sourceName);
        assertEquals("val1", namedValue1.value);

        NamedValue namedValue2 = combinedVariablesSource.get("var2");
        assertEquals("testsource1", namedValue2.sourceName);
        assertEquals("val2", namedValue2.value);

        NamedValue namedValue3 = combinedVariablesSource.get("var3");
        assertEquals("testsource2", namedValue3.sourceName);
        assertEquals("val3", namedValue3.value);

        NamedValue namedValue4 = combinedVariablesSource.get("val4");
        assertNull(namedValue4);

    }

}
