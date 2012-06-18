package org.timconrad.fakecarbon;// this is the file header.

import static org.jboss.netty.channel.Channels.*;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.concurrent.Executors;

public class FakeCarbonPipelineFactory implements ChannelPipelineFactory {

    private final Hashtable<String, String> appConfig;

    public FakeCarbonPipelineFactory(Hashtable<String, String> appConfig) {
        this.appConfig = appConfig;
    }

    public ChannelPipeline getPipeline() throws Exception {
        // this is pretty verbose from the netty examples
        ChannelPipeline pipeline = pipeline();
        pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
        pipeline.addLast("decoder", new StringDecoder());
        pipeline.addLast("encoder", new StringEncoder());
        pipeline.addLast("handler", new FakeCarbonHandler(appConfig));
        return pipeline();
    }

}
