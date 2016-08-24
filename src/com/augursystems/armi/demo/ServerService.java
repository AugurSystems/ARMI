/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi.demo;
import com.augursystems.armi.Armi;
import java.util.ArrayList;

/**
 * This is instantiated as a service when the Armi is started from the
 * command line.  (If Armi is started programmatically, then that server
 * instance's methods can be called directly.)
 * 
 * @author Chris.Janicki@AugurSystems.com
 */
public class ServerService extends Object
{
	private static final String DONE = "Done.";
	private static final String DENY = "Permission denied.";
	private static final String HELLO = "Hi!  Nice talking with you.";
	private Armi server;
	private String pw;
	private final Object SHUTDOWN_WAIT_LOCK = new Object();

	/**
	 * @param password  An optional String to protect calls to the "Armi" service
	 * that can be used to shutdown this server; if non-null then any calls to this
	 * service will silently fail if the password passed as a method parameter
	 * does not match.
	 */
	protected ServerService(Armi server, String pw)
	{
		super();
		this.server = server;
		this.pw = pw;
	}


	/** This is a method that may be called by an ARMI client */
	public String hello()
	{
		return HELLO;
	}

	/** This is a method that may be called by an ARMI client */
	public String hello(String arg)
	{
		return "Who are you calling "+arg+"?  I am \"Mr. ARMI\" to you!";
	}


	public ArrayList listServices()
	{
		return server.getServiceNames();
	}

	public ArrayList listSubscriptions()
	{
		return server.getSubscriptionDescriptions();
	}
	
	
	/**
	 * This method will block until the ARMI server is well on its way (past the 
	 * point of no return) for a complete shutdown.  The caller may receive an
	 * exception since the call probably won't be able to cleanly return, but 
	 * that should be expected, and taken as a positive indication of shutdown.
	 */
	public void waitForShutdown()
	{
		synchronized(SHUTDOWN_WAIT_LOCK)
		{
			while (server.isRunnable()) 
			{
				try {  SHUTDOWN_WAIT_LOCK.wait(); } 
				catch(InterruptedException ie) { }
			}
		}
	}
	

	/** 
	 * This is no-argument option to shutdown the server;
	 * only works if the server password was not set.
	 */
	public String shutdown()
	{
		return shutdown(null);
	}

	/**
	 * This is password-protected option to shutdown the server;
	 * only works if the server password was set and matches.
	 */
	public String shutdown(String password)
	{
		if (isPasswordOK(password))
		{
			Armi.log("Shutting down "+server.toString()+" ... ");
			Thread t = new Thread()
			{
				@Override public void run()
				{
					try { Thread.sleep(2500); } catch (InterruptedException ie) { }
					server.shutdown();
					synchronized(SHUTDOWN_WAIT_LOCK) { SHUTDOWN_WAIT_LOCK.notifyAll(); }
				}
			};
			t.start();
			return DONE;
		}
		else { return DENY; }
	}


private boolean isPasswordOK(String password)
{
	if (pw==null)
	{
		if (password==null) { return true; }
		else { return false; } // was not expecting a password
	}
	else if (password != null)
	{
		return pw.equals(password);
	}
	else { return false; }
}


}
