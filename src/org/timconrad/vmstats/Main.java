package org.timconrad.vmstats;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vmware.vim25.InvalidLogin;
import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServiceInstance;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.spi.LoggerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

	public static void main(String[] args) {
		
		Logger logger = LoggerFactory.getLogger(Main.class);
		Properties configFile = new Properties();
		
		CommandLineParser parser = new PosixParser();
		Options options = new Options();
		Boolean showPerfMgr = false;
		Boolean showEstimate = false;
		Boolean noThreads = false;
		
		options.addOption("P", "perfMgr", false, "Display Performance Manager Counters and exit");
		options.addOption("E", "estimate", false, "Estimate the # of counters written to graphite and exit");
		options.addOption("N", "noThreads", false, "Don't start any threads, just run the main part (helpful for troubleshooting initial issues");
		
		try {
			CommandLine line = parser.parse(options,  args);
			if(line.hasOption("perfMgr")){
				showPerfMgr = true;
			}
			if(line.hasOption("estimate")){
				showEstimate = true;
			}
			if(line.hasOption("noThreads")){
				noThreads = true;
			}
		}catch(org.apache.commons.cli.ParseException e) {
			System.out.println("Unexpected exception: " + e.getMessage());
		}
		
		try {
			configFile.load(new FileInputStream("vmstats.properties"));
		} catch (FileNotFoundException e) {
			logger.info("Configuration file not found!\n\tException: " + e );
			System.exit(-1);
		} catch (IOException e) {
			logger.info("Configuration file not found!\n\tException: " + e );
			System.exit(-1);
		}
		
		// Get settings from config file
		
		String vcsHostRaw = configFile.getProperty("VCS_HOST");
		String vcsUser = configFile.getProperty("VCS_USER");
		String vcsPass = configFile.getProperty("VCS_PASS");
		// vcs information
		// this needs to be https://host/sdk
		String vcsHost = "https://" + vcsHostRaw + "/sdk";
		
		// graphite information
		String graphiteHost = configFile.getProperty("GRAPHITE_HOST");
		int graphitePort = Integer.parseInt(configFile.getProperty("GRAPHITE_PORT"));
		
		// TODO: make this dynamic. maybe.
		int MAX_STAT_THREADS = Integer.parseInt(configFile.getProperty("MAX_STAT_THREADS"));
		 
		// Build internal data structures. 
		
		// use a hashtable to store performance id information
		Hashtable<String, Hashtable<String, String>> perfKeys = new Hashtable<String, Hashtable<String, String>>();
		// BlockingQueue to store managed objects - basically anything that vmware knows about
		BlockingQueue<ManagedEntity> mob_queue = new ArrayBlockingQueue<ManagedEntity>(10000);
		// BlockingQueue to store arrays of stats - each vm generates a bunch of strings that are stored in
		// an array. This array gets sent to the graphite thread to be sent to graphite.
		BlockingQueue<String[]> sender = new ArrayBlockingQueue<String[]>(10000);
		
		// Initialize these vmware types as nulls so we can see if things work properly
		ServiceInstance si = null;
		PerformanceManager perfMgr = null;
		
		try {
			// TODO: Fix login issue - bad character?
			si = new ServiceInstance(new URL(vcsHost), vcsUser, vcsPass, true);

		} catch (InvalidLogin e) {
			logger.info("Invalid login vCenter: " + vcsHost + " User: " + vcsUser);
			System.exit(-1);
		} catch (RemoteException e) {
			logger.info("Remote exception: " + e);
			e.printStackTrace();
		} catch( MalformedURLException e) {
			logger.info("MalformedURLexception: " + e);
			e.printStackTrace();
		}
		
		if (si != null) {
			perfMgr = si.getPerformanceManager();
			PerfCounterInfo[] counters = perfMgr.getPerfCounter();
			
			// build a hash lookup to turn the counter 23 into 'disk.this.that.the.other'
			// These are not sequential.
			for(int i=0; i < counters.length; i++) {
				// create a temp hash to push onto the big hash
				Hashtable<String,String> temp_hash = new Hashtable<String, String>();
				String path = counters[i].getGroupInfo().getKey() + "." + counters[i].getNameInfo().getKey();
				temp_hash.put("key", path);
				temp_hash.put("rollup", counters[i].getRollupType().toString());
				// it's important to understand that the counters aren't sequential, so they have their own id.
				perfKeys.put("" + counters[i].getKey(), temp_hash);
			}
		}else{
			logger.info("Issues with the service instance that wasn't properly handled");
		}
		
		if(showPerfMgr) {
			// show the performance keys that are available to the user
			System.out.println("Showing Performance Counter Enttites available:");
			Enumeration keys = perfKeys.keys();
			while(keys.hasMoreElements()){
				String key = (String) keys.nextElement();
				System.out.println("ID: " + key + " Tag: " + perfKeys.get(key).get("key") + " Rollup: " + perfKeys.get(key).get("rollup"));
			}
			System.exit(0);
		}
		
		if(showEstimate) {
			// estimate the number of keys that will be updated/written to Graphite per minute
			System.out.println("Showing estimate:");
			System.out.println("There are " + perfKeys.size() + " Performance Metrics available");
			ManagedEntity[] vms = null;
			try {
				// VirtualMachine NOT VirtualMachines
				// get a list of virtual machines
				vms = new InventoryNavigator(si.getRootFolder()).searchManagedEntities("VirtualMachine");
			} catch(RemoteException e) {
				e.getStackTrace();
				logger.info("vm grab exception: " + e);
			}
			System.out.println("There are currently " + vms.length + " Virtual Machines known to this vCenter");
			int metricCount = perfKeys.size() * vms.length;
			System.out.println("There will be approximatly " + metricCount +  " stats written to graphite per time period");
			System.out.println("This is way way way off, probably x3 at this point");
			System.exit(0);
		}
		
		// this gets the lists of vm's from vCenter
		if(!noThreads) {
			if( si != null && perfMgr != null) {
				logger.info("ServiceInstance: " + si);
				logger.info("PerformanceManager: " + perfMgr);
				
				vmGrabber vm_grabber = new vmGrabber(si, mob_queue);
				ExecutorService grab_exe = Executors.newCachedThreadPool();
				grab_exe.execute(vm_grabber);
				
				GraphiteWriter graphite = new GraphiteWriter(graphiteHost, graphitePort, sender);
				ExecutorService graph_exe = Executors.newCachedThreadPool();
				graph_exe.execute(graphite);
				
				for(int i = 1; i <= MAX_STAT_THREADS; i++ ) {
					statsGrabber stats_grabber = new statsGrabber(perfMgr, perfKeys, mob_queue, sender);
					ExecutorService stat_exe = Executors.newCachedThreadPool();
					stat_exe.execute(stats_grabber);
				}
			}else{
				logger.info("Either ServiceInstance or PerformanceManager is null, bailing.");
			}
		}else{
			logger.info("foo");
			System.out.println("Not running any of the main threads");
			System.exit(0);
		}
		

	}

}
