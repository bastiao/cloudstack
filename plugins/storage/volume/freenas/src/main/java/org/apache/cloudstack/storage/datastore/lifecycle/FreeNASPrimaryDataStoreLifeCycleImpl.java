/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.datastore.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePoolAutomation;
import org.apache.cloudstack.engine.subsystem.api.storage.ClusterScope;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.engine.subsystem.api.storage.HostScope;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreLifeCycle;
import org.apache.cloudstack.engine.subsystem.api.storage.ZoneScope;
import org.apache.cloudstack.storage.command.AttachPrimaryDataStoreCmd;
import org.apache.cloudstack.storage.command.CreatePrimaryDataStoreCmd;
import org.apache.cloudstack.storage.datastore.PrimaryDataStoreProviderManager;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.volume.datastore.PrimaryDataStoreHelper;

import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolStatus;
import org.apache.log4j.Logger;

public class FreeNASPrimaryDataStoreLifeCycleImpl implements PrimaryDataStoreLifeCycle {

    private static final Logger logger =
            Logger.getLogger(FreeNASPrimaryDataStoreLifeCycleImpl.class);

    @Inject
    EndPointSelector selector;
    @Inject
    PrimaryDataStoreDao dataStoreDao;
    @Inject
    HostDao hostDao;

    @Inject
    private PrimaryDataStoreHelper dataStoreHelper;
    @Inject
    private ResourceManager _resourceMgr;
    @Inject
    StorageManager _storageMgr;
    @Inject
    private StoragePoolAutomation storagePoolAutomation;

    @Inject
    PrimaryDataStoreHelper primaryStoreHelper;
    @Inject
    PrimaryDataStoreProviderManager providerMgr;

    @Inject
    private DataCenterDao zoneDao;


    public FreeNASPrimaryDataStoreLifeCycleImpl() {
    }

    @Override
    public DataStore initialize(Map<String, Object> dsInfos) {


        String url = (String) dsInfos.get("url");
        Long zoneId = (Long) dsInfos.get("zoneId");
        String storagePoolName = (String) dsInfos.get("name");
        String providerName = (String) dsInfos.get("providerName");
        Long capacityBytes = (Long)dsInfos.get("capacityBytes");
        Long capacityIops = (Long)dsInfos.get("capacityIops");
        String tags = (String)dsInfos.get("tags");
        Map<String, String> details = (Map<String, String>) dsInfos.get("details");
        DataCenterVO zone = zoneDao.findById(zoneId);


        DataStore store = primaryStoreHelper.createPrimaryDataStore(null);
        return providerMgr.getPrimaryDataStore(store.getId());
    }

    protected void attachCluster(DataStore store) {
        // send down AttachPrimaryDataStoreCmd command to all the hosts in the
        // cluster
        List<EndPoint> endPoints = selector.selectAll(store);
        CreatePrimaryDataStoreCmd createCmd = new CreatePrimaryDataStoreCmd(store.getUri());
        EndPoint ep = endPoints.get(0);
        HostVO host = hostDao.findById(ep.getId());
        if (host.getHypervisorType() == HypervisorType.XenServer) {
            ep.sendMessage(createCmd);
        }

        endPoints.get(0).sendMessage(createCmd);
        AttachPrimaryDataStoreCmd cmd = new AttachPrimaryDataStoreCmd(store.getUri());
        for (EndPoint endp : endPoints) {
            endp.sendMessage(cmd);
        }



    }

    @Override
    public boolean attachCluster(DataStore dataStore, ClusterScope scope) {
        StoragePoolVO dataStoreVO = dataStoreDao.findById(dataStore.getId());
        dataStoreVO.setDataCenterId(scope.getZoneId());
        dataStoreVO.setPodId(scope.getPodId());
        dataStoreVO.setClusterId(scope.getScopeId());
        dataStoreVO.setStatus(StoragePoolStatus.Attaching);
        dataStoreVO.setScope(scope.getScopeType());
        dataStoreDao.update(dataStoreVO.getId(), dataStoreVO);

        attachCluster(dataStore);

        dataStoreVO = dataStoreDao.findById(dataStore.getId());
        dataStoreVO.setStatus(StoragePoolStatus.Up);
        dataStoreDao.update(dataStoreVO.getId(), dataStoreVO);

        return true;
    }

    @Override
    public boolean attachZone(DataStore dataStore, ZoneScope scope, HypervisorType hypervisorType) {

        dataStoreHelper.attachZone(dataStore);

        List<HostVO> xenServerHosts = _resourceMgr.listAllUpAndEnabledHostsInOneZoneByHypervisor(Hypervisor.HypervisorType.XenServer, scope.getScopeId());
        List<HostVO> vmWareServerHosts = _resourceMgr.listAllUpAndEnabledHostsInOneZoneByHypervisor(Hypervisor.HypervisorType.VMware, scope.getScopeId());
        List<HostVO> kvmHosts = _resourceMgr.listAllUpAndEnabledHostsInOneZoneByHypervisor(Hypervisor.HypervisorType.KVM, scope.getScopeId());
        List<HostVO> hosts = new ArrayList<HostVO>();

        hosts.addAll(xenServerHosts);
        hosts.addAll(vmWareServerHosts);
        hosts.addAll(kvmHosts);


        for (HostVO host : hosts) {
            try {
                _storageMgr.connectHostToSharedPool(host.getId(), dataStore.getId());
            } catch (Exception e) {
                logger.warn("Unable to establish a connection between " + host + " and " + dataStore, e);
            }
        }

        return true;


    }

    @Override
    public boolean attachHost(DataStore store, HostScope scope, StoragePoolInfo existingInfo) {
        return false;
    }

    @Override
    public boolean maintain(DataStore store) {
        return false;
    }

    @Override
    public boolean cancelMaintain(DataStore store) {
        return false;
    }

    @Override
    public boolean deleteDataStore(DataStore store) {
        return false;
    }

    /* (non-Javadoc)
     * @see org.apache.cloudstack.engine.subsystem.api.storage.DataStoreLifeCycle#migrateToObjectStore(org.apache.cloudstack.engine.subsystem.api.storage.DataStore)
     */
    @Override
    public boolean migrateToObjectStore(DataStore store) {
        return false;
    }

    @Override
    public void updateStoragePool(StoragePool storagePool, Map<String, String> details) {
    }

    @Override
    public void enableStoragePool(DataStore store) {
    }

    @Override
    public void disableStoragePool(DataStore store) {
    }
}
