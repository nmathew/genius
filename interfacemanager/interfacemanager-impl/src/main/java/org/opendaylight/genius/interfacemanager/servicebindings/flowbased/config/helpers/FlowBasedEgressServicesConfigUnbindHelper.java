/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.interfacemanager.servicebindings.flowbased.config.helpers;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.interfacemanager.InterfacemgrProvider;
import org.opendaylight.genius.interfacemanager.commons.InterfaceManagerCommonUtils;
import org.opendaylight.genius.interfacemanager.servicebindings.flowbased.config.factory.FlowBasedServicesConfigRemovable;
import org.opendaylight.genius.interfacemanager.servicebindings.flowbased.utilities.FlowBasedServicesUtils;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowBasedEgressServicesConfigUnbindHelper implements FlowBasedServicesConfigRemovable {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedEgressServicesConfigUnbindHelper.class);

    private final InterfacemgrProvider interfaceMgrProvider;
    private static volatile FlowBasedServicesConfigRemovable flowBasedEgressServicesRemovable;

    private FlowBasedEgressServicesConfigUnbindHelper(InterfacemgrProvider interfaceMgrProvider) {
        this.interfaceMgrProvider = interfaceMgrProvider;
    }

    public static void intitializeFlowBasedEgressServicesConfigRemoveHelper(InterfacemgrProvider interfaceMgrProvider) {
        if (flowBasedEgressServicesRemovable == null) {
            synchronized (FlowBasedEgressServicesConfigUnbindHelper.class) {
                if (flowBasedEgressServicesRemovable == null) {
                    flowBasedEgressServicesRemovable = new FlowBasedEgressServicesConfigUnbindHelper(
                            interfaceMgrProvider);
                }
            }
        }
    }

    public static FlowBasedServicesConfigRemovable getFlowBasedEgressServicesRemoveHelper() {
        if (flowBasedEgressServicesRemovable == null) {
            LOG.error("FlowBasedIngressBindHelper`` is not initialized");
        }
        return flowBasedEgressServicesRemovable;
    }

    @Override
    public List<ListenableFuture<Void>> unbindService(InstanceIdentifier<BoundServices> instanceIdentifier,
            BoundServices boundServiceOld) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        DataBroker dataBroker = interfaceMgrProvider.getDataBroker();
        String interfaceName =
                InstanceIdentifier.keyOf(instanceIdentifier.firstIdentifierOf(ServicesInfo.class)).getInterfaceName();
        Class<? extends ServiceModeBase> serviceMode = InstanceIdentifier
                .keyOf(instanceIdentifier.firstIdentifierOf(ServicesInfo.class)).getServiceMode();

        // Get the Parent ServiceInfo
        ServicesInfo servicesInfo = FlowBasedServicesUtils.getServicesInfoForInterface(interfaceName, serviceMode,
                dataBroker);
        if (servicesInfo == null) {
            LOG.error("Reached Impossible part in the code for bound service: {}", boundServiceOld);
            return futures;
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
            ifState = InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName, dataBroker);
        if (ifState == null) {
            LOG.info("Interface not operational, not unbinding Service for Interface: {}", interfaceName);
            return futures;
        }
        List<BoundServices> boundServices = servicesInfo.getBoundServices();

        if (L2vlan.class.equals(ifState.getType())
            || Tunnel.class.equals(ifState.getType())) {
            unbindService(boundServiceOld, boundServices, ifState, dataBroker);
        }
        return futures;
    }

    private static List<ListenableFuture<Void>> unbindService(BoundServices boundServiceOld,
            List<BoundServices> boundServices,
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .Interface ifaceState, DataBroker dataBroker) {
        LOG.info("unbinding egress service {} for interface: {}", boundServiceOld.getServiceName(), ifaceState
            .getName());
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        Interface iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(ifaceState.getName(), dataBroker);
        BigInteger dpId = FlowBasedServicesUtils.getDpnIdFromInterface(ifaceState);
        if (boundServices.isEmpty()) {
            // Remove default entry from Lport Dispatcher Table.
            FlowBasedServicesUtils.removeEgressDispatcherFlows(dpId, ifaceState.getName(), boundServiceOld, tx,
                    NwConstants.DEFAULT_SERVICE_INDEX);
            if (tx != null) {
                futures.add(tx.submit());
            }
            return futures;
        }
        BoundServices[] highLow = FlowBasedServicesUtils.getHighAndLowPriorityService(boundServices, boundServiceOld);
        BoundServices low = highLow[0];
        BoundServices high = highLow[1];
        // This means the one removed was the highest priority service
        if (high == null) {
            LOG.trace("Deleting egress dispatcher table entry for service {}, match service index {}", boundServiceOld,
                NwConstants.DEFAULT_SERVICE_INDEX);
            FlowBasedServicesUtils.removeEgressDispatcherFlows(dpId, ifaceState.getName(), boundServiceOld, tx,
                    NwConstants.DEFAULT_SERVICE_INDEX);
            if (low != null) {
                //delete the lower services flow entry.
                LOG.trace("Deleting egress dispatcher table entry for lower service {}, match service index {}", low,
                    low.getServicePriority());
                FlowBasedServicesUtils.removeEgressDispatcherFlows(dpId, ifaceState.getName(), low, tx,
                        low.getServicePriority());
                BoundServices lower = FlowBasedServicesUtils.getHighAndLowPriorityService(boundServices, low)[0];
                short lowerServiceIndex = (short) (lower != null ? lower.getServicePriority()
                        : low.getServicePriority() + 1);
                LOG.trace("Installing new egress dispatcher table entry for lower service {}, match service index {}," +
                        " update service index {}",
                    low, NwConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex);
                FlowBasedServicesUtils.installEgressDispatcherFlows(dpId, low, ifaceState.getName(), tx,
                        ifaceState.getIfIndex(), NwConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex, iface);
            }
        } else {
            LOG.trace("Deleting egress dispatcher table entry for service {}, match service index {}", boundServiceOld,
                boundServiceOld.getServicePriority());
            FlowBasedServicesUtils.removeEgressDispatcherFlows(dpId, ifaceState.getName(), boundServiceOld, tx,
                    boundServiceOld.getServicePriority());
            short lowerServiceIndex = (short) (low != null ? low.getServicePriority()
                    : boundServiceOld.getServicePriority() + 1);
            BoundServices highest = FlowBasedServicesUtils.getHighestPriorityService(boundServices);
            if (high.equals(highest)) {
                LOG.trace("Update the existing higher service {}, match service index {}, update service index {}",
                    high, NwConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex);
                FlowBasedServicesUtils.installEgressDispatcherFlows(dpId, high, ifaceState.getName(), tx,
                        ifaceState.getIfIndex(), NwConstants.DEFAULT_SERVICE_INDEX, lowerServiceIndex, iface);
            } else {
                LOG.trace("Update the existing higher service {}, match service index {}, update service index {}",
                    high, high.getServicePriority(), lowerServiceIndex);
                FlowBasedServicesUtils.installEgressDispatcherFlows(dpId, high, ifaceState.getName(), tx,
                        ifaceState.getIfIndex(), high.getServicePriority(), lowerServiceIndex, iface);
            }
        }
        futures.add(tx.submit());
        return futures;
    }

    private static List<ListenableFuture<Void>> unbindServiceOnTunnel(BoundServices boundServiceOld,
            List<BoundServices> boundServices,
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .Interface ifState, DataBroker dataBroker) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();

        // FIXME : not yet supported
        return futures;
    }
}
