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

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

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

    public NettyTCPWriter(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        this.bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        this.bootstrap.setPipelineFactory(new NettyTCPWriterPipelineFactory());
        // TODO: do some exception handling here
        this.future = this.bootstrap.connect(new InetSocketAddress(this.host, this.port));
        this.channel = this.future.awaitUninterruptibly().getChannel();

        if(!future.isSuccess()){
            future.getCause().printStackTrace();
            this.bootstrap.releaseExternalResources();
            logger.info("NettyTCP: future unsuccessful");
            System.exit(900);
        }
    }

    public void sendOne(String input) {
        if(!input.contains("\n")){
            input = input + "\n";
        }
        this.lastWrite = this.channel.write(input);
    }

    public void sendMany(String[] inputs) {
        for(String input : inputs) {
            if(!input.contains("\n")) {
                input = input + "\n";
            }
            this.lastWrite = this.channel.write(input);
        }
    }

    public void disconnect() {
        if(lastWrite != null) {
            lastWrite.awaitUninterruptibly();
        }
        channel.close().awaitUninterruptibly();
        this.bootstrap.releaseExternalResources();
    }

}
