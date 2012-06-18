package org.timconrad.fakecarbon;
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

import org.apache.commons.cli.*;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.concurrent.Executors;

public class Main {

    private int port = 2003;

    public Main(int port) {
        this.port = port;
    }

    public void run(Hashtable<String, String> appConfig) {
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new FakeCarbonPipelineFactory(appConfig));
        bootstrap.bind(new InetSocketAddress(port));
    }

    public void setPort(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(Main.class);
        Hashtable<String,String> appConfig = new Hashtable<String,String>();

        CommandLineParser parser = new PosixParser();
        Options options = new Options();

        options.addOption("p", "port", true, "Port to run on (default:2003)");
        options.addOption("D", "displayAll", false, "Log all incoming packets");
        options.addOption("d", "displayBad", false, "Log only incoming malformed packets");
        options.addOption("h", "help", false, "show this help");
        int somePort = 2003;

        try{
            CommandLine line = parser.parse(options, args);
            if(line.hasOption("help")){
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("FakeCarbon.jar", options);
                System.exit(0);
            }

            if(line.hasOption("port")) {
                System.out.println("Say nothing, act casual");
                somePort = Integer.parseInt(line.getOptionValue("port"));
            }

            if(line.hasOption("displayAll")){
                appConfig.put("displayAll", "true");
            }else{
                appConfig.put("displayAll", "false");
            }

            if(line.hasOption("displayBad")){
                appConfig.put("displayBad", "true");
            }else{
                appConfig.put("displayBad", "false");
            }
        }catch(ParseException e) {
            System.out.println("CLI options exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }

        System.out.println("Fake Carbon listener starting up on port " + somePort);
        new Main(somePort).run(appConfig);
    }
}
