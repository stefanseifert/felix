/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.dm.itest.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Assert;

import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.ResourceHandler;
import org.apache.felix.dm.ResourceUtil;
import org.apache.felix.dm.itest.util.Ensure;
import org.apache.felix.dm.itest.util.TestBase;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

@SuppressWarnings({"deprecation", "unchecked", "rawtypes", "unused"})
public class ResourceAdapterTest extends TestBase {
    public void testBasicResourceAdapter() throws Exception {
        DependencyManager m = getDM();
        // helper class that ensures certain steps get executed in sequence
        Ensure e = new Ensure();
        // create a resource provider
        ResourceProvider provider = new ResourceProvider(context, new URL("file://localhost/path/to/file1.txt"));
        // activate it
        m.add(m.createComponent().setImplementation(provider).add(m.createServiceDependency().setService(ResourceHandler.class).setCallbacks("add", "remove")));
        // create a resource adapter for our single resource
        // note that we can provide an actual implementation instance here because there will be only one
        // adapter, normally you'd want to specify a Class here
        m.add(m.createResourceAdapterService("(&(path=/path/to/*.txt)(host=localhost))", false, null, "changed")
              .setImplementation(new ResourceAdapter(e)));
        // wait until the single resource is available
        e.waitForStep(3, 5000);
        // trigger a 'change' in our resource
        provider.change();
        // wait until the changed callback is invoked
        e.waitForStep(4, 5000);
        m.clear();
     }
    
    static class ResourceAdapter {
        protected URL m_resource; // injected by reflection.
        private Ensure m_ensure;
        
        ResourceAdapter(Ensure e) {
            m_ensure = e;
        }
        
        public void start() {
            m_ensure.step(1);
            Assert.assertNotNull("resource not injected", m_resource);
            m_ensure.step(2);
            try {
                InputStream in = m_resource.openStream();
            } 
            catch (FileNotFoundException e) {
                m_ensure.step(3);
            }
            catch (IOException e) {
                Assert.fail("We should not have gotten this exception.");
            }
        }
        
        public void changed() {
            m_ensure.step(4);
        }
    }    
}
