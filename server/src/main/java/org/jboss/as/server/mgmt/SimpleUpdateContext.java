/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.mgmt;

import org.jboss.as.model.UpdateContext;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;

/**
 * Simple implementation of {@link UpdateContext}.
 *
 * @author Brian Stansberry
 */
class SimpleUpdateContext implements UpdateContext {

    private final ServiceContainer serviceContainer;
    private final ServiceTarget serviceTarget;

    SimpleUpdateContext(final ServiceContainer serviceContainer, final ServiceTarget serviceTarget) {
        if (serviceContainer == null)
            throw new IllegalArgumentException("serviceContainer is null");
        if (serviceTarget == null)
            throw new IllegalArgumentException("serviceTarget is null");

        this.serviceContainer = serviceContainer;
        this.serviceTarget = serviceTarget;
    }

    @Override
    public ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    @Override
    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

}
