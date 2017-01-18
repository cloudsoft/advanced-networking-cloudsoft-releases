/*
 * Copyright 2013-2015 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.networking.vclouddirector;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;

import brooklyn.networking.vclouddirector.nat.NatService;
import brooklyn.networking.vclouddirector.nat.PortForwardingConfig;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.util.exceptions.Exceptions;

public class NatDirectClient implements NatClient {

    /**
     * Returns the mutex to be synchronized on whenever accessing/editing the DNAT rules for a given endpoint.
     */
    private static enum MutexRegistry {
        INSTANCE;
        
        private final Map<String, Object> mutexes = Maps.newLinkedHashMap();
        
        public Object getMutexFor(String endpoint) {
            synchronized (mutexes) {
                Object mutex = mutexes.get(endpoint);
                if (mutex == null) {
                    mutex = new Object();
                    mutexes.put(endpoint, mutex);
                }
                return mutex;
            }
        }
    }

    private final NatService client;

    @VisibleForTesting
    public NatService getClient() {
        return client;
    }
    
    public NatDirectClient(JcloudsLocation loc) {
        String identity = checkNotNull(loc.getIdentity(), "identity");
        String credential = checkNotNull(loc.getCredential(), "credential");
        
        checkArgument(identity.contains("@"), "identity %s does not contain vOrg, in location %s", identity, loc);
        String vOrg = identity.substring(identity.lastIndexOf("@") + 1);
        String endpoint = transformEndpoint(loc.getEndpoint(), vOrg);
        String vDC = loc.getRegion();

        client = NatService.builder()
                .endpoint(endpoint)
                .vDC(vDC)
                .identity(identity)
                .credential(credential)
                .mutex(MutexRegistry.INSTANCE.getMutexFor(endpoint))
                .build();
    }

    @Override
    public HostAndPort openPortForwarding(PortForwardingConfig args) {
        try {
            return client.openPortForwarding(args);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HostAndPort closePortForwarding(PortForwardingConfig args) {
        try {
            return client.closePortForwarding(args);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    // jclouds endpoint has suffix "/api"; but VMware SDK wants it without "api"
    public static String transformEndpoint(String endpoint) {
        return transformEndpoint(endpoint, null);
    }

    // jclouds endpoint has suffix "/api"; but VMware SDK wants it without "api" + tenant
    // i.e.: https://emea01.canopy-cloud.com/cloud/org/cct-emea01/
    public static String transformEndpoint(String endpoint, String vOrg) {
        String path = null;
        if (vOrg != null) {
            path = String.format("/cloud/org/%s", vOrg);
        }
        try {
            URI uri = URI.create(endpoint);
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path, null, null).toString();
        } catch (URISyntaxException e) {
            throw Exceptions.propagate(e);
        }
    }
}
