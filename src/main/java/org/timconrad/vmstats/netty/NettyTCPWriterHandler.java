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
import org.jboss.netty.channel.*;

import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class NettyTCPWriterHandler extends SimpleChannelUpstreamHandler {
    final ClientBootstrap bootstrap;
    private final Timer timer;
    private Channel channel;
    private ChannelFuture future;

    private static final Logger logger = LoggerFactory.getLogger(NettyTCPWriterHandler.class);

    public NettyTCPWriterHandler(ClientBootstrap bootstrap, Channel channel, Timer timer) {
        this.bootstrap = bootstrap;
        this.timer = timer;
        this.channel = channel;
    }

    InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) bootstrap.getOption("remoteAddress");
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        logger.info("Channel connected to graphite @ " + getRemoteAddress());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        logger.info("Channel disconnected @ " + getRemoteAddress());
    }

    @Override
    public void channelClosed (ChannelHandlerContext ctx, ChannelStateEvent e) {
        logger.info("Channel closed @ " + getRemoteAddress());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        // this should never happen don't know if i need this.
        logger.info("messageRecieved" + e.getMessage().toString());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.info("unexpected exception from downstream " + e.getCause());
        Throwable cause = e.getCause();
        cause.printStackTrace();
        ctx.getChannel().close();
    }

}
