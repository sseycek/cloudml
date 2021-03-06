/**
 * This file is part of CloudML [ http://cloudml.org ]
 *
 * Copyright (C) 2012 - SINTEF ICT
 * Contact: Franck Chauvel <franck.chauvel@sintef.no>
 *
 * Module: root
 *
 * CloudML is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * CloudML is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with CloudML. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.cloudml.core;

import java.util.ArrayList;
import java.util.List;
import org.cloudml.core.collections.RelationshipInstanceGroup;
import org.cloudml.core.validation.Report;
import org.cloudml.core.visitors.Visitor;

public class ProvidedPortInstance extends PortInstance<ProvidedPort> {


    public ProvidedPortInstance(String name, ProvidedPort type) {
        super(name, type);
    }

    public List<RequiredPortInstance> findClients() {
        final RelationshipInstanceGroup relationships = getDeployment().getRelationshipInstances().whereEitherEndIs(this);
        if (relationships.isEmpty()) {
            final String message = String.format("provided port '%s' is not yet bound to any client", getQualifiedName());
            throw new IllegalArgumentException(message);
        }
        final List<RequiredPortInstance> clients = new ArrayList<RequiredPortInstance>();
        for (RelationshipInstance relationship : relationships) {
            clients.add(relationship.getRequiredEnd());
        }
        return clients;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitProvidedPortInstance(this);
    }

    @Override
    public void validate(Report report) {
        if (getOwner().isUndefined()) {
            report.addError("Server port instance has no owner ('null' found)");
        }
    }

    @Override
    public String toString() {
        return "ServerPortInstance " + getQualifiedName();

    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ProvidedPortInstance) {
            ProvidedPortInstance otherNode = (ProvidedPortInstance) other;
            return getName().equals(otherNode.getName()) && getOwner().equals(otherNode.getOwner());
        }
        return false;
    }
}
