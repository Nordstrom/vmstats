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

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfSampleInfo;
import com.vmware.vim25.PerfMetricId;
import com.vmware.vim25.PerfProviderSummary;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
// this is the consumer in the arrangement
// this goes and gets the stats for a particular VM

class statsGrabber implements Runnable {

	private final BlockingQueue<Object> mob_queue;
	private final BlockingQueue<Object> sender;
	private final PerformanceManager perfMgr;
	private final Hashtable<String, Hashtable<String,String>> perfKeys;
	private final Hashtable<String, String> appConfig;
	private String mobType = "bob";
    private volatile boolean cancelled;
	
	private static final Logger logger = LoggerFactory.getLogger(statsGrabber.class);
	
	public statsGrabber(PerformanceManager perfMgr, Hashtable<String, Hashtable<String, String>> perfKeys,
			BlockingQueue<Object> mob_queue, BlockingQueue<Object> sender, Hashtable<String, String> appConfig, String mobType) {
		this.mob_queue = mob_queue;
		this.sender = sender;
		this.perfMgr = perfMgr;
		this.perfKeys = perfKeys;
		this.appConfig = appConfig;
		this.mobType = mobType;
	}	
	
	private String[] getStats(Object[] info) {
		ManagedEntity managedEntity = (ManagedEntity)info[0];
		String cluster = (String)info[1];
		if (cluster.trim().equals(""))
			cluster = "none";
		
		final ArrayList<String> temp_results = new ArrayList<String>();
		final String TAG_NS = appConfig.get("graphiteTag") + "." + appConfig.get("vcsTag") + "." + cluster;

		PerfProviderSummary pps;
		try {
			String meName = managedEntity.getName();
            // TODO: get rid of other stuff here that won't translate well into graphite
            // TODO: This won't handle every bizarre thing that people want to do with VM naming.
            // get rid of spaces, since we never want them.
            meName = meName.replace(" ", "_");
            String meNameTag = "";
            String[] meNameParts = meName.split("[.]");
            Boolean use_fqdn = false;
            Boolean is_ip = false;
            Boolean all_periods = false;
            Boolean all_absolute = false;
            Boolean all_delta = false;

            // parse it if we're supposed to use the fully qualified name
            if(appConfig.get("USE_FQDN").equals("true")) {
                use_fqdn = true;
            }
            if(appConfig.get("SEND_ALL_PERIODS").equals("true")) {
                all_periods = true;
            }
            if(appConfig.get("SEND_ALL_ABSOLUTE").equals("true")) {
                all_absolute = true;
            }
            if(appConfig.get("SEND_ALL_DELTA").equals("true")) {
                all_delta = true;
            }

            // detect if the object name is an IP address, so that using short name
            // doesn't shorten it to the first octet.
            try {
                int a = Integer.parseInt(meNameParts[0]);
                int b = Integer.parseInt(meNameParts[1]);
                is_ip = true;
            }catch(NumberFormatException e) {
                is_ip = false;
            }

            if(use_fqdn) {
                meNameTag = meName;
            }else{
                if(is_ip) {
                    // make sure the whole IP is displayed
                    meNameTag = meName;
                }else{
                    meNameTag = meNameParts[0];
                }
            }
            // replace all the . with _
            meNameTag = meNameTag.replace(".", "_");

			pps = this.perfMgr.queryPerfProviderSummary(managedEntity);
			// for VM's, this is likely always 20 seconds in this context.
			int refreshRate = pps.getRefreshRate().intValue();
			PerfMetricId[] pmis = this.perfMgr.queryAvailablePerfMetric(managedEntity, null, null, refreshRate);
			int perfEntries = Integer.parseInt(appConfig.get("PERIODS"));
			PerfQuerySpec qSpec = createPerfQuerySpec(managedEntity, pmis, perfEntries, refreshRate);
			// pValues always returns perfEntries results. this code hasn't been tested grabbing more than 1
			// stat at a time.
			PerfEntityMetricBase[] pValues = perfMgr.queryPerf(new PerfQuerySpec[] {qSpec});
			
			if(pValues != null) {
                for (PerfEntityMetricBase pValue : pValues) {
                    PerfEntityMetric pem = (PerfEntityMetric) pValue;
                    PerfMetricSeries[] vals = pem.getValue();
                    PerfSampleInfo[] infos = pem.getSampleInfo();

                    for (int x = 0; vals != null && x < vals.length; x++) {
                        int counterId = vals[x].getId().getCounterId();
                        // create strings for the parts of the tag.
                        Hashtable<String, String> perfKey = perfKeys.get("" + counterId);
                        if (perfKey==null) 
                        	continue;
                        String key = perfKey.get("key");
                        
                        String instance = vals[x].getId().getInstance();
                        // disks will be naa.12341234, change them to naa_12341234 instead
                        instance = instance.replace(".", "_");
                        // some other items are some/random/path, change them to dots
                        instance = instance.replace("/", ".");
                        // the 'none' rollup type is completely legitimate - it doesn't roll up, so it's live data
                        String rollup = perfKeys.get("" + counterId).get("rollup");
                        String statstype = perfKeys.get("" + counterId).get("statstype");

                        String graphiteTag;
                        if (instance.equals("")) {
                            // no instance, no period required
                            graphiteTag = TAG_NS + "." + mobType + "." + meNameTag + "." + key + "." + rollup;
                        } else {
                            graphiteTag = TAG_NS + "." + mobType + "." + meNameTag + "." + key + "." + instance + "." + rollup;
                        }
                        // tag should be vmstats.VMTAG.hostname.cpu.whatever.whatever at this point

                        long stat = 0;
                        if (vals[x] instanceof PerfMetricIntSeries) {
                            PerfMetricIntSeries val = (PerfMetricIntSeries) vals[x];
                            long[] longs = val.getValue();
                            long running_total = 0;
                            for (int c = 0; c < longs.length; c++) {
                                stat = longs[c];
                                running_total += stat;
                                // always create an average, even though it'll be wrong until the 3rd iteration
                                long stat_avg = running_total / longs.length;
                                // get the timestamp for the data
                                long timestamp = infos[c].getTimestamp().getTimeInMillis() / 1000;
                                String graphiteData = graphiteTag + " " + stat + " " + timestamp + "\n";
                                if(all_periods) {
                                    // send all the periods
                                    temp_results.add(graphiteData);
                                }else if(all_absolute && statstype.equals("absolute")) {
                                    // send if we're sending all the absolute and is an absolute type
                                    temp_results.add(graphiteData);
                                }else if(all_delta && statstype.equals("delta")) {
                                    // send if we're sending all delta and is a delta type
                                    temp_results.add(graphiteData);
                                }else{
                                    // send only the last data
                                    if( c == (longs.length - 1)) {
                                        if(statstype.equals("absolute")) {
                                            // if it's an absolute, average the data and send it
                                            // TODO: turn this into a flag?
                                            graphiteData = graphiteTag + " " + stat_avg + " " + timestamp + "\n";
                                            temp_results.add(graphiteData);
                                        }else{
                                            // otherwise, don't average it
                                            graphiteData = graphiteTag + " " + stat + " " + timestamp + "\n";
                                            temp_results.add(graphiteData);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
			}
			
		} catch (RuntimeFault e) {
			logger.info("statsGrabber: Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
			e.printStackTrace();
            System.exit(100);
		} catch (RemoteException e) {
			logger.info("statsGrabber: Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
			e.printStackTrace();
            System.exit(101);
		}
		return temp_results.toArray(new String[temp_results.size()]);

	}
	
	private static PerfQuerySpec createPerfQuerySpec(ManagedEntity me, PerfMetricId[] metricIds, int maxSample, int interval) {
		PerfQuerySpec qSpec = new PerfQuerySpec();
		qSpec.setEntity(me.getMOR());
		// set the maximum of metrics to be return
		// only appropriate in real-time performance collecting
		qSpec.setMaxSample(new Integer(maxSample));
		//    qSpec.setMetricId(metricIds);
		// optionally you can set format as "normal"
		qSpec.setFormat("normal");
		// set the interval to the refresh rate for the entity
		qSpec.setIntervalId(new Integer(interval));

		return qSpec;
	}

    public void cancel() {
        this.cancelled = true;
    }
	
	public void run() {
        long run_start = 0;
        long total_stats = 0;
        long mob_count = 0;
        while(!cancelled) {
            // take item from BlockingQueue
            Object mob = null;
            try {
                mob = this.mob_queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(108);
            }

            if(mob instanceof Object[] && ((Object[])mob)[0] instanceof ManagedEntity) {
            	Object[] castedMob = (Object[])mob;
                // keep count of mobs that we've processed
                mob_count++;
                // run the getStats function on the vm
                String[] stats = this.getStats(castedMob);
                // take the output from the getStats function and send to graphite.
                total_stats += stats.length;
                try {
                    sender.put(stats);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.exit(107);
                }
            }else if(mob instanceof String) {
                if(mob.equals("start_stats")){
                    run_start = System.currentTimeMillis();
                }
                if(mob.equals("stop_stats")) {
                    long took = System.currentTimeMillis() - run_start;
                    logger.info(Thread.currentThread().getName() + " took: " + took + "ms" + ",sent " + total_stats
                            + " stats for " + mob_count + " managed objects stats to Graphite");
                    total_stats = 0;
                    mob_count = 0;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.exit(106);
                }
            }else{
                logger.info("Unknown mob type, should be String or ManagedEntity");
                System.exit(105);
            }
        }
	}
}
