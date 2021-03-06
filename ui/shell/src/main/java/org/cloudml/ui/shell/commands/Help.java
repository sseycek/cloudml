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
/*
 */

package org.cloudml.ui.shell.commands;

/**
 * The help command which display the help of the shell
 */
public class Help extends ShellCommand {

    private final String subject;

    public Help(String subject) {
        super(VOLATILE);
        this.subject = subject;
    }
    
    public boolean hasSubject() {
        return subject != null;
    }

    @Override
    public void execute(ShellCommandHandler handler) {
        handler.help(subject);
    }

    @Override
    public String toString() {
        if (hasSubject()) {
            return String.format("help \"%s\"", subject);
        }
        return "help";
    }
    
    
    
    
}
