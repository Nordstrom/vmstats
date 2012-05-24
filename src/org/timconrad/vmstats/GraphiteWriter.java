package org.timconrad.vmstats;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphiteWriter implements Runnable{
	private static final Logger logger = LoggerFactory.getLogger(GraphiteWriter.class);
	private final BlockingQueue<String[]> dumper;
	private String host = null;
	private int port = 0;
	
	public GraphiteWriter(String host, int port, BlockingQueue<String[]> dumper) {
		// the constructor. construct things.
		this.dumper = dumper;
		this.host = host;
		this.port = port;
	}
	
	public void run() {
		GraphiteUDPWriter graphite = new GraphiteUDPWriter(host, port);
		try {
			while(true) {
				// take the first one off the queue. this is a BlockingQueue so it blocks the loop until somethin
				// comes along on the queue.
				String[] value = this.dumper.take();
				// send it via sendMany in the graphite object
				graphite.sendMany(value);
			}
			
		} catch(InterruptedException e) {
			e.getStackTrace();
			logger.info("Thread: " + Thread.currentThread().getName() + "Interrupted: " + e.getMessage());
			Thread.currentThread().interrupt();
		} catch(Exception e) {
			e.getStackTrace();
			logger.info("Thread: " + Thread.currentThread().getName() + " +  Interrupted: " + e.getMessage());
		}
	}
}
