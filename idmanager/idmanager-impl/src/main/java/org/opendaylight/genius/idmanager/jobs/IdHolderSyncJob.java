/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.idmanager.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.idmanager.IdHolder;
import org.opendaylight.genius.idmanager.IdUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPoolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPoolKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class IdHolderSyncJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(IdHolderSyncJob.class);
    private String localPoolName;
    private IdHolder idHolder;
    private DataBroker broker;

    public IdHolderSyncJob(String localPoolName, IdHolder idHolder,
            DataBroker broker) {
        super();
        this.localPoolName = localPoolName;
        this.idHolder = idHolder;
        this.broker = broker;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        ArrayList<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
            IdPoolBuilder idPool = new IdPoolBuilder().setKey(new IdPoolKey(localPoolName));
            idHolder.refreshDataStore(idPool);
            InstanceIdentifier<IdPool> localPoolInstanceIdentifier = IdUtils.getIdPoolInstance(localPoolName);
            WriteTransaction tx = broker.newWriteOnlyTransaction();
            tx.merge(LogicalDatastoreType.CONFIGURATION, localPoolInstanceIdentifier, idPool.build(), true);
            IdUtils.incrementPoolUpdatedMap(localPoolName);
            futures.add(tx.submit());
            if (LOG.isDebugEnabled()) {
                LOG.debug("IdHolder synced {}", idHolder);
            }
            return futures;
    }
}