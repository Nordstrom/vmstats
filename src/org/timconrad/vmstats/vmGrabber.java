package org.timconrad.vmstats;
// this is a Producer in the arrangement
// this goes and gets a list of vm's to send to statsGrabber

import java.rmi.RemoteException;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;

public class vmGrabber implements Runnable{
	
	private final BlockingQueue<ManagedEntity> vm_mob_queue;
	private final BlockingQueue<ManagedEntity> esx_mob_queue;
	private final ServiceInstance si;
	private static final Logger logger = LoggerFactory.getLogger(vmGrabber.class);

	public vmGrabber(ServiceInstance si, BlockingQueue<ManagedEntity> vm_mob_queue, BlockingQueue<ManagedEntity> esx_mob_queue) {
		this.vm_mob_queue = vm_mob_queue;
		this.esx_mob_queue = esx_mob_queue;
		this.si = si;
	}
	
	public void run() {
		try {
			while(true) {
				long start = System.currentTimeMillis();
				ManagedEntity[] vms = null;
				try {
					// VirtualMachine NOT VirtualMachines
					// get a list of virtual machines
					vms = new InventoryNavigator(this.si.getRootFolder()).searchManagedEntities("VirtualMachine");
				} catch(RemoteException e) {
					e.getStackTrace();
					logger.info("vm grab exception: " + e);
				}
				
				logger.info("Found " + vms.length + " Virtual Machines");
				if (vms != null) {
					// if they're not null, loop through them and send them to the
					// statsGrabber thread to get stats for.
					for(int i = 0; i < vms.length; i++) {
						if(vms[i] != null) {
							this.vm_mob_queue.put(vms[i]);
						}
					}
				}
				long vm_stop = System.currentTimeMillis();
				
				long vm_loop_took = vm_stop - start;
				logger.debug("vmGrabber VM loop took " + vm_loop_took + "ms.");
				ManagedEntity[] esx = null;
				// get the esx nodes, aka HostSystem
				try {
					esx = new InventoryNavigator(this.si.getRootFolder()).searchManagedEntities("HostSystem");
				} catch(RemoteException e) {
					e.getStackTrace();
					logger.info("vm grab exception: " + e);
				}
				
				logger.info("Found " + esx.length + " ESX Hosts");
				if (esx != null) {
					// if they're not null, loop through them and send them to the
					// statsGrabber thread to get stats for.
					for(int i = 0; i < esx.length; i++) {
						if(esx[i] != null) {
							this.esx_mob_queue.put(esx[i]);
						}
					}
				}			
				
				long stop = System.currentTimeMillis();
				long loop_took = stop - start;
				logger.debug("vmGrabber ESX loop took " + loop_took + "ms.");
				// stupid simple thing to make this go every 60 seconds, since we're getting 'past data' anyways.
				// there's probably more accurate ways of doing this.
				long sleep_time = 60000 - loop_took;
				logger.debug("Sleeping for " + sleep_time + "ms.");
				Thread.sleep(sleep_time);
			}
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.info("Interrupted Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
		} catch(Exception e) {
			System.out.println(e.getStackTrace().toString());
			logger.info("Other Exception Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
		}
		
	}

}
