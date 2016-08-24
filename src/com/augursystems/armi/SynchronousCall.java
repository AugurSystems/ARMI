/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Chris.Janicki@AugurSystems.com
 */
class SynchronousCall extends Object implements Externalizable
{
	private static final AtomicInteger callCount = new AtomicInteger(0);

	/** The name of a service that is registered to handle calls of this service */
	protected String service;

	/** The Java method to call on service */
	protected String method;

	/**
	 * A unique serial number for this call instance only; used to deliver the response.
	 */
	protected int serial;

	/** The arguments to pass on the method call */
	protected Serializable[] args;

	/** This queuing system is just a way to block the invoke() call until the
	 * Receiver thread gets the response.  Since everything is blocked by the
	 * synchronous calling, the queue never has more than one entry.
	 */
	private transient final BlockingQueue<SynchronousResponse> responses = new LinkedBlockingQueue<SynchronousResponse>();
	private transient final Object CALL_LOCK = new Object();
	private transient Interruptor inter;

	public SynchronousCall()
	{
		super();
	}

	/**
	 * @param service  The ArmiString that should match the name of a service registered
	 * on the remote server.
	 * @param method  The Java method to call.
	 * @param args  The Serializable[] of arguments to pass on the method call; may be null
	 */
	SynchronousCall(String service, String method, Serializable[] args)
	{
		this.serial = callCount.incrementAndGet();
		this.service = service;
		this.method = method;
		this.args = args;
	}

	
	/**
	 * @serialData Writes 1) the UTF String 'service'; 2) the UTF String 'method';
	 * 3) the UTF String 'flavor'; 4) the default ObjectOutputStream encoding of
	 * the args[].
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeUTF(service);
		//System.out.println("SynchronousCall wrote service="+service);
		out.writeUTF(method);
		//System.out.println("SynchronousCall wrote method="+method);
		out.writeInt(serial);
		//System.out.println("SynchronousCall wrote flavor="+flavor);
		out.writeObject(args);
		//System.out.println("SynchronousCall wrote args="+args);
	}


	/** Implements Externalizable */
	public void readExternal(ObjectInput in)  throws IOException, ClassNotFoundException
	{
		service = in.readUTF();
		//System.out.println("SynchronousCall read service="+service);
		method = in.readUTF();
		//System.out.println("SynchronousCall read method="+method);
		serial = in.readInt();
		//System.out.println("SynchronousCall read serial="+serial);
		args = (Serializable[])in.readObject();
		//System.out.println("SynchronousCall read args="+args);
	}


	@Override	public boolean equals(Object obj)
	{
		if (obj instanceof SynchronousCall)
		{
			SynchronousCall call = (SynchronousCall)obj;
			return
				service.equals(call.service) &&
				method.equals(call.method) &&
				(
					(args==null && call.args==null) ||
					(args!=null && call.args!=null && (args.length == call.args.length))
				); 
			// TODO: This isn't really enough... args could be diff classes,
			// but we don't currently use this method.
		}
		else { return false; }
	}


	@Override	public int hashCode()
	{
		return service.hashCode()+method.hashCode()+(args==null?0:args.length);
	}


	@Override public String toString()
	{
		return service+"."+method+"("+(args==null?"":Arrays.toString(args))+")";
	}


	/** 
	 * Called by ArmiRemote when it receives a SynchronousResponse from the Receiver for the 
	 * remote ARMI server, in response to our previous invoke call.
	 */
	void handleResponse(SynchronousResponse res)
	{
		responses.add(res); // will wake the caller's thread, which should be waiting on getResponse().
	}


	/**
	 * Called by the client's thread; blocks until the response is received from
	 * the remote server.
	 * 
	 * @return The SynchronousResponse received from the remote server.
	 * @throws InterruptedException if there was a problem; see message of exception for reason
	 */
	protected SynchronousResponse getResponse() throws InterruptedException
	{
		while (true)
		{
			synchronized(CALL_LOCK)
			{
				inter = Interruptor.scheduleInterrupt(Thread.currentThread(), Armi.DEFAULT_CALL_TIMEOUT);
			}
			try { return responses.take(); }
			catch (InterruptedException ie) 
			{ 
				if (inter.getReason()!=null) throw inter.getReason();
				// else keep waiting.... spurious interruption?  Shouldn't happen, I think.
			} 
			finally // cancel the wake-up call in case still pending
			{ 
				synchronized(CALL_LOCK) { if (inter.isAlive()) inter.cancel(); }
			} 
		}
	}


	/**
	 * Called by the remote server to execute the call; done so in a separate
	 * thread created here.
	 *
	 * @param localServer  The Armi reference for invoking the method call
	 * @param remoteServer  The ArmiRemote where the result will be sent
	 */
	final void start(final Armi localServer, final ArmiRemote remoteServer)
	{
		CallThread t = new CallThread(localServer, remoteServer, this);
		t.start();
	}


	public void abort(String reason) throws IllegalStateException
	{
		if (Armi.debug) System.out.println("SynchronousCall.abort() for: "+reason);
		synchronized(CALL_LOCK)
		{
			if (inter!=null) { inter.interruptNow(reason); }//throws IllegalStateException
		}
	}
	

}
