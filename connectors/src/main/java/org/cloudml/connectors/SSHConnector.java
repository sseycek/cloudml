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
package org.cloudml.connectors;
import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jcraft.jsch.*;

public class SSHConnector {

    private static final Logger journal = Logger.getLogger(SSHConnector.class.getName());

    String keyPath="";
    String user="";
    String host="";
    String passwd="";

    public SSHConnector(String keyPath, String user, String host){
        this.keyPath=keyPath;
        this.user=user;
        this.host=host;
    }

    public SSHConnector(String keyPath, String user, String host, String passwd){
        this.keyPath=keyPath;
        this.passwd=passwd;
        this.user=user;
        this.host=host;
    }


    public Boolean checkConnectivity(){
        JSch jsch = new JSch();
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        Session session=null;
        Channel channel=null;
        try {
            session = jsch.getSession(user, host, 22);
            if(!keyPath.equals("")){
                if(checkKeyIsFile(keyPath)){
                    jsch.addIdentity(keyPath);
                }else{
                    final byte[] prvkey = keyPath.getBytes(); // Private key must be byte array
                    final byte[] emptyPassPhrase = new byte[0]; // Empty passphrase for now, get real passphrase from MyUserInfo

                    jsch.addIdentity(
                            user,            // String userName
                            prvkey,          // byte[] privateKey
                            null,            // byte[] publicKey
                            emptyPassPhrase  // byte[] passPhrase
                    );
                }
            }else{
                session.setPassword(passwd);
            }

            session.setConfig(config);
            session.connect(0);
            channel = session.openChannel("exec");
            ChannelExec channelExec=((ChannelExec)channel);
        } catch (JSchException e) {
            journal.log(Level.SEVERE, "Connection not yet available or refused");
            return false;
        } finally {
            if(channel != null)
                channel.disconnect();
            if(session != null)
                session.disconnect();
        }
        return true;
    }


    /**
     * Execute a command through SSH on the host specified in the object instance
     * @param command
     */
    public Boolean execCommandSsh(String command){
        journal.log(Level.INFO, ">> executing command...");
        journal.log(Level.INFO, ">> "+ command);
        JSch jsch = new JSch();
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        Session session=null;
        Channel channel=null;
        try {
            session = jsch.getSession(user, host, 22);
            if(!keyPath.equals("")){
                journal.log(Level.INFO, ">> Connection using an ssh key");
                if(checkKeyIsFile(keyPath)){
                    jsch.addIdentity(keyPath);
                }else{
                    final byte[] prvkey = keyPath.getBytes(); // Private key must be byte array
                    final byte[] emptyPassPhrase = new byte[0]; // Empty passphrase for now, get real passphrase from MyUserInfo

                    jsch.addIdentity(
                            user,            // String userName
                            prvkey,          // byte[] privateKey
                            null,            // byte[] publicKey
                            emptyPassPhrase  // byte[] passPhrase
                    );
                }
            }else{
                session.setPassword(passwd);
            }
            session.setConfig(config);
            session.connect(0);

            channel = session.openChannel("exec");
            ChannelExec channelExec=((ChannelExec)channel);
            channelExec.setPty(true);
            channelExec.setCommand(command);
            channelExec.setErrStream(System.err);
            channel.setInputStream(null);
            channel.setExtOutputStream(null);

            InputStream in;
            in = channel.getInputStream();
            InputStream extIn=channel.getExtInputStream();
            channel.connect();
            byte[] tmp=new byte[4096];
            byte[] tmp2=new byte[4096];
            while(true){
                while(in.available()>0 || extIn.available()>0){
                    if(in.available()>0){
                        int i=in.read(tmp, 0, 4096);
                        if(i<0)break;
                        journal.log(Level.INFO, ">> "+ new String(tmp, 0, i));
                    }
                    if(extIn.available()>0){
                        int i=extIn.read(tmp2, 0, 4096);
                        if(i<0)break;
                        journal.log(Level.INFO, ">> "+ new String(tmp2, 0, i));
                    }
                }
                if(channel.isClosed()){
                    journal.log(Level.INFO, ">> exit-status: "+channel.getExitStatus());
                    break;
                }
            }

        } catch (JSchException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            journal.log(Level.SEVERE,"File access error "+e.getMessage());
            return false;
        }finally{
            if(channel != null)
                channel.disconnect();
            if(session != null)
                session.disconnect();
        }
        return true;
    }


    public void upload(String sourcePath, String destinationPath){
        journal.log(Level.INFO, ">> upload command..."+host+" "+ sourcePath + "->" + destinationPath);
        JSch jsch = new JSch();
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");

        try{
            jsch.addIdentity(keyPath);
            Session session = jsch.getSession(user, host, 22);
            session.setConfig(config);
            session.connect(0);

            Channel channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp channelSftp = (ChannelSftp) channel;
            String[] path= destinationPath.split("/");
            String directory="";
            for(int i = 0; i < (path.length - 1); i ++){
                directory += path[i]+"/";
            }
            channelSftp.cd(directory);
            File f1 = new File(sourcePath);
            if(path[path.length-1].equals(""))
                channelSftp.put(new FileInputStream(f1), sourcePath);
            else channelSftp.put(new FileInputStream(f1), path[path.length - 1]);

            channelSftp.exit();
            channel.disconnect();
            session.disconnect();
            // Because we are using the research platform we need to wait a bit
            try{
                Thread.sleep(22000);
            }catch(Exception ee){
                ee.printStackTrace();
            }
        } catch (JSchException e) {
            journal.log(Level.SEVERE, e.getMessage());
        } catch (IOException e) {
            journal.log(Level.SEVERE,"File access error "+e.getMessage());
        } catch (SftpException e) {
            e.printStackTrace();
        }
    }

    private Boolean checkKeyIsFile(String key){
        File file = new File(key);
        if (file.exists()){
            return true;
        }
        return false;
    }

}
