package org.timconrad.vmstats.netty;
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

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executors;

import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyTCPWriter {

    private static final Logger logger = LoggerFactory.getLogger(NettyTCPWriterHandler.class);
    private String host = "localhost";
    private int port = 2003;

    private Channel channel;
    private ChannelFuture future;
    private ChannelFuture lastWrite;
    private ClientBootstrap bootstrap;
    final Timer timer = new HashedWheelTimer();
    
    private int disconnectCounter = 1;
	private int disconnectAfter;

    public NettyTCPWriter(String host, int port, int disconnectAfter) {
        this.host = host;
        this.port = port;
        this.disconnectAfter = disconnectAfter;
    }

    public void connect() throws IOException {
        this.bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        this.bootstrap.setPipelineFactory(new NettyTCPWriterPipelineFactory(this.bootstrap, this.channel, this.timer));
        this.bootstrap.setOption("tcpNoDelay", true);
        
        // TODO: do some exception handling here
        bootstrap.setOption("remoteAddress", new InetSocketAddress(this.host, this.port));
        this.future = this.bootstrap.connect();
        this.channel = this.future.awaitUninterruptibly().getChannel();
        this.channel.setReadable(false);
        
        if(!this.future.isSuccess()){
            logger.info("NettyTCP: future unsuccessful");
        }
    }

    public void sendOne(String input) {
        if(!input.contains("\n")){
            input = input + "\n";
        }
        this.lastWrite = this.channel.write(input);
    }
    
    public void sendMany2(String[] inputs) throws IOException {
    	
    	StringBuilder sb = new StringBuilder();
    	
    	
    	for(String input : inputs) {
    		if (input != null && !input.trim().equals(""))
    			sb.append(input + "\r\n ");
    	}
    	
    	if(!this.channel.isConnected()) {
            logger.info("Channel not connected");
            this.future = this.bootstrap.connect().awaitUninterruptibly();
			this.channel = this.future.awaitUninterruptibly().getChannel();
			
            if(!this.future.isSuccess()){
                logger.info("NettyTCP: future unsuccessful");
                try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					
				}
                
            }
        }
    	
    	logger.info("writing " + sb.length() + " to graphite");
    	this.lastWrite = this.channel.write(sb.toString());
    }

    public void sendMany(String[] inputs) throws IOException {
        for(String input : inputs) {
            if(!input.contains("\n")) {
                input = input + "\n";
            }
            if(!this.channel.isConnected()) {
                logger.info("Channel not connected");
                this.future = this.bootstrap.connect().awaitUninterruptibly();
				this.channel = this.future.awaitUninterruptibly().getChannel();
				
                if(!this.future.isSuccess()){
                    logger.info("NettyTCP: future unsuccessful");
                    try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						
					}
                    
                }
            }
            
        	this.lastWrite = this.channel.write(input);
            disconnectCounter++;
            
            
            if (disconnectAfter != 0 && disconnectCounter % disconnectAfter == 0)
            {
            	disconnectCounter = 1;
            	if(this.lastWrite != null) {
                    this.lastWrite.awaitUninterruptibly();
                }
				this.channel.disconnect().awaitUninterruptibly();
				logger.info("disconnected after sending " + disconnectAfter + " stats as specified in config file.");
            }
        }
    }

    public void disconnect() {
        if(this.lastWrite != null) {
            this.lastWrite.awaitUninterruptibly();
        }
        this.channel.close().awaitUninterruptibly();
        this.bootstrap.releaseExternalResources();
    }

}
