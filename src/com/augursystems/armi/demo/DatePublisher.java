/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi.demo;
import com.augursystems.armi.Armi;
import java.util.Date;

/**
 * This is just an example publisher.  It publishes the current date periodically,
 * according to the constructor parameters.
 * 
 * @author Chris.Janicki@AugurSystems.com
 */
class DatePublisher extends Thread
{
	private Armi server;
	private String flavor;
	private int intervalMillis;

	DatePublisher(int intervalMillis, Armi server, String flavor)
	{
		super("Date Packet Source ("+flavor+")");
		setDaemon(true); // allow JVM to shutdown without stopping thread
		this.server = server;
		this.flavor = flavor;
		this.intervalMillis = intervalMillis;
	}

	@Override public void run()
	{
		Date date = new Date();
		System.out.println("Starting "+date.getClass().getSimpleName()+"; interval="+intervalMillis+"ms; type/flavor='"+date.getClass().getName()+"/"+flavor+"'");
		try
		{
			while (true)
			{
				Thread.sleep(intervalMillis);
				date.setTime(System.currentTimeMillis());
				server.publish(date, flavor);
			}
		}
		catch (Exception e) { e.printStackTrace(); }
	}

}
