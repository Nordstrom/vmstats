package org.timconrad.vmstats;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphiteUDPWriter {
	
	private InetAddress host;
	private int port;
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
	}
	
	public void sendOne(String input) {
		/**
		 * 
		 * Sends a single record via UDP to Graphite
		 * 
		 * @input - properly formated graphite string this.tag 0 <unixtime
		 */
		String sendThis;
		
		// a bit awkward, but need a \n, and other fixup stuff
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
		String sendThis;

		if(enableSend) {
			try {
				this.connect();
				for(int i=0; i < input.length; i ++) {
					// a bit awkward, but need a \n, and other fixup stuff
					// could happen here
					if(!input[i].contains("\n")) {
						logger.debug("String " + input[i] + " does not contain newline, adding one");
						sendThis = input[i] + "\n";
					}else{
						sendThis = input[i];
					}
					byte[] outBuffer = new byte[sendThis.length() + 1];
					outBuffer = sendThis.getBytes();
					
					DatagramPacket udpPacket = new DatagramPacket(outBuffer, outBuffer.length, this.host, this.port);
					this.socket.send(udpPacket);
				}
				this.socket.close();
				
			} catch (IOException e) {
				logger.info("Could not send data: " + input);
				e.printStackTrace();
			}
		}
	}

}
