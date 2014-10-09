
package org.timconrad.vmstats;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueWatcher implements Runnable {

	private Map<String, BlockingQueue<Object>> _queues;
	private volatile boolean cancelled;
	private int _sleep;
	private static final Logger logger = LoggerFactory.getLogger(QueueWatcher.class);
	
	
	public QueueWatcher(Map<String,BlockingQueue<Object>> queues, int sleep) {
		_queues = queues;
		_sleep = sleep;
	}

	public void cancel() {
        this.cancelled = true;
    }
	
	public void run() {
		
		while(!cancelled) {
			for (Map.Entry<String, BlockingQueue<Object>> queue : _queues.entrySet()) {
				logger.info("The {} queue has {} items.", queue.getKey(), queue.getValue().size());
			}
			
			try {
				Thread.sleep(_sleep);
			} catch (InterruptedException e) {
				logger.error(e.toString());
			}
		}
	}
	
	
}
