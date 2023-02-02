/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * The ARMI server orchestrates all communication to/from any number of remote
 * ARMI servers and the local clients/listeners.
 * The ARMI protocol/implementation is an alternative to RMI, with the addition
 * of asynchronous one-to-many messaging via a publish/subscribe mechanism.
 * <p>
 * This Armi class is your main interface for registering "services", calling
 * methods on services, subscribing for asynchronous data, and publishing data.
 * To get started, you need an instance of Armi.  Usually, you only need one per
 * JVM.  With this Armi instance, you can use the call() method to execute methods
 * on a local or remote service, or subscribe for data on a local or remote ARMI server.
 * </p><p>
 * If you want to register a server for remote callers, or publish data for remote
 * subscribers, you first need to call acceptRemoteClients().  That will start a
 * server socket listening for remote requests.  Then you can call registerService()
 * or use publish(), respectively.
 * </p><p>
 * An instance of any class that has public methods with
 * Serializable parameters and Serializable return value
 * can be registered as a 'service'.
 * </p><p>
 * Note: The 'runnable' and 'ss' variables aren't fully protected for thread
 * safety yet, although realistically they are quite safe for normal usage.
 *
 *
 * @author Chris.Janicki@AugurSystems.com
 */
public final class Armi extends Object implements Runnable
{
	public static final String REASON_SHUTDOWN_REQ = "Shutdown requested."; // this will be tested by apps that may verify reason a call failed, e.g. the SS "kill" call.
	private static final Serializable[] NO_ARGS = new Serializable[] { };
	public static final ArmiVoid VOID = ArmiVoid.VOID; // a convenient return value for remote methods
	/**
	 * The default port (1441) for inter-server ARMI communications;
	 * used by the acceptRemoteClients() method, if a null HostPort is specified.
	 */
	public static final int DEFAULT_PORT = 1441;
	public static final int DEFAULT_CALL_TIMEOUT = 10000; // in milliseconds
	public static final int DEFAULT_CONNECT_TIMEOUT = 10000; // in milliseconds
	
	static boolean debug = false;

	/** The optional ServerSocket to receive remote requests; null until start(). */
	private ServerSocket ss;

	/** A flag to control the server socket's accept() thread. */
	private volatile boolean runnable = false;

	/** 
	 * "Transmitters" manage a message queue for one data type and flavor; there is
	 * an automatic 'null' flavor for each type.  Each transmitter
	 * maintains a list of listeners for that data type/flavor; when a message
	 * arrives, it transmits the message to all its listeners.
	 */
	private final Map<String,Map<String,List<Subscriber>>> subscribers = new HashMap<String,Map<String,List<Subscriber>>>();

	/** "Services" are local objects with methods that may be called remotely. */
	private final Map<String,Object> services = new HashMap<String,Object>();

  /** A map of remote ARMI servers. */
	private final Map<HostPort,ArmiRemote> remotes = new HashMap<HostPort,ArmiRemote>();

	/** The thread managing remote conditions; null until acceptRemoteClients() called */
	private Thread thread = null;
	private AccessControl control=null;

	public interface AccessControl { public boolean isAddressAllowed(InetAddress addy); }
	
	public static void log(String desc) { log(desc,null); }

	/** Reusable Date object to avoid instantiation, and doubles as a sync lock to separate log() calls */
	private static final Date LOG_DATE = new Date();
	static volatile String lastLog=""; static volatile int logRepeat=0; static int logRepeatMax = 10;
	@SuppressWarnings({"CallToPrintStackTrace"})
	public static void log(String desc, Throwable exc)
	{
		synchronized(LOG_DATE)
		{
			if (desc==null) { System.out.println(); return; } // log blank line, useful for formatting break?
			if (desc.equals(lastLog)) 
			{ 
				logRepeat++; 
				if (logRepeat >= logRepeatMax) // log progress, bump up limit
				{ 
					if (logRepeatMax<=100000000) 
					{
						logRepeatMax *= 10;
						System.out.println("["+(new Date())+"] ARMI> Last ARMI log repeated "+String.format("%,d",logRepeat)+" times, and counting...");
					} 
					else 
					{
						System.out.println("["+(new Date())+"] ARMI> Last ARMI log repeated "+String.format("%,d",logRepeat)+" times.  Too high, resetting counter.");
						logRepeat=0;
					}
				} 
			}
			else
			{
				if (logRepeat>0) 
				{ 
					String m = "Last ARMI log repeated "+String.format("%,d",logRepeat)+" times.";
					logRepeat=0; logRepeatMax = 10;
					log(m,null); 
				}
				LOG_DATE.setTime(System.currentTimeMillis());
				System.out.println("["+LOG_DATE+"] ARMI> "+desc);
				lastLog = desc;
				if (exc!=null) { exc.printStackTrace(); }
			}
		}
	}

	/**
	 * Creates an Armi instance, ready for remote calls and subscriptions, but not
	 * yet initialized to accept calls/subscriptions from other remote Armi
	 * instances.  See acceptRemoteClients().
	 */
	public Armi()
	{
		super();
	}


	/** 
	 * @return A boolean indicating the state of the remote client listener (when hosting remotely accessible services); 
	 * only TRUE after acceptRemoteClients() is called, and until shutdown() is called.
	 */
	public final boolean isRunnable()
	{
		return runnable;
	}


	/**
	 * Accept connections from remote servers (which are proxies for their local clients)
	 */
	@Override public final void run()
	{
		while (isRunnable())
		{
			try 
			{
				Socket socket = ss.accept();
				InetAddress remoteAddy = socket.getInetAddress();
				int remotePort = socket.getPort();
				if (control!=null && !control.isAddressAllowed(remoteAddy))
				{
					if (true) { log("Denied per access control: "+remoteAddy); }
					socket.close();
					continue;
				}
				if (debug) { log("Accepting connection from remote server @ "+remoteAddy); }
				ArmiRemote remote = new ArmiRemote(new HostPort(remoteAddy, remotePort), this, socket);
				remote.start();
				putRemoteServer(remote);
			}
			catch (Exception e)
			{
				if (isRunnable()) { log("Problem accepting connection from remote server.", e); }
			}
		}
		log("ARMI server is no longer accepting remote connections.");
	}


	/**
	 * Waits until the remote client-accepting thread finishes; used by CommandLine
	 * to make sure the JVM doesn't exit immediately, since the thread is a daemon.
	 */
	public final void join()
	{
		if (isRunnable() && thread != null)
		{
			try { thread.join(); }
			catch (InterruptedException ie) { } // shouldn't happen, if does, then probably shutting down anyway
		}
	}
	

	/**
	 * Finds the list used to store subscribers for the given type and flavor; no
	 * application of custom filtering (the Filter interface) is applied at this
	 * point.  Consider this a simple query on a two-keyed database.  Note that 
	 * the returned list is actually the backing dataset; changes (e.g. adding
	 * subscribers) will affect the whole system, and should be sync'd by locking 
	 * the 'subscribers' object.
	 * 
	 * @param type
	 * @param flavor 
	 * @param shouldCreate  A boolean indicating that the given type/flavor list
	 * should be created if it does not already exist.
	 * @return A List<Subscriber> that holds the current list of subscriptions for 
	 * the given type/flavor; this is the *actual* list, so changes affect the 
	 * whole system, and should be sync'd by locking the 'subscribers' object.
	 */
	private List<Subscriber> getSubscribers(String type, String flavor, boolean shouldCreate)
	{
		//if (debug) System.out.println("getTransmitter() flavor = "+flavor);
		synchronized(subscribers)
		{
			Map<String,List<Subscriber>> flavorMap = subscribers.get(type);
			if (flavorMap==null)
			{
				if (shouldCreate)
				{
					flavorMap = new HashMap<String,List<Subscriber>>();
					subscribers.put(type, flavorMap);
				}
				else { return null; }
			}
			List<Subscriber> subs = flavorMap.get(flavor);
			if (subs == null)
			{
				if (shouldCreate)
				{
					subs = new ArrayList<Subscriber>();//(this, type, flavor);
					flavorMap.put(flavor, subs);
				}
				else { return null; }
			}
			return subs;
		}
	}


	/** Called by Receiver when handling a request from a new remote client */
	final void putRemoteServer(ArmiRemote rServer)
	{
		synchronized (remotes)
		{
			remotes.put(rServer.hostPort, rServer);
			if (debug) { log("Put remote server connection: "+rServer+"; servers size now " + remotes.size()); }
		}
	}


	/**
	 * Uses the DEFAULT_CONNECT_TIMEOUT (5000ms) when trying to establish a connection.
	 * 
	 * @param hostPort The HostPort where the remote server is located.
	 * @return The non-null ArmiRemote representing a connection to a remote
	 * ARMI server.
	 * @throws java.io.SocketTimeoutException if the remote server cannot be contacted within the default timeout
	 * @throws java.io.IOException if the remote server cannot be contacted for any other reason
	 */
	final ArmiRemote openRemoteServer(HostPort remoteHostPort) throws IOException
	{
		return openRemoteServer(remoteHostPort, DEFAULT_CONNECT_TIMEOUT);
	}
	
	
	/**
	 * @param hostPort The HostPort where the remote server is located.
	 * @param timeoutMillis  The number of milliseconds allowed to establish a 
	 *   connection with the remote server, else a SocketTimoutException is thrown;
	 *   a zero value means no explicit time-out.
	 * @return The non-null ArmiRemote representing a connection to a remote
	 * ARMI server.
	 * @throws java.io.SocketTimeoutException if the remote server cannot be contacted within the specified timeout
	 * @throws java.io.IOException if the remote server cannot be contacted for any other reason
	 */
	final ArmiRemote openRemoteServer(HostPort remoteHostPort, int timeoutMillis) throws IOException
	{
		synchronized (remotes)
		{
			ArmiRemote rs = remotes.get(remoteHostPort);
			if (rs==null)
			{
				if (remoteHostPort.getPort()==0) { throw new IOException("Remote port can't be zero."); }
				InetSocketAddress isa = new InetSocketAddress(remoteHostPort.getHost(), remoteHostPort.getPort());
				Socket socket = new Socket();
				socket.connect(isa, timeoutMillis);
				rs = new ArmiRemote(remoteHostPort, this, socket);
				rs.start();
				putRemoteServer(rs);
			}
			return rs;
		}
	}


	/**
	 * @param hostPort The HostPort where the remote server is located.
	 * @return The possibly null (if not already cached) ArmiRemote representing
	 * a connection to a remote ARMI server.
	 */
	final ArmiRemote getRemoteServer(HostPort remoteHostPort)
	{
		synchronized (remotes)
		{
			return remotes.get(remoteHostPort);
		}
	}


	final void closeRemoteServer(ArmiRemote rServer)
	{
		if (rServer.isRunnable())
		{
			synchronized (remotes)
			{
				rServer.shutdown();
				rServer = remotes.remove(rServer.hostPort);
				if (debug) { log("Removed remote server connection: "+rServer+"; servers size now " + remotes.size()); }
			}
			// REMOVE ALL LOCAL SUBS WAITING ON THIS REMOTE SERVER...
			synchronized(subscribers)
			{
				for (Map<String,List<Subscriber>> subsForType : subscribers.values())
				{
					for (List<Subscriber> subs : subsForType.values())
					{
						Subscriber s;
						for (ListIterator<Subscriber> lit = subs.listIterator(); lit.hasNext(); )
						{
							s = lit.next();
							if (s instanceof SubscriberLocal)
							{
								SubscriberLocal sl = (SubscriberLocal)s;
								sl.client.abort("Remote server closing.");
								lit.remove();
							}
						}
					}
				}
			}
		}
	}


	/**
	 * Invokes a method on a registered service object.  Used by CallThread.
	 * @param call  The SynchronousCall containing the service/method name and arguments.
	 * @param remoteServer  The ArmiRemote that wants the result.
	 */
	final Serializable invoke(SynchronousCall call, ArmiRemote remoteServer)
	{
		Object service;
		Serializable response;
		synchronized(services)
		{
			service = services.get(call.service);
		}
		if (service==null)
		{
			response = new ArmiException("Service not found: "+call.service);
		}
		else
		{
			Serializable[] args = call.args;
			Class[] classes = null;
			if (args!=null)
			{
				classes = new Class[args.length];
				for (int i=args.length-1; i>=0; i--)
				{
					classes[i] = args[i].getClass();
				}
			}
			try
			{
				if (debug) { System.out.println("Looking up method=\""+call.method+"\" with args="+classes); }
				Method m = service.getClass().getMethod(call.method, classes);
				if (Modifier.isPublic(m.getModifiers()))
				{
					if (Serializable.class.isAssignableFrom(m.getReturnType()))
					{
						// INVOKE!...
						if (debug) { System.out.println("Going to invoke: "+call); }
						if (m.isVarArgs()) response = (Serializable)m.invoke(service, (Object)args);
						else response = (Serializable)m.invoke(service, (Object[])args);
						if (debug) { System.out.println("Got response (serial# "+call.serial+"): "+response); }
					}
					else
					{
						response = new ArmiException("Method does not return Serializable object; won't execute: "+call);
					}
				}
				else
				{
					response = new ArmiException("Method is not public; won't execute: "+call);
				}
			}
			catch(NoSuchMethodException nsme)
			{
				response = new ArmiException("Method not found: "+call.service+"."+call.method+Arrays.toString(classes));
			}
			catch(InvocationTargetException ite)
			{
				response = new ArmiException("Problem reported by service during method call.", ite.getCause());
			}
			catch(Throwable e)
			{
				if (isRunnable())
				{
					log ("Invocation problem for "+call, e);
					response = new ArmiException("Invocation problem for "+call, e);
				}
				else response = null;
			}
		}
		return response;
	}


/**
 * The List&lt;Serializable&gt; of service names (actually the whole list is String).
 * Used by the "Armi" service.
 */
	public ArrayList<Serializable> getServiceNames()
	{
		synchronized(services)
		{
			return new ArrayList<Serializable>(services.keySet());
		}
	}


/**
 * The List&lt;Serializable&gt; of subscriptions types currently tracked;
 * actually the whole list is Strings, of the general format:
 * "[type]/[flavor]".
 * Used by the "Armi" service.
 */
	public ArrayList<Serializable> getSubscriptionDescriptions()
	{
		ArrayList<Serializable> list = new ArrayList<Serializable>();
		synchronized(subscribers)
		{
			for (String type : subscribers.keySet())
			{
				for (String flavor : subscribers.get(type).keySet())
				{
					for (Subscriber s : subscribers.get(type).get(flavor))
					{
						list.add(s.toString());
					}
				}
			}
		}
		return list;
	}


	/**
	 * Shuts down any non-daemon threads, closes sockets, and cleans up memory.
	 * If called from main(), this should likely exit the JVM.
	 */
	public final void shutdown()
	{
		if (debug) { System.out.println("Server.shutdown()!!!"); }
		runnable = false;
		synchronized(subscribers)
		{
			for (Map<String,List<Subscriber>> flavorMap : subscribers.values())
			{
//				for (List<Subscriber> subs : flavorMap.values()) { subs.shutdown(); }
				flavorMap.clear();
			}
			subscribers.clear();
		}
		synchronized(remotes)
		{
			for (ArmiRemote rs : remotes.values()) { rs.shutdown(); }
			remotes.clear();
		}
		if (ss!=null)
		{
			try { ss.close(); }
			catch (IOException ioe) { log("Problem during shutdown.", ioe); }
		}
		if (thread!=null) 
		{
			try { thread.join(); } catch (InterruptedException ie) { } 
		}
	}


	/**
	 * Convenience method that will wrap the Serializable in an ARMI Packet and
	 * transmit to all subscribers of its type.
	 * @param a  The Serializable object to transmit to all subscribers of its type.
	 */
	public final void publish(Serializable a)
	{
		publish(a,null);
	}


	/**
	 * Convenience method that will wrap the Serializable in an ARMI Packet and
	 * transmit to all subscribers of its type.
	 * @param a  The Serializable object to transmit to all subscribers of its type.
	 */
	public final void publish(Serializable a, String flavor)
	{
		 publish(new Packet(a, flavor));
	}


	/**
	 * Called by a local broadcaster, or a remote client via a Receiver.
	 * Will transmit the packet to matching flavored subscribers, and also to 
	 * non-flavored subscriptions regardless of the packet's flavor.
	 *
	 * @param p  The Packet to be transmitted to all subscribers of its service.
	 */
	public final void publish(Packet p)
	{
		// REMEMBER THAT getSubscribers() RETURNS THE BACKING LIST... DONT CHANGE IT
		List<Subscriber> subs = getSubscribers(p.dataType, p.dataFlavor, false);

		if (debug) { System.out.println("Transmitting ("+p.dataFlavor+" flavor) packet "+p+" ("+p.instance+") to "+(subs==null?0:subs.size())+" subscribers"); }
		publish(p, subs);
		// generalize (remove flavor) to reach subscribers that didn't specify a
		// flavor, unless the packet is ephemeral (response from method call)...
		String flavor = p.dataFlavor; // may be null
		if (flavor!=null) // only if not already generalized
		{
			subs = getSubscribers(p.dataType, null, false);
			if (debug) { System.out.println("Transmitting (null flavor) packet "+p+" to "+(subs==null?0:subs.size())+" subscribers"); }
			publish(p, subs);
		}
	}

	private void publish(Packet p, List<Subscriber> subs)
	{
		if (subs!=null)
		{
			synchronized(subs)
			{
				for (Subscriber sub : subs)
				{
					if (sub.accepts(p)) // apply filter
					{
						try { sub.transmit(p); }
						catch (IOException ioe)
						{
							if (sub instanceof SubscriberRemote)
							{
								SubscriberRemote subRemote = (SubscriberRemote)sub;
								subRemote.remoteServer.shutdown();
								// TODO: the remoteServer.shutdown() call will remove all its subs... 
								// Won't that cause a co-modification error on the list traversal here?
							}
						}
					}
				}
			}
		}
	}


	/**
	 * Subscribe to a data type/flavor published on this and/or a remote ARMI server.
	 * Any subsequent matching data packets will be sent to the given Client.
	 *
	 * @param hp The HostPort of the remote ARMI server;
	 * null if you are only subscribing to this local Armi instance.
	 *
	 * @param type The String 'type' of data to receive; usually this is the fully
	 * qualified class name of the object.  Must not be null.
	 *
	 * @param flavor The String 'flavor' of data to receive; null if you want all flavors.
	 *
	 * @param filter  An optionally null Filter that further limits the subscription;
	 * if the Filter is also an instance of Serializable, it is sent to the remote server
	 * so that it may prevent unnecessary transmissions over the network; in either
	 * case, the filter is always applied at the local server too.
	 *
	 * @param client  The Client implementation where matching data should be sent
	 * when published.
	 *
	 * @return Your Subscription receipt; hold on to it; you'll need it if you wish to cancel later.
	 */
	public final Subscription subscribe(String type, String flavor, Filter filter, Client client, HostPort hp) throws ArmiException
	{
		SubscriberRemote rsub = null;
		SubscriberLocal lsub = null;
		ArmiRemote remote = null;
		if (type!=null)
		{
			// register with the local server (proxy for the client)...
			lsub = new SubscriberLocal(type, flavor, filter, client);
			_subscribe(lsub);
			if (hp != null) // register with the remote server...
			{
				rsub = new SubscriberRemote(type, flavor, filter instanceof Serializable ? filter : null);
				try
				{
					if (debug) { System.out.println("Transmitting subscription to remote server..."); }
					remote = openRemoteServer(hp); // may throw IOException
					remote.transmit(new Packet(rsub));
					if (debug) { System.out.println("Done transmitting."); }
				}
				catch(SocketTimeoutException ste) { throw new ArmiException("Time-out expired while connecting to "+hp); }
				catch(Exception e) { throw new ArmiException("Problem transmitting subscription to remote server.", e); }
			}
		}
		return new Subscription(this, lsub, remote, rsub);
	}

	/**
	 * Adds a subscriber to this Armi instance.
	 * Used internally, by Armi.subscribe() (which sends a SubscriberLocal),
	 * and by Receiver (which sends a SubscriberRemote).
	 */
	final void _subscribe(Subscriber sub)
	{
		if (debug) { log("Subscribing "+sub); }
		List<Subscriber> subs = getSubscribers(sub.type, sub.flavor, true);
		synchronized (subs)
		{
			subs.add(sub);
		}
	}

	/**
	 * Removes the given Subscriber from this server
	 * @param subscriber
	 * @return A boolean; <code>true</code> if the given subscriber was found and removed;
	 * <code>false</code> if the given subscriber was not found.
	 */
	final boolean unsubscribe(Subscriber subscriber)
	{
		List<Subscriber> subs = getSubscribers(subscriber.type, subscriber.flavor, false);
		if (subs!=null)
		{
			synchronized (subs)
			{
				return subs.remove(subscriber);
			}
		}
		return false;
	}


	/**
	 * Registers (or unregisters) a named service for remote method calls.
	 * 
	 * @param name The published name of the server;
	 *   usually the fully-qualified class name.
	 * @param service  The Object providing the service; any public method on the object
	 *   is available for calls; use <code>null</code> if you want
	 *   to unregister the named service.
	 */
	public final void registerService(String name, Object service) throws IllegalStateException
	{
		if (!isRunnable() || ss==null)
		{
			throw new IllegalStateException("Server not running; you must first call acceptRemoteClients().");
		}
		if (name != null)
		{
			synchronized(services)
			{
				if (service==null) { services.remove(name); } // unregister
				else { services.put(name, service); } // register
			}
		}
	}


	/**
	 * Execute a remote method, synchronously (like RMI).
	 * 
	 * @param hp The HostPort of the remote server that you want to call.
	 * @param serviceName  The String name of the service, as registered by the service on the remote server.
	 * @param method  The String method name to call on the remote service; this is the
	 * actual name of a public Java method within the service's implementing class.
	 * @param args  The Serializable[] of method arguments; may be null if the method has no parameters
	 *
	 * @return The Serializable value returned from the call.
	 * 
	 * @throws ArmiException if anything goes wrong; it is usually
	 * received from the remote server.  If the ArmiException.getCause() is an IOException,
	 * it usually means that there was a problem contacting the remote server.
	 * 
	 * TODO: Use the time-out form of the openRemoteServer() to optionally specify a non-default time-out for callers
	 */
	public final Serializable call(HostPort hp, String serviceName, String method, Serializable[] args) throws ArmiException
	{
		if (args==null) args = NO_ARGS;
		try 
		{
			ArmiRemote remote = openRemoteServer(hp); // may throw IOException
			return remote.call(serviceName, method, args);
		}
		catch(SocketTimeoutException ste) { throw new ArmiException("Time-out expired while connecting for "+method+"("+argsToClassList(args)+") @ "+hp, ste); }
		catch(Exception e) 
		{
			if (debug) { e.printStackTrace(); }
			throw new ArmiException("Problem transmitting call to "+method+"("+argsToClassList(args)+") @ "+hp, e);
		}
	}

	public final String argsToClassList(Serializable[] args) 
	{
		StringBuilder sb = new StringBuilder();
		for (Serializable arg : args)
		{
			sb.append(arg.getClass().getName());
		}
		return sb.toString();
	}
	

	/** A convenience method to call acceptRemoteClients() on any local network interface, at default ARMI port number */
	public final int acceptRemoteClients() throws IllegalStateException, IOException
	{
		return acceptRemoteClients(null, null);
	}
	
	
	/**
	 * Starts a socket server to accept remote clients; until then, this Armi instance
	 * can only be used to make calls or subscribe.  This call is necessary
	 * <em>only</em> when you want to make this ARMI server available to external clients.
	 * In other words, when you are publishing data to this ARMI server, and want to
	 * let remote ARMI clients subscribe.
	 * You should call this at most once per instance, otherwise an exception is thrown.
	 * <p>
	 * If you are not publishing data to this Armi instance, then you do not need
	 * to call this method at all.
	 * You can still use this Armi instance to communicate with
	 * remote ARMI servers, as a client.
	 * </p><p>
	 * Usually you only need one Armi instance per JVM, but multiple instances
	 * may be useful if you want to create private messaging domains.
	 * </p>
	 *
	 * @param hp  The HostPort where the socket should bind;
	 * to bind all local interfaces, use null 'host' when constructing the HostPort;
	 * to bind to a randomly available port number, use a 'port' value of zero
	 * when constructing the HostPort (useful only when this ARMI server is going
	 * to "register" itself for call-backs from a remote ARMI server).
	 *
	 * @throws java.lang.IllegalStateException if the server is already running 
	 * for this Armi instance.
	 *
	 * @throws IOException if there is an I/O problem starting the ServerSocket.
	 *
	 * @return The port number where the server is running; this number is only
	 * useful when you explictly specify a zero port number since a random port number will
	 * be assigned; otherwise it just returns the same port number you specified.
	 */
	public final int acceptRemoteClients(HostPort hp, AccessControl control) throws IllegalStateException, IOException
	{
		if (hp==null) hp = new HostPort((String)null, DEFAULT_PORT);
		if (hp.getPort()<0) hp.setPort(DEFAULT_PORT);
		this.control = control;
		if (!isRunnable() && ss==null)
		{
			thread = new Thread(this);
			thread.setDaemon(true);
			try 
			{ 
				ss = new ServerSocket(hp.getPort(), 50, hp.getHost());
				hp.setPort(ss.getLocalPort());
				thread.setName("ARMI Server @ "+hp);
				runnable = true;
				log("Starting "+thread.getName());
				thread.start();
				return hp.getPort();
			}
			catch (IOException ioe)
			{
				throw ioe;  // let the caller handle or log it
			}
		}
		else { throw new IllegalStateException("Already running "+thread.getName()); }
	}


	/**
	 * Used for diagnostics.
	 * @return A String containing this ARMI object's unique ID, and information
	 * regarding its status as a server for remote connections.
	 */
	@Override public final String toString()
	{
		StringBuilder sb = new StringBuilder(30);
		sb.append("ARMI ");
		if (thread==null) { sb.append("(not accepting remote clients)"); }
		else { sb.append("with ").append(thread.getName()); }
		return sb.toString();
	}


}
