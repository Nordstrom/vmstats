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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vmware.vim25.InvalidLogin;
import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServiceInstance;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

	public static void main(String[] args) {
		
		Logger logger = LoggerFactory.getLogger(Main.class);
		Properties config = new Properties();
		Boolean showPerfMgr = false;
		Boolean showEstimate = false;
		Boolean noThreads = false;
		Boolean noGraphite = false;
        
        File configFile = new File("vmstats.properties");
		
		Hashtable<String, String> appConfig = new Hashtable<String, String>();
		
		CommandLineParser parser = new PosixParser();
		Options options = new Options();
		
		options.addOption("P", "perfMgr", false, "Display Performance Manager Counters and exit");
		options.addOption("E", "estimate", false, "Estimate the # of counters written to graphite and exit");
		options.addOption("N", "noThreads", false, "Don't start any threads, just run the main part (helpful for troubleshooting initial issues");
		options.addOption("g", "noGraphite", false, "Don't send anything to graphite");
        options.addOption("c", "configFile", true, "Configuration file for vmstats - defaults to 'vmstats.properties' in the .jar directory");
        options.addOption("O", "runOnce", false, "Run the stats gatherer one time - useful for debugging");
        options.addOption("D", "debugOutput", false, "Dump the output to a thread-named file.");
        options.addOption("h", "help", false, "show help");

		try {
			CommandLine line = parser.parse(options,  args);
            if(line.hasOption("help")){
                System.out.println("vmstats.jar -Dlog4j.configuration=file:/path/to/log4j.properties [options]");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("vmstats.jar", options);
                System.exit(0);
            }
			if(line.hasOption("perfMgr")){
				showPerfMgr = true;
			}
			if(line.hasOption("estimate")){
				showEstimate = true;
			}
			if(line.hasOption("noThreads")){
				noThreads = true;
			}
			if(line.hasOption("noGraphite")){
				noGraphite = true;
			}
            if(line.hasOption("runOnce")) {
                appConfig.put("runOnce","true");

            }else{
                appConfig.put("runOnce", "false");
            }
            if(line.hasOption("debugOutput")) {
                appConfig.put("debugOutput", "true");
            }else{
                appConfig.put("debugOutput", "false");
            }

            if(line.hasOption("configFile")){
                // if the user adds a custom config flag, use it. Otherwise they'll get the default.
                String file = line.getOptionValue("configFile");
                File optFile = new File(file);
                boolean exists = optFile.exists();
                // check to make sure the file exists.
                if(!exists) {
                    System.out.println("The configuration file doesn't seem to exist in path: " + file);
                    System.exit(0);
                }else{
                    configFile = optFile;
                }

            }
		}catch(org.apache.commons.cli.ParseException e) {
			System.out.println("CLI options exception: " + e.getMessage());
            e.printStackTrace();
		}
		
		try {
			config.load(new FileInputStream(configFile));
		} catch (FileNotFoundException e) {
			logger.info("Configuration file not found!\n\tException: " + e );
			System.exit(-1);
		} catch (IOException e) {
			logger.info("Configuration file not found!\n\tException: " + e );
			System.exit(-1);
		}

        Enumeration configOpts = config.propertyNames();
        // this will have to be manually updated.
        String[] expectedOptions = {"VCS_TAG", "VCS_USER", "GRAPHITE_PORT",
                "GRAPHITE_TAG", "VCS_HOST", "VCS_PASS", "MAX_VMSTAT_THREADS",
                "GRAPHITE_HOST", "ESX_STATS", "USE_FQDN", "SLEEP_TIME",
                "SEND_ALL_ABSOLUTE", "SEND_ALL_DELTA", "SEND_ALL_PERIODS"};
        ArrayList<String> matchedOptions = new ArrayList<String>();
        while(configOpts.hasMoreElements()) {
            String optTmp = (String) configOpts.nextElement();
            for(int i = 0; i < expectedOptions.length; i++) {
                if(optTmp.equals(expectedOptions[i])) {
                    matchedOptions.add(optTmp);
                }
            }
        }

        if(expectedOptions.length != matchedOptions.size()) {
            // this kinda blows, but better than throwing a null pointer exception
            // or doing try/catch for each possible option below.
            System.out.println("Configuration file options are missing");
            System.exit(-1);
        }

		// Get settings from config file
		String vcsHostRaw = config.getProperty("VCS_HOST");
		String vcsUser = config.getProperty("VCS_USER");
		String vcsPass = config.getProperty("VCS_PASS");
		String vcsTag = config.getProperty("VCS_TAG");
		List<String> statExcludes = Arrays.asList(config.getProperty("STAT_EXCLUDES").split(","));

		appConfig.put("vcsTag", vcsTag);
		// vcs information
		// this needs to be https://host/sdk
		String vcsHost = "https://" + vcsHostRaw + "/sdk";
		String graphEsx = config.getProperty("ESX_STATS");
        appConfig.put("USE_FQDN",config.getProperty("USE_FQDN"));
		appConfig.put("graphEsx", graphEsx);
		
		// graphite information
		String graphiteHost = config.getProperty("GRAPHITE_HOST");
		int graphitePort = Integer.parseInt(config.getProperty("GRAPHITE_PORT"));
		String graphiteTag = config.getProperty("GRAPHITE_TAG");

        try {
            appConfig.put("graphiteTag", graphiteTag);
        }catch(NullPointerException e) {
            System.out.println("Issue with configuration file - Missing GRAPHITE_TAG");
            System.exit(-1);
        }
		
		// TODO: make this dynamic. maybe.
		int MAX_VMSTAT_THREADS = Integer.parseInt(config.getProperty("MAX_VMSTAT_THREADS"));
        int MAX_ESXSTAT_THREADS = Integer.parseInt(config.getProperty("MAX_ESXSTAT_THREADS"));
        int MAX_GRAPHITE_THREADS = Integer.parseInt(config.getProperty("MAX_GRAPHITE_THREADS"));

        appConfig.put("MAX_VMSTAT_THREADS", String.valueOf(MAX_VMSTAT_THREADS));
        appConfig.put("MAX_ESXSTAT_THREADS", String.valueOf(MAX_ESXSTAT_THREADS));
        String SLEEP_TIME = config.getProperty("SLEEP_TIME");
        appConfig.put("SLEEP_TIME",SLEEP_TIME);
        String CACHED_LOOP_CYCLES = config.getProperty("CACHED_LOOP_CYCLES");
        appConfig.put("CACHED_LOOP_CYCLES", CACHED_LOOP_CYCLES);
        String SEND_ALL_PERIODS = config.getProperty("SEND_ALL_PERIODS");
        appConfig.put("SEND_ALL_PERIODS",SEND_ALL_PERIODS);
        int sleep_time = Integer.parseInt(SLEEP_TIME);
        if((sleep_time % 20) == 0) {
            int periods =  sleep_time / 20;
            appConfig.put("PERIODS", String.valueOf(periods));
        }else{
            System.out.println("SLEEP_TIME needs to be divisible by 20, please fix");
            System.exit(-1);
        }
        appConfig.put("SEND_ALL_ABSOLUTE", config.getProperty("SEND_ALL_ABSOLUTE"));
        appConfig.put("SEND_ALL_DELTA", config.getProperty("SEND_ALL_DELTA"));
        appConfig.put("DISCONNECT_GRAPHITE_AFTER", config.getProperty("DISCONNECT_GRAPHITE_AFTER"));

		// Build internal data structures. 
		
		// use a hashtable to store performance id information
		Hashtable<String, Hashtable<String, String>> perfKeys = new Hashtable<String, Hashtable<String, String>>();
		// BlockingQueue to store managed objects - basically anything that vmware knows about
		BlockingQueue<Object> vm_mob_queue = new ArrayBlockingQueue<Object>(20000);
		BlockingQueue<Object> esx_mob_queue = new ArrayBlockingQueue<Object>(10000);
		// BlockingQueue to store arrays of stats - each managed object generates a bunch of strings that are stored in
		BlockingQueue<Object> sender = new ArrayBlockingQueue<Object>(60000);
		
		// Initialize these vmware types as nulls so we can see if things work properly
		ServiceInstance si = null;
		PerformanceManager perfMgr = null;
		
		try {
            // TODO: this doesn't handle some ASCII characters well, not sure why.
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
				String group = counters[i].getGroupInfo().getKey();
				
				if (statExcludes.contains(group)) continue;
				
				// create a temp hash to push onto the big hash
				Hashtable<String,String> temp_hash = new Hashtable<String, String>();
				String path = group + "." + counters[i].getNameInfo().getKey();
                // this is a key like cpu.run.0.summation
				temp_hash.put("key", path);
                // one of average, latest, maximum, minimum, none,  summation
				temp_hash.put("rollup", counters[i].getRollupType().toString());
                // one of absolute, delta, rate
                temp_hash.put("statstype", counters[i].getStatsType().toString());
				// it's important to understand that the counters aren't sequential, so they have their own id.
				perfKeys.put("" + counters[i].getKey(), temp_hash);
			}
		}else{
			logger.info("Issues with the service instance that wasn't properly handled");
            System.exit(-1);
		}
		
		if(showPerfMgr) {
			// show the performance keys that are available to the user
			System.out.println("Showing Performance Counter Entities available:");
			System.out.println("Read the following link for more information:");
			System.out.println("http://vijava.sourceforge.net/vSphereAPIDoc/ver5/ReferenceGuide/vim.PerformanceManager.html");
			Enumeration<String> keys = perfKeys.keys();
            System.out.println("ID|Tag|Rollup");
			while(keys.hasMoreElements()){
				String key = (String) keys.nextElement();
				System.out.println(key + "|" + perfKeys.get(key).get("key") + "|" + perfKeys.get(key).get("rollup"));
			}
			System.exit(0);
		}
		
		if(showEstimate) {
			// estimate the number of keys that will be updated/written to Graphite per minute
			System.out.println("Currently Disabled");
		}
		
		// this gets the lists of vm's from vCenter
		if(!noThreads) {
			if(si != null && perfMgr != null) {
				logger.info("ServiceInstance: " + si);
				logger.info("PerformanceManager: " + perfMgr);
				
				meGrabber me_grabber = new meGrabber(si, vm_mob_queue, esx_mob_queue, appConfig, sender);
				ExecutorService grab_exe = Executors.newCachedThreadPool();
				grab_exe.execute(me_grabber);
				
				// it's easier sometimes to debug things without stats being sent to graphite. make noGraphite = true; to 
				// change this.
				if(!noGraphite) {
                    for(int i = 1; i <= MAX_GRAPHITE_THREADS; i++ ) {
                        GraphiteWriter graphite = new GraphiteWriter(graphiteHost, graphitePort, sender, appConfig);
                        ExecutorService graph_exe = Executors.newCachedThreadPool();
                        graph_exe.execute(graphite);
                    }
				}else{
                    System.out.println("Graphite output has been disabled via the -g flag.");
                }
				
				for(int i = 1; i <= MAX_VMSTAT_THREADS; i++ ) {
					statsGrabber vm_stats_grabber = new statsGrabber(perfMgr, perfKeys, vm_mob_queue, sender, appConfig, "vm");
					ExecutorService vm_stat_exe = Executors.newCachedThreadPool();
					vm_stat_exe.execute(vm_stats_grabber);
				}

				if(graphEsx.contains("true")) {
                    for(int i = 1; i <= MAX_ESXSTAT_THREADS; i++ ) {
                        statsGrabber esx_stats_grabber = new statsGrabber(perfMgr, perfKeys, esx_mob_queue, sender, appConfig, "ESX");
                        ExecutorService esx_stat_exe = Executors.newCachedThreadPool();
                        esx_stat_exe.execute(esx_stats_grabber);
                    }
				}
				
				Map<String, BlockingQueue<Object>> watch = new HashMap<String, BlockingQueue<Object>>();
				watch.put("graphite", sender);
				watch.put("vm", vm_mob_queue);
				watch.put("esx", esx_mob_queue);
				
				QueueWatcher watcher = new QueueWatcher(watch, 5000);
				Executors.newSingleThreadExecutor().execute(watcher);
				
				
			}else{
				logger.info("Either ServiceInstance or PerformanceManager is null, bailing.");
			}
		}else{
			System.out.println("Not running any of the main threads");
			System.exit(0);
		}
	}
}
