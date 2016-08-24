/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */

package com.augursystems.armi;

/**
 * A container of fixed capacity byte array buffer, that may optionally overflow.
 * The read()/write() methods are optionally blocking to wait for data/capacity.
 * The blocking options are set in the constructor.
 * This implementation is fully synchronized (thread-safe).
 * <p>
 * This is a copy of the class from Augur Systems' Commons package: iai.io.QueueBytes.java
 *
 * @author Chris.Janicki@AugurSystems.com
 */
class QueueBytes extends Object implements java.io.Serializable
{
	private static final boolean debug = false;
	private final byte[] queue;
	private volatile int first, size, capacity;
	private volatile boolean readBlocking, writeBlocking;
	private String name;

	/**
	 *
	 * @param capacity The size of the underlying buffer
	 * @param readBlocking  If true, any attempt to read() will block until data is available
	 * @param writeBlocking If true, any attempt to write() will block until there is room for the data
	 */
	QueueBytes(int capacity, boolean readBlocking, boolean writeBlocking)
	{
		super();
		queue = new byte[capacity];
		this.readBlocking = readBlocking;
		this.writeBlocking = writeBlocking;
		first = 0;
		size = 0;
		this.capacity = capacity;
		this.name = getClass().getSimpleName();
	}

	final void setName(String name)
	{
		this.name = this.name+"@"+name;
	}


	/**
	 * Adds bytes onto the queue buffer
	 * @return The integer number of bytes that would not fit in the buffer;
	 * if greater than zero, then an overflow occurred (only occurs in non-blocking
	 * mode).
	 */
	final synchronized int write(byte[] bytes, int index, int length)
	{
		int len, next;
		if (debug)
		{
			System.out.println(name+"> write() called for bytes="+length);
		}
		while(writeBlocking && (capacity-size)<length) // not enough room yet
		{
			if (debug) { System.out.println(name+"> write() waiting because writeBlocking="+writeBlocking+", capacity="+capacity+", size="+size+", length="+length); }
			try{ wait(); }
			catch(InterruptedException ie) { }
		}
		next = (first+size) % capacity;
		if (next >= first) // not wrapped around yet
		{
			len = Math.min(capacity-next, length);
			if (debug) { System.out.println(name+"> index="+index+", next="+next+", len="+len+", whole length="+length); }
			System.arraycopy(bytes, index, queue, next, len);
			index += len;
			length -= len;
			size += len;
			if (length > 0) // wrap-around second half?
			{
				len = Math.min(first, length);
				if (debug) { System.out.println(name+"> Wrapped "+len+" bytes"); }
				System.arraycopy(bytes, index, queue, 0, len);
				index += len;
				length -= len;
				size += len;
			}
		}
		else // data is already wrapped around
		{
			len = Math.min(first-next, length);
			System.arraycopy(bytes, index, queue, next, len);
			index += len;
			length -= len;
			size += len;
		}
		notifyAll();
		if (debug && length>0) { System.out.println(name+"> size="+size+", capacity="+capacity+", length remaining="+length); }
		while (readBlocking && length > 0)
		{
			if (debug) { System.out.println(name+"> Writing blocked for "+length+" bytes"); }
			int leftover = write(bytes, index, length);
			index += length - leftover;
			length = leftover;
			if (debug) { System.out.println(name+"> Writing unblocked; "+length+" bytes more to go..."); }
			if (leftover==0) { return leftover; }
		}
		if (debug) { System.out.println(name+"> after write() size="+size); }
		return length; // normally zero unless overflow
	}


	/**
	 * May be called by a thread that wants to unblock a blocked read() or write() call.
	 * This should usually only be called by a shutdown thread.
	 */
	final synchronized void unblock()
	{
		if (debug) { System.out.println(name+"> QueueBytes.unlock()"); }
		readBlocking = false;
		writeBlocking = false;
		notifyAll();
	}


	final synchronized int read(byte[] bytes, int index, int length)
	{
		while (readBlocking && length>0 && size==0)
		{
			if (debug) { System.out.println(name+"> Reading blocked..."); }
			try { wait(); }
			catch(InterruptedException ie) { }
		}
		int len;
		length = Math.min(length, size);
		len = Math.min(length, capacity-first);
		System.arraycopy(queue, first, bytes, index, len);
		index += len;
		first += len;
		if (first == capacity) { first = 0; }
		size -= len;
		if (len<length) // more to go, wrapped around
		{
			len = length - len;
			System.arraycopy(queue, first, bytes, index, len);
			first += len;
			if (first == capacity) { first = 0; }
			size -= len;
		}
		notifyAll();
		if (debug) { System.out.println(name+"> Reading returning bytes: "+length); }
		return length;
	}


	final synchronized byte read()
	{
		while (readBlocking && size==0)
		{
			if (debug) { System.out.println(name+"> Reading blocked..."); }
			try { wait(); }
			catch(InterruptedException ie) { }
		}
		byte b = queue[first];
		first++;
		if (first == capacity) { first = 0; }
		size--;
		notifyAll();
		return b;
	}


	/**
	 * @param length The number of bytes you'd like to see fit
	 *
	 * @return a boolean indicating if there is sufficient room for the given
	 * length of data; no guarantees if multi-threaded access isn't managed;
	 * if <code>false</code>, you could try again later, assuming that there is a
	 * reader draining the buffer.
	 */
	final synchronized boolean hasCapacity(int length)
	{
		return (capacity-size) >= length;
	}


	/**
	 * @return the current number of bytes held in the queue; range = 0 .. capacity)
	 */
	final synchronized int getSize()
	{
		return size;
	}


//	public static void main(String[] args)
//	{
//		final QueueBytes bq = new QueueBytes(10,true, false);
//
//		Thread writer = new Thread()
//		{
//			public void run()
//			{
//				byte[] master = new byte[256];
//				for (int i=0; i<256; i++) { master[i] = (byte)i; }
//
//				int i=0;
//				while (true)
//				{
//					int r = Math.min((int)(Math.random()*11d), 256-i);
//					if (bq.write(master, i, r)>0) { System.out.println("OVERFLOW!"); }
//					i+=r;
//					i%=256;
//					try { Thread.sleep(10); } catch (InterruptedException ie) { }
//				}
//			}
//		};
//
//		Thread reader = new Thread()
//		{
//			int testing = 1000;
//			public void run()
//			{
//				byte[] buffer = new byte[256];
//				byte next=0;
//				while (testing>0)
//				{
//					int r = (int)(Math.random()*11d);
//					int s = bq.read(buffer, 0, r);
//					for (int i=0; i<s; i++)
//					{
//						if (buffer[i]!= next)
//						{
//							System.out.println("Problem? got "+buffer[i]+", but expected "+next);
//							System.exit(0);
//						}
//						next++;
//					}
//					try { Thread.sleep(20); } catch (InterruptedException ie) { }
//					testing--;
//				}
//				System.out.println("Done!");
//				System.exit(0);
//			}
//		};
//
//		reader.start();
//		writer.start();
//		}


}
