/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi.demo;
import com.augursystems.armi.*;
import java.io.IOException;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.*;

/**
 * The main() class for command line operation; usually just for testing since
 * Armi servers are usually started programmatically within an application.
 * See the documentation for instructions to run a simple test with this.
 * You can execute this command line tool (and see the usage when you don't
 * use any arguments) by running:  <code>java -jar armi.jar</code>
 *
 * @author  Chris.Janicki@AugurSystems.com
 */
public class CommandLine
{
	private static boolean debug;
	/**
	 * Starts up the server from the command line (usually just for testing).
	 * @throws UnknownHostException  if the given host can't found, or if the server can't start on the given port.
	 */
	public static void main(String[] args) throws UnknownHostException, IOException, ArmiException
	{
		GetOpts opts = new GetOpts(args);
		debug = debug || opts.isSet("debug");
		String pw = opts.get("pw");
		final Armi armi = new Armi();

		try
		{
			//if (!opts.isSet("remote")) { armi.shutdown(); usage(); return; }
			HostPort hp = parseHostPort(opts.get("remote"));
			if (opts.isSet("stop"))
			{
				Serializable[] passwordArgs = null;
				if (opts.isSet("pw")) { passwordArgs = new Serializable[] { opts.get("pw") }; }
				System.out.println(armi.call(hp,"Server", "shutdown", passwordArgs ));
				armi.shutdown();
			}
			else if (opts.isSet("start"))
			{
				armi.acceptRemoteClients(hp);

				// Start demo publishers?...
				if (opts.isSet("demo"))
				{
					// Register the utility service for this Armi instance, so that is
					// is accessible for some methods, like shutdown(), listServices(), etc.
					armi.registerService("Server", new ServerService(armi, pw));
					System.out.println("Registered service: Server");
					WorldClockService wc = new WorldClockService();
					armi.registerService("WorldClock", wc);
					System.out.println("Registered service: WorldClock");
					DatePublisher demo1 = new DatePublisher(1000,armi,"1sec");
					DatePublisher demo5 = new DatePublisher(5000,armi,"5sec");
					demo1.start();
					demo5.start();
				}
				armi.join(); // wait for shutdown
			}
			else if (opts.isSet("call"))
			{
				String call = opts.get("call");
				int i = call.lastIndexOf('.');
				String service = call.substring(0,i);
				String method = call.substring(i+1);
				List<String> rems = opts.getRemainders();
				String[] params = new String[rems.size()];
				for (int j=0; j<rems.size(); j++) { params[j] = rems.get(j); }
				Serializable response;
				response = armi.call(hp,service, method, params);
				try { response = armi.call(hp,service, method, params); }
				catch (ArmiException ae) 
				{
					if (ae.getCause()!=null)
					{
						try { response = armi.call(hp,service, method, new Serializable[][] { params } ); }
						catch (ArmiException ae2) { response = "1) " + ae.getCause().toString() +System.getProperty("line.separator")+"2) "+ ae2.getCause().toString(); }
					}
				}
				System.out.println(response);
				armi.shutdown();
			}
			else if (opts.isSet("sub"))
			{
				Client client = new Client()
				{
					public void handlePacket(final Packet p)
					{
						try { System.out.println("Received packet containing date = "+p.decodeInstance()); }
						catch (Exception e) { Armi.log("Problem handling Packet ("+p+")",e); }
					}


					public void abort(String reason)
					{
						System.out.println("Aborted: "+reason);
					}
				};
				if (debug) { System.out.println("Trying to subscribe to: type="+opts.get("sub")+", flavor="+opts.get("flavor")); }
				Subscription sub = armi.subscribe(opts.get("sub"), opts.get("flavor"), null, client, hp);
				if (debug) { System.out.println("Received subscription confirmation: "+sub); }
				final Object waiter = new Object();
				synchronized (waiter) 
				{ 
					while(true) 
					{
						try { waiter.wait(); } // hang forever, until program terminated
						catch (InterruptedException ie) { } 
					}
				}
			}
			else
			{
				armi.shutdown();
				usage();
			}
		}
		catch (java.net.ConnectException ce)
		{
			System.out.println("Unable to contact the ARMI Server.");
		}
		catch (ArmiException ae)
		{
			if (debug) { Armi.log("Problem in CommandLine", ae); }
			else
			{
				System.out.println(ae.getMessage());
				Throwable cause = ae.getCause();
				if (ae.getCause()!=null)
				{
					String msg = cause.getMessage();
					System.out.println("Caused by: "+cause.getClass().getName()+ (msg==null?"":" ("+msg+")"));
					if (cause instanceof NullPointerException) { Armi.log("NPE in CommandLine", ae); }
				}
			}
			armi.shutdown();
		}
	}
	

	private static void usage()
	{
		System.out.println("ARMI is a remote method invocation library with both synchronous (RMI-like) and");
		System.out.println("asychronous (message bus-like) messaging facilities.  ARMI is an API library;");
		System.out.println("this console interface is usually for testing only.");
		System.out.println();
		System.out.println("Usage: <cmd> -remote [<host>:<port>] -<start|stop>  [-demo] [-pw <password>] [-debug]");
		System.out.println("-remote The host:port of the remote ARMI server");
		System.out.println("-start Starts the server to accept remote connections.");
		System.out.println("-stop  Stops a server.");
		System.out.println("-pw    Optional password set at start; if set, required at stop");
		System.out.println("-demo  Starts the built-in date packet source, at start only");
		System.out.println("-debug  Enabes extra feedback, if any available");
		System.out.println();
		System.out.println("Usage: <cmd> -remote <host>:<port> -sub <type> [-flavor <flavor>] [-debug]");
		System.out.println("-remote The host:port of the remote ARMI server");
		System.out.println("-sub    Subscribes to receive date packets from a server");
		System.out.println("-flavor A specific subtype, to further filter the type");
		System.out.println("\""+Date.class.getName()+"\" is the demo periodic date packet type.");
		System.out.println("\"1sec\" and \"5sec\" are the two example flavors.");
		System.out.println("-debug  Enabes extra feedback, if any available");
		System.out.println();
		System.out.println("Usage: <cmd> -remote <host>:<port> -call <service>.<method> [<arg1> <arg2> ...] [-debug]");
		System.out.println("-remote The host:port of the remote ARMI server");
		System.out.println("-call   Executes a remote method and prints the result");
		System.out.println("-debug  Enabes extra feedback, if any available");
		System.out.println("\"Server.hello\" is a built-in server health test method");
		System.out.println("\"Server.listSubscriptions\" lists all current subscriptions");
		System.out.println();
	}



	private static HostPort parseHostPort(String s) throws UnknownHostException
	{
		if (s==null) { return new HostPort((String)null, Armi.DEFAULT_PORT); }
		else { return new HostPort(s); }
	}

	
	
}
