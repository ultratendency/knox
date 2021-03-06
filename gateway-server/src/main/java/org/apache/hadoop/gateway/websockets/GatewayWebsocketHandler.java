/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.websockets;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.service.definition.ServiceDefinition;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.registry.ServiceDefEntry;
import org.apache.hadoop.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.hadoop.gateway.services.registry.ServiceRegistry;
import org.apache.hadoop.gateway.util.ServiceDefinitionsLoader;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * Websocket handler that will handle websocket connection request. This class
 * is responsible for creating a proxy socket for inbound and outbound
 * connections. This is also where the http to websocket handoff happens.
 * 
 * @since 0.10
 */
public class GatewayWebsocketHandler extends WebSocketHandler
    implements WebSocketCreator {

  private static WebsocketLogMessages LOG = MessagesFactory
      .get(WebsocketLogMessages.class);

  public final static String WEBSOCKET_PROTOCOL_STRING = "ws://";
  
  final static String REGEX_SPLIT_CLUSTER_NAME = "^((?:[^/]*/){1}[^/]*)";
  
  final static String REGEX_SPLIT_CONTEXT = "^((?:[^/]*/){2}[^/]*)";

  final GatewayConfig config;
  final GatewayServices services;

  /**
   * Create an instance
   * 
   * @param config
   * @param services
   */
  public GatewayWebsocketHandler(final GatewayConfig config,
      final GatewayServices services) {
    super();

    this.config = config;
    this.services = services;

  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.eclipse.jetty.websocket.server.WebSocketHandler#configure(org.eclipse.
   * jetty.websocket.servlet.WebSocketServletFactory)
   */
  @Override
  public void configure(final WebSocketServletFactory factory) {
    factory.setCreator(this);
    factory.getPolicy()
        .setMaxTextMessageSize(config.getWebsocketMaxTextMessageSize());
    factory.getPolicy()
        .setMaxBinaryMessageSize(config.getWebsocketMaxBinaryMessageSize());

    factory.getPolicy().setMaxBinaryMessageBufferSize(
        config.getWebsocketMaxBinaryMessageBufferSize());
    factory.getPolicy().setMaxTextMessageBufferSize(
        config.getWebsocketMaxTextMessageBufferSize());

    factory.getPolicy()
        .setInputBufferSize(config.getWebsocketInputBufferSize());

    factory.getPolicy()
        .setAsyncWriteTimeout(config.getWebsocketAsyncWriteTimeout());
    factory.getPolicy().setIdleTimeout(config.getWebsocketIdleTimeout());

  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.eclipse.jetty.websocket.servlet.WebSocketCreator#createWebSocket(org.
   * eclipse.jetty.websocket.servlet.ServletUpgradeRequest,
   * org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse)
   */
  @Override
  public Object createWebSocket(ServletUpgradeRequest req,
      ServletUpgradeResponse resp) {

    try {
      final URI requestURI = req.getRequestURI();
      final String path = requestURI.getPath();

      /* URL used to connect to websocket backend */
      final String backendURL = getMatchedBackendURL(path);

      /* Upgrade happens here */
      return new ProxyWebSocketAdapter(URI.create(backendURL));
    } catch (final Exception e) {
      LOG.failedCreatingWebSocket(e);
      throw e;
    }
  }

  /**
   * This method looks at the context path and returns the backend websocket
   * url. If websocket url is found it is used as is, or we default to
   * ws://{host}:{port} which might or might not be right.
   * 
   * @param The
   *          context path
   * @return Websocket backend url
   */
  private synchronized String getMatchedBackendURL(final String path) {

    final ServiceRegistry serviceRegistryService = services
        .getService(GatewayServices.SERVICE_REGISTRY_SERVICE);

    final ServiceDefinitionRegistry serviceDefinitionService = services
        .getService(GatewayServices.SERVICE_DEFINITION_REGISTRY);

    /* Filter out the /cluster/topology to get the context we want */
    String[] pathInfo = path.split(REGEX_SPLIT_CONTEXT);

    final ServiceDefEntry entry = serviceDefinitionService
        .getMatchingService(pathInfo[1]);

    if (entry == null) {
      throw new RuntimeException(
          String.format("Cannot find service for the given path: %s", path));
    }

    final File servicesDir = new File(config.getGatewayServicesDir());

    final Set<ServiceDefinition> serviceDefs = ServiceDefinitionsLoader
        .getServiceDefinitions(servicesDir);

    /* URL used to connect to websocket backend */
    String backendURL = urlFromServiceDefinition(serviceDefs,
        serviceRegistryService, entry, path);

    try {

      /* if we do not find websocket URL we default to HTTP */
      if (!StringUtils.contains(backendURL, WEBSOCKET_PROTOCOL_STRING)) {
        URL serviceUrl = new URL(backendURL);

        final StringBuffer backend = new StringBuffer();
        /* Use http host:port if ws url not configured */
        final String protocol = (serviceUrl.getProtocol() == "ws"
            || serviceUrl.getProtocol() == "wss") ? serviceUrl.getProtocol()
                : "ws";
        backend.append(protocol).append("://");
        backend.append(serviceUrl.getHost()).append(":");
        backend.append(serviceUrl.getPort()).append("/");
        ;
        backend.append(serviceUrl.getPath());

        backendURL = backend.toString();
      }

    } catch (MalformedURLException e) {
      LOG.badUrlError(e);
      throw new RuntimeException(e.toString());
    }

    return backendURL;
  }

  private static String urlFromServiceDefinition(
      final Set<ServiceDefinition> serviceDefs,
      final ServiceRegistry serviceRegistry, final ServiceDefEntry entry,
      final String path) {

    final String[] contexts = path.split("/");

    final String serviceURL = serviceRegistry.lookupServiceURL(contexts[2],
        entry.getName().toUpperCase());

    /*
     * we have a match, if ws:// is present it is returend else http:// is
     * returned
     */
    return serviceURL;

  }

}
