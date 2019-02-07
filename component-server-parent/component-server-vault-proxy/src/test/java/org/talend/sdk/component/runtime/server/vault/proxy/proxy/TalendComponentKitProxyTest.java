/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.runtime.server.vault.proxy.proxy;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.MonoMeecrowaveConfig;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.junit.jupiter.api.Test;

@MonoMeecrowaveConfig
class TalendComponentKitProxyTest {

    @ConfigurationInject
    private Meecrowave.Builder serverConfig;

    @Inject
    private Client client;

    @Test
    void action() { // the mock returns its input to allow is to test input is deciphered
        final Map<String, String> decrypted = base()
                .path("api/v1/action/execute")
                .queryParam("family", "testf")
                .queryParam("type", "testt")
                .queryParam("action", "testa")
                .queryParam("lang", "testl")
                .request(APPLICATION_JSON_TYPE)
                .post(entity(new HashMap<String, String>() {

                    {
                        put("configuration.username", "simple");
                        put("configuration.password", "vault:v1:hcccVPODe9oZpcr/sKam8GUrbacji8VkuDRGfuDt7bg7VA==");
                    }
                }, APPLICATION_JSON_TYPE), new GenericType<Map<String, String>>() {
                });
        assertEquals(new HashMap<String, String>() {

            {
                // original value (not ciphered)
                put("configuration.username", "simple");
                // deciphered value
                put("configuration.password", "test");
                // action input config
                put("family", "testf");
                put("type", "testt");
                put("action", "testa");
                put("lang", "testl");
            }
        }, decrypted);
    }

    private WebTarget base() {
        return client.target("http://localhost:" + serverConfig.getHttpPort());
    }
}
