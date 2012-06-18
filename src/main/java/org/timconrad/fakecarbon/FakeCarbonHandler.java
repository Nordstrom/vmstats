package org.timconrad.fakecarbon;// this is the file header.

import org.jboss.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

public class FakeCarbonHandler extends SimpleChannelUpstreamHandler {

    private final Hashtable<String, String> appConfig;
    private static final Logger logger = LoggerFactory.getLogger(FakeCarbonHandler.class);
    private int count = 0;

    public FakeCarbonHandler(Hashtable<String, String> appConfig) {
        this.appConfig = appConfig;
    }
    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if(e instanceof ChannelStateEvent){
            System.out.println("ChannelStateEvent: " + e.toString());
        }
        super.handleUpstream(ctx,e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // do nothing
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        String request = (String) e.getMessage();
        if(appConfig.get("displayAll").contains("true")){
            logger.info("packet(" + count + "): " + request);
        }
        count++;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        System.out.println("Unexpected exception from downstream: " + e.getCause());
        e.getChannel().close();
    }
}
