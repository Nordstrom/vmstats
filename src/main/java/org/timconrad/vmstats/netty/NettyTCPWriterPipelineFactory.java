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
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

import static org.jboss.netty.channel.Channels.*;

import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyTCPWriterPipelineFactory implements ChannelPipelineFactory {
    final ClientBootstrap bootstrap;
    private final Timer timer;
    private Channel channel;
    private static final Logger logger = LoggerFactory.getLogger(NettyTCPWriterPipelineFactory.class);

    public NettyTCPWriterPipelineFactory(ClientBootstrap bootstrap, Channel channel, Timer timer) {
        this.bootstrap = bootstrap;
        this.timer = timer;
        this.channel = channel;
    }

    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();
        pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
        pipeline.addLast("decoder", new StringDecoder());
        pipeline.addLast("encoder", new StringEncoder());
        pipeline.addLast("handler", new NettyTCPWriterHandler(this.bootstrap, this.channel, this.timer));
        return pipeline;
    }
}
