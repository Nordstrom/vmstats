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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.timconrad.vmstats.netty.NettyTCPWriter;

class GraphiteWriter implements Runnable{
	private static final Logger logger = LoggerFactory.getLogger(GraphiteWriter.class);
	private final BlockingQueue<Object> dumper;
	private String host = null;
	private int port = 0;
    private volatile boolean cancelled;
    private final Hashtable<String, String> appConfig;
    private boolean debugOutput = false;
	public GraphiteWriter(String host, int port, BlockingQueue<Object> dumper,Hashtable<String, String> appConfig) {
		// the constructor. construct things.
		this.dumper = dumper;
		this.host = host;
		this.port = port;
        this.appConfig = appConfig;
	}

    public void cancel() {
        this.cancelled = true;
    }
	
	public void run() {
        NettyTCPWriter graphite = new NettyTCPWriter(host, port, Integer.parseInt(this.appConfig.get("DISCONNECT_GRAPHITE_AFTER")));
        long total_stats = 0;
        try {
            graphite.connect();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        String threadName = Thread.currentThread().getName();
        BufferedWriter out  = null;
        if(this.appConfig.get("debugOutput").contains("true")){
            this.debugOutput = true;
        }
        if(this.debugOutput) {
            String fileName = "debug-gwriter-" + threadName + ".log";
            FileWriter fstream = null;
            try {
                fstream = new FileWriter(fileName);
                out = new BufferedWriter(fstream);
            }catch (Exception e) {
                System.out.println("file open error");
                System.exit(-1);
            }
        }
        try{
			while(!cancelled) {
				
				// take the first one off the queue. this is a BlockingQueue so it blocks the loop until somethin
				// comes along on the queue.
				Object value = this.dumper.take();
                String[] values;
                if(value instanceof String[]) {
                    // send it via sendMany in the graphite object
                    values = (String[]) value;
                    if (values.length != 0)
                    	graphite.sendMany(values);
                    total_stats += values.length;
                }
                else if(value instanceof String) {
                    if(value.equals("dump_stats")) {
                        logger.debug(threadName + " sent " + total_stats + " stats to graphite@" + this.host + ":" + this.port);
                        total_stats = 0;
                    }
                    else{
                        graphite.sendOne((String) value);
                    }
                }
                if(debugOutput) {
                    if(value instanceof String[]) {
                        values = (String[]) value;
                        for(int x=0; x < values.length; x++) {
                            if(out != null) {
                                out.write(values[x]);
                            }
                        }
                    }
                    if(out != null) {
                        out.flush();
                    }
                }
			}
			
		} catch(InterruptedException e) {
			e.getStackTrace();
            e.printStackTrace();
			logger.info("Thread: " + Thread.currentThread().getName() + "Interrupted: " + e.getMessage());
			Thread.currentThread().interrupt();
            System.exit(300);
		} catch(Exception e) {
			e.getStackTrace();
            e.printStackTrace();
			logger.info("Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
            System.exit(301);
		}
	}
}
