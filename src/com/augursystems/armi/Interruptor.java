/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;

/**
 * This class will interrupt the given thread after the given amount of time if the 
 * given thread is still alive.
 */
public class Interruptor extends Thread
{
private final Thread thread;
private final int timeoutMillis;
private volatile InterruptedException reason = null;

private Interruptor(Thread thread, int timeoutMillis)
{
	this.thread = thread;
	this.timeoutMillis = timeoutMillis;
}


public static Interruptor scheduleInterrupt(Thread thread, int timeoutMillis)
{
	Interruptor inter = new Interruptor(thread, timeoutMillis);
	inter.start();
	return inter;
}



/** Abandons the scheduled interruption if this thread itself is interrupted */
@Override
public void run()
{
	try
	{
		sleep(timeoutMillis);
		if (thread.isAlive())
		{
			if (reason==null) // nothing happened while asleep
			{ 
				reason = new InterruptedException("Time-out expired @ "+timeoutMillis/1000+" seconds."); 
				thread.interrupt();
			}
			else { } // shouldn't get here since would have been interrupted, unless just in process of it
		}
	}
	catch (InterruptedException ie)
	{
		if (reason!=null) { thread.interrupt(); } 
		else { } // cancel() was called, so just let thread fall through
	}
}


/** Aborts the interruption for the given reason, which will be retrievable via getReason() */
public void interruptNow(String reason) throws IllegalStateException
{
	if (Armi.debug) System.out.println("interruptNow() called for: "+reason);
	if (this.reason!=null) throw new IllegalStateException("Cannot fire for \""+reason+"\" because already fired for \""+this.reason.getMessage()+"\"");
	this.reason = new InterruptedException(reason);
	interrupt();
}


public void cancel()
{
	if (reason==null) interrupt();
	else { throw new IllegalStateException("Cannot cancel because already fired for \""+this.reason.getMessage()+"\""); }
}


/**
 * The reason why this Interruptor fired is in the InterruptedException's 
 * getMessage(); an exception is used (rather than just the text) so that the 
 * reason's stack trace is also available, for debugging.
 * 
 * @return An InterruptedException containing the reason message and stack trace
 * of the causing thread.
 */
public InterruptedException getReason()
{
	return reason;
}


}
