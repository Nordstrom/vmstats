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
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GraphiteWriter implements Runnable{
	private static final Logger logger = LoggerFactory.getLogger(GraphiteWriter.class);
	private final BlockingQueue<String[]> dumper;
	private String host = null;
	private int port = 0;
    private volatile boolean cancelled;
	
	public GraphiteWriter(String host, int port, BlockingQueue<String[]> dumper) {
		// the constructor. construct things.
		this.dumper = dumper;
		this.host = host;
		this.port = port;
	}

    public void cancel() {
        this.cancelled = true;
    }
	
	public void run() {
		GraphiteUDPWriter graphite = new GraphiteUDPWriter(host, port);
        String threadName = Thread.currentThread().getName();
        String fileName = "debug-gwriter-" + threadName + ".log";
        BufferedWriter out  = null;
        FileWriter fstream = null;
        try {
            fstream = new FileWriter(fileName);
            out = new BufferedWriter(fstream);
        }catch (Exception e) {
            System.out.println("file open error");
            System.exit(-1);
        }
		try {
			while(!cancelled) {
				// take the first one off the queue. this is a BlockingQueue so it blocks the loop until somethin
				// comes along on the queue.
				String[] value = this.dumper.take();
				// send it via sendMany in the graphite object
				graphite.sendMany(value);
                for(int x=0; x < value.length; x++) {
                    out.write(value[x]);
                }
                out.flush();
                Thread.sleep(100);
			}
			
		} catch(InterruptedException e) {
			e.getStackTrace();
			logger.info("Thread: " + Thread.currentThread().getName() + "Interrupted: " + e.getMessage());
			Thread.currentThread().interrupt();
            System.exit(300);
		} catch(Exception e) {
			e.getStackTrace();
			logger.info("Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
            System.exit(301);
		}
	}
}
