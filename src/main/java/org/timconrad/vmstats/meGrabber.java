package org.timconrad.vmstats;
/*
 * Copyright 2012 Tim Conrad - tim@timconrad.org
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

// this is a Producer in the arrangement
// this goes and gets a list of managed entities to send to statsGrabber

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;

class meGrabber implements Runnable{
	
	private final BlockingQueue<Object> vm_mob_queue;
	private final BlockingQueue<Object> esx_mob_queue;
    private final BlockingQueue<Object> sender;
    private HashMap<String, String> clusterMap;
	private final Hashtable<String, String> appConfig;
	private final ServiceInstance si;
	private static final Logger logger = LoggerFactory.getLogger(meGrabber.class);
    private volatile boolean cancelled;

	public meGrabber(ServiceInstance si, 
			BlockingQueue<Object> vm_mob_queue,
            BlockingQueue<Object> esx_mob_queue, 
            Hashtable<String, String> appConfig, 
            BlockingQueue<Object> sender) {
		this.vm_mob_queue = vm_mob_queue;
		this.esx_mob_queue = esx_mob_queue;
		this.appConfig = appConfig;
		this.si = si;
        this.sender = sender;
        this.clusterMap = new HashMap<String, String>();
	}
    public void cancel() {
        this.cancelled = true;
    }

	public void run() {
		try {
			while(!cancelled) {
				long start = System.currentTimeMillis();
				ManagedEntity[] vms = null;
				ManagedEntity[] clusters = null;
				clusterMap = new HashMap<String, String>();
                for(int i = 0; i < Integer.parseInt(appConfig.get("MAX_VMSTAT_THREADS")); i++) {
                    String stats = "start_stats";
                    this.vm_mob_queue.put(stats);
                }
				try {
					// VirtualMachine NOT VirtualMachines
					// get a list of virtual machines
					vms = new InventoryNavigator(this.si.getRootFolder()).searchManagedEntities("VirtualMachine");
					clusters = new InventoryNavigator(si.getRootFolder()).searchManagedEntities(new String[][] { new String[] { "ClusterComputeResource", "host", "name",}, }, true);
				} catch(RemoteException e) {
					e.getStackTrace();
					logger.info("vm grab exception: " + e);
                    System.exit(200);
				}
				
				
				for (ManagedEntity cluster : clusters) {
					String name = cluster.getName();
					ManagedObjectReference[] hosts = (ManagedObjectReference[]) cluster.getPropertyByPath("host");
					if (hosts == null) continue;
					for (ManagedObjectReference host : hosts) {
						clusterMap.put(host.val, name);
					}
				}
				
				if (vms != null) {
                    logger.info("Found " + vms.length + " Virtual Machines");
					// if they're not null, loop through them and send them to the
					// statsGrabber thread to get stats for.
                    for (ManagedEntity vm : vms) {
                        if (vm != null) {
                            String cluster = "none";
                            ManagedObjectReference host = (ManagedObjectReference) vm.getPropertyByPath("runtime.host");
                            if (clusterMap.containsKey(host.val)) {
                            	cluster = clusterMap.get(host.val);
                            }
                            this.vm_mob_queue.put(new Object[] { vm, cluster });
                        }
                    }
				}else{
                    logger.info("Found null virtual machines. Something's probably wrong.");
                }
				long vm_stop = System.currentTimeMillis();
				long vm_loop_took = vm_stop - start;
				logger.debug("meGrabber VM loop took " + vm_loop_took + "ms.");

                for(int i = 0; i < Integer.parseInt(appConfig.get("MAX_VMSTAT_THREADS")); i++) {
                    String stats = "stop_stats";
                    this.vm_mob_queue.put(stats);
                }

                long esx_loop_took = 0;
				String graphEsx = this.appConfig.get("graphEsx");
				if (graphEsx.contains("true")) {
                    for(int i = 0; i < Integer.parseInt(appConfig.get("MAX_ESXSTAT_THREADS")); i++) {
                        String stats = "start_stats";
                        this.esx_mob_queue.put(stats);
                    }
					ManagedEntity[] esx = null;
					// get the esx nodes, aka HostSystem
					try {
						esx = new InventoryNavigator(this.si.getRootFolder()).searchManagedEntities("HostSystem");
					} catch(RemoteException e) {
						e.getStackTrace();
						logger.info("vm grab exception: " + e);
                        System.exit(201);
					}
					
					logger.info("Found " + esx.length + " ESX Hosts");
					if (esx != null) {
						// if they're not null, loop through them and send them to the
						// statsGrabber thread to get stats for.
                        for (ManagedEntity anEsx : esx) {
                            if (anEsx != null) {
                            	String cluster = "none";
                            	
                            	String id = anEsx.getMOR().val;
                            	if (clusterMap.containsKey(id)) {
                                	cluster = clusterMap.get(id);
                                }
                            	
                                this.esx_mob_queue.put(new Object[]{ anEsx, cluster});
                            }
                        }
					}			
					long esx_stop = System.currentTimeMillis();
					esx_loop_took = esx_stop - start;
					logger.debug("meGrabber ESX loop took " + esx_loop_took + "ms.");
                    for(int i = 0; i < Integer.parseInt(appConfig.get("MAX_ESXSTAT_THREADS")); i++) {
                        String stats = "stop_stats";
                        this.esx_mob_queue.put(stats);
                    }
				}
				
				long loop_took = vm_loop_took + esx_loop_took;
				// stupid simple thing to make this go every 60 seconds, since we're getting 'past data' anyways.
				// there's probably more accurate ways of doing this.
                int user_sleep_time = Integer.parseInt(appConfig.get("SLEEP_TIME")) * 1000;
				long sleep_time = user_sleep_time - loop_took;
				logger.debug("Sleeping for " + sleep_time + "ms.");
				Thread.sleep(sleep_time);
                // check the config object if it's determined to only run one time
                String dump = "dump_stats";
                sender.put(dump);
                if(appConfig.get("runOnce").contains("true")){
                    logger.info("Run once flag detected, exiting now.");
                    System.exit(0);
                }
			}
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.info("Interrupted Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
            System.exit(202);
		} catch(Exception e) {
			System.out.println(Arrays.toString(e.getStackTrace()));
			logger.info("Other Exception Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
            System.exit(203);
		}
		
	}

}
