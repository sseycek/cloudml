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
package org.cloudml.facade.commands;

import org.cloudml.mrt.Coordinator;

/**
 * Interface of object able to implements the commands supported by the CloudML
 * facade
 */
public interface CommandHandler {

    public void handle(AnalyseRobustness command);
    
    public void handle(StartComponent command);

    public void handle(StopComponent command);

    public void handle(Attach command);

    public void handle(Detach command);

    public void handle(Install command);

    public void handle(Uninstall command);

    public void handle(Instantiate command);

    public void handle(Destroy command);

    public void handle(Upload command);

    public void handle(LoadDeployment command);

    public void handle(StoreDeployment command);

    public void handle(Deploy command);

    public void handle(ListComponents command);
    
    public void handle(ListComponentInstances command);
    
    public void handle(ViewComponent command);

    public void handle(ViewComponentInstance command);
    
    public void handle(LoadCredentials command);
    
    public void handle(StoreCredentials command);
    
    public void handle(ShotImage command);

    public void handle(Snapshot command);

    public void handle(ScaleOut command);

    public void handle(Image command);

    public void handle(Reset command);
    
    public void handle(ValidateCommand command);

    public void handle(DebugMode command);

    public void handle(Burst command);

    public void handle(offlineMigration command);

    public void handle(onlineMigration command);

    public void handle(GetDeployment command);

    public Coordinator getCoordinator();
    
}