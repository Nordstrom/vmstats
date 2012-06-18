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
import java.util.Hashtable;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;

class meGrabber implements Runnable{
	
	private final BlockingQueue<ManagedEntity> vm_mob_queue;
	private final BlockingQueue<ManagedEntity> esx_mob_queue;
	private final Hashtable<String, String> appConfig;
	private final ServiceInstance si;
	private static final Logger logger = LoggerFactory.getLogger(meGrabber.class);
    private volatile boolean cancelled;

	public meGrabber(ServiceInstance si, BlockingQueue<ManagedEntity> vm_mob_queue,
                     BlockingQueue<ManagedEntity> esx_mob_queue, Hashtable<String, String> appConfig) {
		this.vm_mob_queue = vm_mob_queue;
		this.esx_mob_queue = esx_mob_queue;
		this.appConfig = appConfig;
		this.si = si;
	}
    public void cancel() {
        this.cancelled = true;
    }
	public void run() {
		try {
			while(!cancelled) {
				long start = System.currentTimeMillis();
				ManagedEntity[] vms = null;
				try {
					// VirtualMachine NOT VirtualMachines
					// get a list of virtual machines
					vms = new InventoryNavigator(this.si.getRootFolder()).searchManagedEntities("VirtualMachine");
				} catch(RemoteException e) {
					e.getStackTrace();
					logger.info("vm grab exception: " + e);
                    System.exit(200);
				}
				
				if (vms != null) {
                    logger.info("Found " + vms.length + " Virtual Machines");
					// if they're not null, loop through them and send them to the
					// statsGrabber thread to get stats for.
                    for (ManagedEntity vm : vms) {
                        if (vm != null) {
                            this.vm_mob_queue.put(vm);
                        }
                    }
				}else{
                    logger.info("Found null virtual machines. Something's probably wrong.");
                }
				long vm_stop = System.currentTimeMillis();
				long vm_loop_took = vm_stop - start;
				logger.debug("meGrabber VM loop took " + vm_loop_took + "ms.");

                long esx_loop_took = 0;
				String graphEsx = this.appConfig.get("graphEsx");
				if (graphEsx.contains("true")) {
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
                                this.esx_mob_queue.put(anEsx);
                            }
                        }
					}			
					long esx_stop = System.currentTimeMillis();
					esx_loop_took = esx_stop - start;
					logger.debug("meGrabber ESX loop took " + esx_loop_took + "ms.");
				}
				
				long loop_took = vm_loop_took + esx_loop_took;
				// stupid simple thing to make this go every 60 seconds, since we're getting 'past data' anyways.
				// there's probably more accurate ways of doing this.
				long sleep_time = 60000 - loop_took;
				logger.debug("Sleeping for " + sleep_time + "ms.");
				Thread.sleep(sleep_time);
                // check the config object if it's determined to only run one time
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
