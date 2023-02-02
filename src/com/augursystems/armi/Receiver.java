/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads from a socket connection;
 * the socket may be initiated by a client,
 * or the result of a connection from this server to a remote server (on behalf
 * of a client of course).
 * @author Chris.Janicki@AugurSystems.com
 */
final class Receiver extends Thread
{
	private static final String SUBSCRIBER = com.augursystems.armi.SubscriberRemote.class.getName();
	private static final String CALL = com.augursystems.armi.SynchronousCall.class.getName();
	private static final String RESPONSE = com.augursystems.armi.SynchronousResponse.class.getName();
	private boolean runnable = true;
	private ArmiRemote armiRemote = null;
	private Armi armi;
	private final String clientName;
	private final List<Subscriber> subs = new ArrayList<Subscriber>(); // access must be synchronized
	private Socket socket;
	private ArmiInputStream ais;

	/** This constructor is used only by the ArmiRemote constructor */
	Receiver(Armi armi, ArmiRemote armiRemote, Socket socket) throws IOException
	{
		super();
		setDaemon(true);
		clientName = armiRemote.hostPort.toString();
		setName("ARMI Receiver @ "+clientName);
		this.armiRemote = armiRemote;
		this.armi = armi;
		this.socket = socket;
	}


	final boolean isRunnable()
	{
		return runnable;
	}

	
	@Override public final void run()
	{
		if (Armi.debug) { System.out.println("Starting... "+getName()); }
		try { this.ais = new ArmiInputStreamHeaderless(socket.getInputStream()); } // superclass ObjectInputStream's constructor will read the stream header here
		catch (IOException ioe)
		{
			Armi.log(Thread.currentThread().getName()+"> Problem opening stream for "+getName()+".", ioe);
			runnable = false;
		}
		while(isRunnable()) // && armi.isRunnable()) ...is not true for pure clients
		{
			try
			{
				if (Armi.debug) { Armi.log(Thread.currentThread().getName()+"> Waiting to read new Packet @ "+System.currentTimeMillis()+"..."); }
				Packet p = (Packet)ais.readObject(); // may block
				if (Armi.debug)
				{
					Armi.log(Thread.currentThread().getName()+"> Receiver> Received packet @ "+System.currentTimeMillis()+": "+p);
					if (p!=null && p.dataType.equals("com.augursystems.armi.ArmiException")) // Note that the ArmiString dataType accepts String as an equals() param
					{
						Serializable e = p.decodeInstance();
						Armi.log("Exception received: "+e);
					}
				}
				if (!isRunnable()) { break; } // check again, since may have blocked above
				if (p.dataType.equals(SUBSCRIBER))
				{
					SubscriberRemote subscriber = (SubscriberRemote)p.decodeInstance();
					subscriber.setOutputStream(armiRemote,clientName); // talk to the remote subscriber through its server
					if (subscriber.subscribe) 
					{
						armi._subscribe(subscriber);
						synchronized(subs) { subs.add(subscriber); }// for GC help after close()
					}
					else { armi.unsubscribe(subscriber); }
				}
				else if (p.dataType.equals(CALL))
				{
					SynchronousCall call = (SynchronousCall)p.decodeInstance();
					if (Armi.debug) { Armi.log("Receiver> Will call: " + call); }
					call.start(armi, armiRemote);// Start the thread to make call and reply
				}
				else if (p.dataType.equals(RESPONSE))
				{
					SynchronousResponse response = (SynchronousResponse)p.decodeInstance();
					if (Armi.debug) { Armi.log("Receiver> got call response: " + response); }
					armiRemote.handleResponse(response);
				}
				else
				{
					armi.publish(p); // find listeners for this dataType and get it there...
				}
			}
			catch (EOFException eofe)
			{
				if (Armi.debug) { Armi.log("Disonnected: "+getName()+" @ "+System.currentTimeMillis());  eofe.printStackTrace(); System.out.println(); }
				if (Armi.debug) { Armi.log("subscriptions = " + armi.getSubscriptionDescriptions()); System.out.println(); }
				if (Armi.debug) { Armi.log("========================================"); }
				close("Remote server disconnected.");
			}
			catch (java.io.StreamCorruptedException sce)
			{
				String m = "Bad packet from "+socket.getInetAddress(); //Logging sce (which shows bad byte) makes log unnecessarily unique (evading logRepeat counter)...// +": "+sce.getMessage();
				if (isRunnable()) Armi.log(m);
				close(m);
			}
			catch (Exception ex)
			{
				if (isRunnable())
				{
					Armi.log("Unexpected problem in "+getName(), ex);
				}
				close("Unexpected problem (" +ex.toString()+")");
			}
		}
		if (Armi.debug){  Armi.log("Shutdown: "+getName()); }
		try { if (ais != null) { ais.close(); } }
		catch (IOException ex)
		{
			Armi.log("Problem closing "+getName()+".", ex);
		}
	}


	/**
	 * Called internally if the socket dies; or by ArmiRemote which then closes
	 * the socket so the I/O will unblock here.
	 */
	void close(String reason)
	{
		if (Armi.debug) System.out.println(getName()+" closing.  isRunnable()="+isRunnable()+", "+reason);
		if (isRunnable())
		{
			runnable = false;
			synchronized(subs) 
			{
				if (Armi.debug) System.out.println("Receiver.close() going to close "+subs.size()+" subs for: " + reason);
				for (Subscriber sub : subs) 
				{
					boolean successfullyRemoved = armi.unsubscribe(sub);
					if (Armi.debug) System.out.println("Receiver.close() unsubscribing subscriber @ "+sub+", successful? "+successfullyRemoved);
					if (Armi.debug) System.out.println("Calling subscriber.close() @ "+sub);
					sub.close(reason); // removes ArmiRemote, to help GC
				}
				subs.clear();
			}
			if (armiRemote.isRunnable()) // may have alread closed if it initiated our shutdown
			{
				armi.closeRemoteServer(armiRemote);
			}
			if (Armi.debug) { Armi.log("Closed "+getName()); }
			armiRemote = null;// help GC...
			armi = null;
		}
	}

	

}
