/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.core.async;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.test.CoreLoggerContexts;
import org.apache.logging.log4j.core.test.categories.AsyncLoggers;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Category(AsyncLoggers.class)
public class AsyncLoggerConfigTest {

    private static final String FQCN = AsyncLoggerConfigTest.class.getName();

    @BeforeClass
    public static void beforeClass() {
        System.setProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY, "AsyncLoggerConfigTest.xml");
    }

    @Test
    public void testAdditivity() throws Exception {
        final File file = new File("target", "AsyncLoggerConfigTest.log");
        assertTrue("Deleted old file before test", !file.exists() || file.delete());

        final Logger log = LogManager.getLogger("com.foo.Bar");
        final String msg = "Additive logging: 2 for the price of 1!";
        log.info(msg);
        CoreLoggerContexts.stopLoggerContext(file); // stop async thread

        final BufferedReader reader = new BufferedReader(new FileReader(file));
        final String line1 = reader.readLine();
        final String line2 = reader.readLine();
        reader.close();
        file.delete();
        assertNotNull("line1", line1);
        assertNotNull("line2", line2);
        assertTrue("line1 correct", line1.contains(msg));
        assertTrue("line2 correct", line2.contains(msg));

        final String location = "testAdditivity";
        assertTrue("location", line1.contains(location) || line2.contains(location));
    }

    @Test
    public void testIncludeLocationDefaultsToFalse() {
        final LoggerConfig rootLoggerConfig =
                AsyncLoggerConfig.RootLogger.createLogger(
                        null, "INFO", null, new AppenderRef[0], null, new DefaultConfiguration(), null);
        assertFalse("Include location should default to false for async loggers",
                    rootLoggerConfig.isIncludeLocation());

        final LoggerConfig loggerConfig =
                AsyncLoggerConfig.createLogger(
                        null, "INFO", "com.foo.Bar", null, new AppenderRef[0], null, new DefaultConfiguration(),
                        null);
        assertFalse("Include location should default to false for async loggers",
                    loggerConfig.isIncludeLocation());
    }

    @Test
    public void testSingleFilterInvocation() {
        final Configuration configuration = new NullConfiguration();
        final Filter filter = mock(Filter.class);
        final LoggerConfig config = AsyncLoggerConfig.newAsyncBuilder()
                .withLoggerName(FQCN)
                .withConfig(configuration)
                .withLevel(Level.INFO)
                .withtFilter(filter)
                .build();
        final Appender appender = mock(Appender.class);
        when(appender.isStarted()).thenReturn(true);
        when(appender.getName()).thenReturn("test");
        config.addAppender(appender, null, null);
        final AsyncLoggerConfigDisruptor disruptor = (AsyncLoggerConfigDisruptor) configuration.getAsyncLoggerConfigDelegate();
        disruptor.start();
        try {
            config.log(FQCN, FQCN, null, Level.INFO, new SimpleMessage(), null);
            verify(appender, timeout(100).times(1)).append(any());
            verify(filter, times(1)).filter(any());
        } finally {
            disruptor.stop();
        }
    }
}
