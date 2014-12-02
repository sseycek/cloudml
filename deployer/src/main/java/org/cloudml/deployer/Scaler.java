package org.cloudml.deployer;

import org.cloudml.connectors.Connector;
import org.cloudml.connectors.ConnectorFactory;
import org.cloudml.core.*;
import org.cloudml.core.actions.StandardLibrary;
import org.cloudml.core.collections.ProvidedExecutionPlatformGroup;
import org.cloudml.core.collections.RelationshipInstanceGroup;
import org.cloudml.mrt.Coordinator;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ferrynico on 02/12/2014.
 */
public class Scaler {

    private static final Logger journal = Logger.getLogger(Scaler.class.getName());

    private Deployment currentModel;
    private Coordinator coordinator;
    StandardLibrary lib = new StandardLibrary();
    VMInstance ci;
    private CloudAppDeployer dep;

    public Scaler(Deployment currentModel, Coordinator coordinator, CloudAppDeployer dep){
        this.currentModel=currentModel;
        this.coordinator=coordinator;
        this.dep=dep;
    }


    private VM createNewInstanceOfVMFromImage(VMInstance vmi){
        VM existingVM=vmi.asExternal().asVM().getType();
        VM v=currentModel.getComponents().onlyVMs().firstNamed(existingVM.getName()+"-fromImage");
        if(v == null){//in case a type for the snapshot has already been created
            String name=lib.createUniqueComponentInstanceName(currentModel,existingVM);
            v=new VM(name+"-fromImage",existingVM.getProvider());
            v.setGroupName(existingVM.getGroupName());
            v.setRegion(existingVM.getRegion());
            v.setImageId("tempID");
            v.setLocation(existingVM.getLocation());
            v.setMinRam(existingVM.getMinRam());
            v.setMinCores(existingVM.getMinCores());
            v.setMinStorage(existingVM.getMinStorage());
            v.setSecurityGroup(existingVM.getSecurityGroup());
            v.setSshKey(existingVM.getSshKey());
            v.setPrivateKey(existingVM.getPrivateKey());
            v.setProvider(existingVM.getProvider());
            ProvidedExecutionPlatformGroup pepg=new ProvidedExecutionPlatformGroup();
            for(ProvidedExecutionPlatform pep: existingVM.getProvidedExecutionPlatforms()){
                ArrayList<Property> pg=new ArrayList<Property>();
                for(Property property: pep.getOffers()){
                    Property prop=new Property(property.getName());
                    prop.setValue(property.getValue());
                    pg.add(prop);
                }
                ProvidedExecutionPlatform p =new ProvidedExecutionPlatform(name+"-"+pep.getName(),pg);
                pepg.add(p);
            }
            v.setProvidedExecutionPlatforms(pepg.toList());
            currentModel.getComponents().add(v);
        }
        ci=lib.provision(currentModel,v).asExternal().asVM();
        return v;
    }

    private Map<InternalComponentInstance, InternalComponentInstance> duplicateHostedGraph(VMInstance vmiSource,VMInstance vmiDestination){
        //InternalComponentInstanceGroup icig= currentModel.getComponentInstances().onlyInternals().hostedOn(vmiSource);
        StandardLibrary lib=new StandardLibrary();
        return lib.replicateSubGraph(currentModel, vmiSource, vmiDestination);
    }

    private void manageDuplicatedRelationships(RelationshipInstanceGroup rig, Set<ComponentInstance> listOfAllComponentImpacted){
        if(rig != null){
            dep.configureWithRelationships(rig);
            for(RelationshipInstance ri: rig){
                listOfAllComponentImpacted.add(ri.getClientComponent());
                listOfAllComponentImpacted.add(ri.getServerComponent());
            }
        }
    }


    private void configureBindingOfImpactedComponents(Set<ComponentInstance> listOfAllComponentImpacted, Map<InternalComponentInstance, InternalComponentInstance> duplicatedGraph){
        for(InternalComponentInstance ici: duplicatedGraph.values()){
            for(ProvidedPortInstance ppi: ici.getProvidedPorts()){
                RelationshipInstanceGroup rig=currentModel.getRelationshipInstances().whereEitherEndIs(ppi);
                manageDuplicatedRelationships(rig, listOfAllComponentImpacted);
            }
            for(RequiredPortInstance rpi: ici.getRequiredPorts()){
                RelationshipInstanceGroup rig=currentModel.getRelationshipInstances().whereEitherEndIs(rpi);
                manageDuplicatedRelationships(rig, listOfAllComponentImpacted);
            }
        }
    }

    private void configureImpactedComponents(Set<ComponentInstance> listOfAllComponentImpacted, Map<InternalComponentInstance, InternalComponentInstance> duplicatedGraph){
        for(ComponentInstance ici: listOfAllComponentImpacted){
            coordinator.updateStatusInternalComponent(ici.getName(), InternalComponentInstance.State.INSTALLED.toString(), CloudAppDeployer.class.getName());
            if(ici.isInternal()){
                Provider p=ici.asInternal().externalHost().asVM().getType().getProvider();
                Connector c2=ConnectorFactory.createIaaSConnector(p);
                for(Resource r: ici.getType().getResources()){
                    dep.configure(c2, ci.getType(), ici.asInternal().externalHost().asVM(), r.getConfigureCommand(),false);
                }
                c2.closeConnection();
            }
            coordinator.updateStatusInternalComponent(ici.getName(), InternalComponentInstance.State.CONFIGURED.toString(), CloudAppDeployer.class.getName());
        }
    }

    private void startImpactedComponents(Set<ComponentInstance> listOfAllComponentImpacted, Map<InternalComponentInstance, InternalComponentInstance> duplicatedGraph){
        for(ComponentInstance ici: listOfAllComponentImpacted){
            if(ici.isInternal()){
                Provider p=ici.asInternal().externalHost().asVM().getType().getProvider();
                Connector c2=ConnectorFactory.createIaaSConnector(p);
                for(Resource r: ici.getType().getResources()){
                    dep.start(c2,ci.getType(),ici.asInternal().externalHost().asVM(),r.getStartCommand());
                }
                c2.closeConnection();
            }
            coordinator.updateStatusInternalComponent(ici.getName(), InternalComponentInstance.State.RUNNING.toString(), CloudAppDeployer.class.getName());
        }

        for(InternalComponentInstance ici: duplicatedGraph.values()){
            coordinator.updateStatusInternalComponent(ici.getName(), InternalComponentInstance.State.RUNNING.toString(), CloudAppDeployer.class.getName());
        }
    }


    /**
     * Method to scale out a VM within the same provider
     * Create a snapshot of the VM and then configure the bindings
     *
     * @param vmi an instance of VM
     */
    public void scaleOut(VMInstance vmi) {
        Deployment tmp=currentModel.clone();

        Connector c = ConnectorFactory.createIaaSConnector(vmi.getType().getProvider());

        //1. instantiate the new VM using the newly created snapshot
        VM v=createNewInstanceOfVMFromImage(vmi);

        //2. update the deployment model by cloning the PaaS and SaaS hosted on the replicated VM
        Map<InternalComponentInstance, InternalComponentInstance> duplicatedGraph=duplicateHostedGraph(vmi, ci);

        //3. For synchronization purpose with provision once the model has been fully updated
        String ID=c.createImage(vmi);
        c.closeConnection();
        v.setImageId(ID);

        Connector c2=ConnectorFactory.createIaaSConnector(v.getProvider());
        HashMap<String,String> result=c2.createInstance(ci);

        c2.closeConnection();
        coordinator.updateStatusInternalComponent(ci.getName(), result.get("status"), CloudAppDeployer.class.getName());
        coordinator.updateStatus(vmi.getName(), ComponentInstance.State.RUNNING.toString(), CloudAppDeployer.class.getName());
        coordinator.updateIP(ci.getName(),result.get("publicAddress"),CloudAppDeployer.class.getName());

        //4. configure the new VM
        //execute the configuration bindings
        Set<ComponentInstance> listOfAllComponentImpacted= new HashSet<ComponentInstance>();
        configureBindingOfImpactedComponents(listOfAllComponentImpacted,duplicatedGraph);

        //execute configure commands on the components
        configureImpactedComponents(listOfAllComponentImpacted,duplicatedGraph);

        //execute start commands on the components
        startImpactedComponents(listOfAllComponentImpacted,duplicatedGraph);

        journal.log(Level.INFO, ">> Scaling completed!");
    }

    /**
     * To scale our a VM on another provider (kind of bursting)
     *
     * @param vmi      the vm instance to scale out
     * @param provider the provider where we want to burst
     */
    public void scaleOut(VMInstance vmi, Provider provider) {
        Connector c = ConnectorFactory.createIaaSConnector(provider);
        StandardLibrary lib = new StandardLibrary();

        //need to clone the model
        Deployment targetModel=currentModel.clone();

        VM existingVM=vmi.asExternal().asVM().getType();
        VM v=currentModel.getComponents().onlyVMs().firstNamed(existingVM.getName()+"-scaled");
        if(v == null){//in case a type for the snapshot has already been created
            String name=lib.createUniqueComponentInstanceName(targetModel,existingVM);
            v=new VM(name+"-scaled",provider);
            v.setGroupName(existingVM.getGroupName());
            v.setRegion(existingVM.getRegion());
            v.setImageId(existingVM.getImageId());
            v.setLocation(existingVM.getLocation());
            v.setMinRam(existingVM.getMinRam());
            v.setMinCores(existingVM.getMinCores());
            v.setMinStorage(existingVM.getMinStorage());
            v.setSecurityGroup(existingVM.getSecurityGroup());
            v.setSshKey(existingVM.getSshKey());
            v.setPrivateKey(existingVM.getPrivateKey());
            v.setProvider(existingVM.getProvider());
            ProvidedExecutionPlatformGroup pepg=new ProvidedExecutionPlatformGroup();
            for(ProvidedExecutionPlatform pep: existingVM.getProvidedExecutionPlatforms()){
                ArrayList<Property> pg=new ArrayList<Property>();
                for(Property property: pep.getOffers()){
                    Property prop=new Property(property.getName());
                    prop.setValue(property.getValue());
                    pg.add(prop);
                }
                ProvidedExecutionPlatform p =new ProvidedExecutionPlatform(name+"-"+pep.getName(),pg);
                pepg.add(p);
            }
            v.setProvidedExecutionPlatforms(pepg.toList());
            targetModel.getComponents().add(v);
        }


        //2. update the deployment model by cloning the PaaS and SaaS hosted on the replicated VM
        Map<InternalComponentInstance, InternalComponentInstance> duplicatedGraph=duplicateHostedGraph(vmi, ci);

        dep.deploy(targetModel);
    }

}
