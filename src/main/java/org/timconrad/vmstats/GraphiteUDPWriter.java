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

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GraphiteUDPWriter {
	
	private InetAddress host;
	private final int port;
	private Boolean enableSend = true;
	private DatagramSocket socket = null;
	private static final Logger logger = LoggerFactory.getLogger(GraphiteUDPWriter.class);
	
	public GraphiteUDPWriter(String someHost, int port) {
		/**
		 * This is the constructor 
		 * 
		 * @host - the host to connect to
		 * @int - the port number (should be 2003)
		 */
		try {
			this.host = InetAddress.getByName(someHost);
		} catch (UnknownHostException e) {
			logger.info("Unknown host: " + host);
			logger.debug("Unknown host exception: " + e);
		}
		this.port = port;
	}
	
	public void setEnableSend(Boolean enableSend) {
		this.enableSend = enableSend;
	}
	
	public Boolean getEnableSend() {
		return enableSend;
	}
	
	private void connect() throws IOException {
		/**
		 * Connect to the graph server, only used internally
		 */
		this.socket = new DatagramSocket();
		this.socket.connect(host, port);
        this.socket.setSendBufferSize(458752);
	}
	
	public void sendOne(String input) {
		/**
		 *
		 * Sends a single record via UDP to Graphite
		 * 
		 * @input - properly formatted graphite string this.tag 0 < unix time
		 */
		String sendThis;
		
		// a bit awkward, but need a \n, and other fix-up stuff
		// could happen here
		if(!input.contains("\n")) {
			logger.debug("String " + input + " does not contain newline, adding one");
			sendThis = input + "\n";
		}else{
			sendThis = input;
		}
		byte[] outBuffer = new byte[sendThis.length() + 1];
		outBuffer = sendThis.getBytes();
		
		if(enableSend) {
			try {
				this.connect();
				DatagramPacket udpPacket = new DatagramPacket(outBuffer, outBuffer.length, this.host, this.port);
				this.socket.send(udpPacket);
				this.socket.close();
				
			} catch (IOException e) {
				logger.info("Could not send data: " + input);
				e.printStackTrace();
			}
		}
	}
	
	public void sendMany(String[] input) {
		/**
		 * Loop through an array of strings and send all of them
		 * @input - a String array of properly formatted graphite data
		 */
        int count = 1;

		if(enableSend) {
			try {
				this.connect();
                String sendLine;
                String sendThis = "";
                for (String anInput : input) {
                    // a bit awkward, but need a \n, and other fixup stuff
                    // could happen here
                    if (!anInput.contains("\n")) {
                        logger.debug("String " + anInput + " does not contain newline, adding one");
                        sendLine = anInput + "\n";
                    } else {
                        sendLine = anInput;
                    }

                    sendThis += sendLine;

                    if ( count == 6) {
                        byte[] outBuffer = new byte[sendThis.length() + 1];
                        outBuffer = sendThis.getBytes();
                        DatagramPacket udpPacket = new DatagramPacket(outBuffer, outBuffer.length, this.host, this.port);
                        try {
                            this.socket.send(udpPacket);
                        }catch(PortUnreachableException e){
                            System.out.println("Graphite port unreachable");
                            System.exit(0);
                        }
                        sendThis = "";
                        count = 1;
                    }else{
                        count++;
                    }
                }
                if(sendThis.length() > 0) {
                    byte[] outBuffer = new byte[sendThis.length() + 1];
                    outBuffer = sendThis.getBytes();
                    DatagramPacket udpPacket = new DatagramPacket(outBuffer, outBuffer.length, this.host, this.port);
                    this.socket.send(udpPacket);
                }
				this.socket.close();
				
			} catch (IOException e) {
				logger.info("Could not send data: " + Arrays.toString(input));
				e.printStackTrace();
			}
		}
	}

}
