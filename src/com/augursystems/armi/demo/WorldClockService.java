package com.augursystems.armi.demo;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * An ARMI service that will provide the current time in given time zones.
 * 
 * @author Chris.Janicki@AugurSystems.com
 * Copyright 2012 Augur Systems, Inc.  All rights reserved.
 */
public class WorldClockService
{

	
	/**
	 * Provides the current time in the given time zone.
	 * @param tz  A String representing a time zone
	 * @return a String containing the current time in the time zone given,
	 * terminated by end-of-line character(s), per O/S.
	 */
	public final String getTime(String tz)
	{
		String eol = System.getProperty("line.separator");
		StringBuilder sb = new StringBuilder(12);
		TimeZone z = TimeZone.getTimeZone(tz);
		GregorianCalendar cal = new GregorianCalendar(z);
		sb.append(zeroPad(cal.get(Calendar.HOUR_OF_DAY))).append(":").append(zeroPad(cal.get(Calendar.MINUTE))).append(" ").append(z.getID()).append(eol);
		return sb.toString();
	}
	
	
	/**
	 * Provides the current time in the given time zone(s).
	 * @param tzs  A variable number of String parameters representing time zones
	 * @return a String containing the current time in each time zone given, each
	 * terminated by end-of-line character(s), per O/S.
	 */
	public final String getTimes(String ... tzs)
	{
		StringBuilder sb = new StringBuilder(tzs.length*12);
		for (String tz : tzs)
		{
			sb.append(getTime(tz));
		}
		return sb.toString();
	}
		
		
	private static String zeroPad(int i)
	{
		if (i<10) return "0"+i;
		else return Integer.toString(i);
	}
	

}
