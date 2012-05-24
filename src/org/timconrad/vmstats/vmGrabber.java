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
	
	private final BlockingQueue<ManagedEntity> mob_queue;
	private final ServiceInstance si;
	private static final Logger logger = LoggerFactory.getLogger(vmGrabber.class);

	public vmGrabber(ServiceInstance si, BlockingQueue<ManagedEntity> mob_queue) {
		this.mob_queue = mob_queue;
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
							this.mob_queue.put(vms[i]);
						}
					}
				}
				long stop = System.currentTimeMillis();
				
				long loop_took = stop - start;
				logger.debug("vmGrabber loop took " + loop_took + "ms.");
				// TODO: Make this sleep properly. Perhaps.  With ~220 VM's, this will skew about 1.5 seconds a day in current form.
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
