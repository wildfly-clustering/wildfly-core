/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.client.impl;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;

/**
 * {@link ModelControllerClient} based on a Remoting {@link Endpoint}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class RemotingModelControllerClient extends AbstractModelControllerClient {

    private Endpoint endpoint;
    private ManagementClientChannelStrategy strategy;
    private boolean closed;

    private final ManagementChannelHandler channelAssociation;
    private final ModelControllerClientConfiguration clientConfiguration;
    private final StackTraceElement[] allocationStackTrace;

    public RemotingModelControllerClient(final ModelControllerClientConfiguration configuration) {
        this.channelAssociation = new ManagementChannelHandler(new ManagementClientChannelStrategy() {
            @Override
            public Channel getChannel() throws IOException {
                return getOrCreateChannel();
            }

            @Override
            public synchronized void close() throws IOException {
                //
            }
        }, configuration.getExecutor(), this);
        this.clientConfiguration = configuration;
        this.allocationStackTrace = Thread.currentThread().getStackTrace();
    }

    @Override
    protected ManagementChannelAssociation getChannelAssociation() throws IOException {
        return channelAssociation;
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if(closed) {
                return;
            }
            closed = true;
            // Don't allow any new request
            channelAssociation.shutdown();
            // First close the channel and connection
            if (strategy != null) {
                StreamUtils.safeClose(strategy);
                strategy = null;
            }
            // Then the endpoint
            final Endpoint endpoint = this.endpoint;
            if (endpoint != null) {
                this.endpoint = null;
                try {
                    endpoint.closeAsync();
                } catch (UnsupportedOperationException ignored) {
                }
            }
            // Cancel all still active operations
            channelAssociation.shutdownNow();
            try {
                channelAssociation.awaitCompletion(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            } finally {
                StreamUtils.safeClose(clientConfiguration);
            }
            // Per WFCORE-1573 remoting endpoints should be closed asynchronously, however consumers of this client
            // likely need to wait until the endpoints are fully shutdown.
            if (endpoint != null) try {
                endpoint.awaitClosed();
            } catch (InterruptedException e) {
                final InterruptedIOException cause = new InterruptedIOException(e.getLocalizedMessage());
                cause.initCause(e);
                throw cause;
            }
        }
    }

    protected synchronized Channel getOrCreateChannel() throws IOException {
        if (closed) {
            throw ControllerClientLogger.ROOT_LOGGER.objectIsClosed(ModelControllerClient.class.getSimpleName());
        }
        if (strategy == null) {
            try {

                endpoint = Endpoint.builder().setEndpointName("management-client").build();

                final ProtocolConnectionConfiguration configuration = ProtocolConfigurationFactory.create(clientConfiguration, endpoint);

                strategy = ManagementClientChannelStrategy.create(configuration, channelAssociation, clientConfiguration.getCallbackHandler(),
                        clientConfiguration.getSaslOptions(), clientConfiguration.getSSLContext(),
                        new CloseHandler<Channel>() {
                    @Override
                    public void handleClose(final Channel closed, final IOException exception) {
                        channelAssociation.handleChannelClosed(closed, exception);
                    }
                });
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return strategy.getChannel();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if(! closed) {
                // Create the leak description
                final Throwable t = ControllerClientLogger.ROOT_LOGGER.controllerClientNotClosed();
                t.setStackTrace(allocationStackTrace);
                ControllerClientLogger.ROOT_LOGGER.leakedControllerClient(t);
                // Close
                StreamUtils.safeClose(this);
            }
        } finally {
            super.finalize();
        }
    }

}
