/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.contentindex.client;

import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;

/**
 * 
 * @author Daniel Bernstein
 *
 */
public class ESContentIndexClientUtil {
    
    public static Client createESClient(){
        Map<String,String> settingsMap = new HashMap<>();

        Settings settings = ImmutableSettings.settingsBuilder()
                                             .put(settingsMap).build();
        Client client = new TransportClient(settings)
            .addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
            
        return client;
    }

    public static ContentIndexClient createContentIndexClient(){
        Client client = createESClient();
        ElasticsearchOperations elasticsearchOperations =
            new ElasticsearchTemplate(client);
        return new ESContentIndexClient(elasticsearchOperations,
                                                      client);
    }
}