/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;


/**
 * This encapsulates a host (InetAddress) and port number (int).
 * This implementation should work for IPv6 also (where the IP address is 
 * wrapped by brackets), although this has not been tested yet.
 *
 * @author  Chris.Janicki@AugurSystems.com
 */
public final class HostPort implements Externalizable
{
	private InetAddress host;
	private int port;
	private transient int hashCode;
	
	
/** 
 * Creates a new instance of HostPort containing the local host and the default 
 * ARMI port.
 */
public HostPort() throws UnknownHostException
{
	this(InetAddress.getLocalHost(), Armi.DEFAULT_PORT);
}


/** Creates a new instance of HostPort */
public HostPort(InetAddress host, int port)
{
	this.host=host;
	this.port=port;
}

/** Creates a new instance of HostPort */
public HostPort(String host, int port) throws UnknownHostException
{
	this(host==null || host.trim().length()==0 ? null : InetAddress.getByName(host), port);
}

///** Creates a new instance of HostPort */
//public HostPort(java.net.URI uri) throws UnknownHostException
//{
//	this(uri.getHost(), uri.getPort());
//}

/**
 * Note: The parsing of IPv6 has not been tested yet.
 * 
 * @param hostColonPort  Either "#.#.#.#:#" for IPv4, or
 * "[#:#:#:#:#:#:#:#:#:#:#:#:#:#:#:#]:#" for IPv6; in both formats,
 * the last digit is the port number.
 * If the port is missing (or no colon), then the port is set to zero.
 *
 * @throws java.net.UnknownHostException
 */
public HostPort(String hostColonPort) throws UnknownHostException
{
	String h;
	int i;
	if (hostColonPort.startsWith("[") && (i=hostColonPort.indexOf(']'))>0) // IPv6
	{
		h = hostColonPort.substring(1,i);
		i++; // advance to colon
	}
	else // IPv4
	{
		i = hostColonPort.indexOf(":");
		//if (i<0) throw new UnknownHostException("Missing colon ':' in '"+hostColonPort+"'"); }
		if (i>=0) h = hostColonPort.substring(0,i);
		else h = hostColonPort;
	}
	if (i<0 || i>=hostColonPort.length())
	{
		this.port = 0;
	}
	else
	{
		try
		{
			this.port = Integer.parseInt(hostColonPort.substring(i+1));
		}
		catch (NumberFormatException nfe) { throw new UnknownHostException("Bad port number in '"+hostColonPort+"'"); }
	}
	this.host = InetAddress.getByName(h);
}


public final void set(InetAddress host, int port)
{
	this.host = host;
	this.port = port;
	this.hashCode = 0; // reset; may be recalculated the next time hashCode() is called
}


public final InetAddress getHost() { return host; }

public final void setHost(InetAddress host) { this.host = host; }

public final int getPort() { return port; }

public final void setPort(int port) { this.port = port; }


/** 
 * Parses a string of the form "[host1]:[port1], [host2]:[port2], ..." into an IPv4 HostPort[];
 * the delimeter is any combination of comma and/or space characters; a colon separates each host and port.
 *
 * @throws UnknownHostException if there is any problem parsing the string, including unknown hosts, 
 *   bad format, bad port number, etc.
 * @return A HostPort[] containing the successfully parsed data; null if it looks like there was no data
 */
public static HostPort[] parse(String src) throws UnknownHostException
{
	if (src==null || src.length()<1 || src.indexOf(":")<0) { return null; }
	String[] targets = src.split(", ");
	List<HostPort> list = new ArrayList<HostPort>(targets.length);
	String[] hp;
	int port;
	for (int i=targets.length-1; i>=0; i--)
	{
		hp = targets[i].split(":");
		if (hp.length!=2) { throw new UnknownHostException(targets[i]); }
		try { port = Integer.parseInt(hp[1]); }
		catch (NumberFormatException nfe) { port = -1; }
		if (port<=0) { throw new UnknownHostException("Bad port number ("+hp[1]+"); host="+hp[0]); }
		try { list.add(new HostPort(InetAddress.getByName(hp[0]), port)); }
		catch(UnknownHostException uhe) { throw new UnknownHostException(hp[0] + ": " +uhe); }
	}
	HostPort[] hps = new HostPort[list.size()];
	hps = (HostPort[])list.toArray(hps);
	return hps;
}

	@Override	public String toString()
	{
		return (host==null?"*":host)+":"+port;
	}


	/**
	 * Implements Externalizable.
	 * @serialData 1) the int number of bytes in the byte[] representing the host
	 * address; 2) the host address's byte[]; 3) the UTF String host address;
	 * 4) the int port number.
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{
		byte[] bytes = host.getAddress();
		out.writeInt(bytes.length);
		out.write(bytes);
		out.writeUTF(host.getHostAddress());
		out.writeInt(port);
	}


	/**
	 * Implements Externalizable.
	 */
	public void readExternal(ObjectInput in)  throws IOException, ClassNotFoundException
	{
		int length = in.readInt();
		byte[] bytes = new byte[length];
		in.readFully(bytes);
		int p = in.readInt(); // read int to fix stream before tempting exception in InetAddress()
		InetAddress h = InetAddress.getByAddress(bytes);
		set(h,p);
	}


	@Override	public boolean equals(Object obj)
	{
		return obj instanceof HostPort &&
			host.equals(((HostPort)obj).host) &&
			port == ((HostPort)obj).port;
	}


	@Override	public int hashCode()
	{
		if (hashCode==0)
		{
			int code = 0;
			if (host!=null) 
			{
				byte[] octets = host.getAddress();
				for (byte b : octets) { code += b; }
			}
			hashCode = code + port;
		}
		return hashCode;
	}

}
