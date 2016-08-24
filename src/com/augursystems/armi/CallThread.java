/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.IOException;
import java.io.Serializable;

/**
 * This thread invokes a method on behalf of a remote caller.
 * The called method may want information about the
 * remote caller (e.g. to log, or verify the caller's JVM is authorized).
 * In that case, the method can cast its current thread to this
 * CallThread, then call getCallersHostPort():
 * <pre><code>
 * Thread t = Thread.currentThread();
 * if (t instanceof CallThread)
 * {
 *   CallThread ct = (CallThread)t;
 *   HostPort caller = ct.getCallersHostPort();
 *   // ...
 * }
 * </code></pre>
 * Note also that getName() will return a String with the following format:
 * <code>[serviceName].[methodName]([arg1],[arg2],...) called from Remote ARMI @ [host]:[port]</code>
 *
 * @author Chris.Janicki@AugurSystems.com
 * Copyright 2010 Augur Systems, Inc.  All rights reserved.
 */
public class CallThread extends Thread
{
final Armi localServer;
final ArmiRemote remoteServer;
final SynchronousCall call;

CallThread(final Armi localServer, final ArmiRemote remoteServer, final SynchronousCall call)
{
	super(call+" called from "+remoteServer);
	setDaemon(true);
	this.localServer = localServer;
	this.remoteServer = remoteServer;
	this.call = call;
}

/** This is the thread's method; DO NOT CALL IT DIRECTLY. */
@Override public void run()
{
	Serializable result = localServer.invoke(call, remoteServer);
	SynchronousResponse response = new SynchronousResponse(call.serial, result);
	try
	{
		remoteServer.transmit(response);
	}
	catch (IOException ioe)
	{
		if (localServer.isRunnable()) Armi.log("Problem sending call's response back to remote server ("+remoteServer+"): "+result);
	}
}


/**
 * @return The HostPort of the remote ARMI server that invoked your method.
 */
public HostPort getCallersHostPort()
{
	return remoteServer.hostPort;
}



}
