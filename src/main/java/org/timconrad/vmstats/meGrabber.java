package org.timconrad.vmstats;
/*
 * Copyright 2012 Tim Conrad - tim@timconrad.org
 * Copyright 2014, Nordstrom, Inc - peter.dalinis@nordstrom.com
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;

class meGrabber implements Runnable{
	
	private final BlockingQueue<Object> vm_mob_queue;
	private final BlockingQueue<Object> esx_mob_queue;
	private final List<Object> vm_cache;
	private final List<Object> esx_cache;
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
        this.vm_cache = new ArrayList<Object>();
        this.esx_cache = new ArrayList<Object>();
	}
	
    public void cancel() {
        this.cancelled = true;
    }
    
    public void copyCachesToQueues() {
    	try {
	    	for (Object item : vm_cache) {
				vm_mob_queue.put(item);
				
	    	}
	    	for (Object item : esx_cache) {
	    		esx_mob_queue.put(item);
	    	}
    	} catch (InterruptedException e) {
    		Thread.currentThread().interrupt();
			logger.info("Interrupted Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
            System.exit(202);
		}
    }
    
    public void refreshVMCache() {
    	vm_cache.clear();
    	   	
    	long start = System.currentTimeMillis();
		ManagedEntity[] vms = null;
		ManagedEntity[] clusters = null;
		clusterMap = new HashMap<String, String>();
        for(int i = 0; i < Integer.parseInt(appConfig.get("MAX_VMSTAT_THREADS")); i++) {
            String stats = "start_stats";
            vm_cache.add(stats);
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
				clusterMap.put(host.val, name.replace(" ", "_").replace(".", "_"));
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
                    vm_cache.add(new Object[] { vm, cluster });
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
            vm_cache.add(stats);
        }
    }
    
    public void refreshESXCache() {
    	long start = System.currentTimeMillis();
        long esx_loop_took = 0;
		String graphEsx = this.appConfig.get("graphEsx");
		if (graphEsx.contains("true")) {
            for(int i = 0; i < Integer.parseInt(appConfig.get("MAX_ESXSTAT_THREADS")); i++) {
                String stats = "start_stats";
                esx_cache.add(stats);
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
                    	
                        esx_cache.add(new Object[]{ anEsx, cluster});
                    }
                }
			}			
			
			esx_loop_took = System.currentTimeMillis() - start;
			logger.debug("meGrabber ESX loop took " + esx_loop_took + "ms.");
            for(int i = 0; i < Integer.parseInt(appConfig.get("MAX_ESXSTAT_THREADS")); i++) {
                String stats = "stop_stats";
                esx_cache.add(stats);
            }
		}
    }

	public void run() {
		int cachedLoopCounter = 0;
		int cachedLoopCycles = Integer.parseInt(appConfig.get("CACHED_LOOP_CYCLES"));
		int user_sleep_time = Integer.parseInt(appConfig.get("SLEEP_TIME")) * 1000;
		String dump = "dump_stats";
		
		try {
			while(!cancelled) {
				refreshVMCache();
				refreshESXCache();
				
				while (cachedLoopCounter < cachedLoopCycles) {
					copyCachesToQueues();
					sender.put(dump);
					cachedLoopCounter++;
					Thread.sleep(user_sleep_time);
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
