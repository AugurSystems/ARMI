/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Wraps any remote exception;
 * analogous to RMI's RemoteException class.
 * 
 * @author Chris.Janicki@AugurSystems.com
 */
public class ArmiException extends Exception implements Externalizable
{
	/**
	 * A message provided by the ARMI system to describe the circumstances when
	 * the causal exception occurred; possibly null.
	 */
	private String message;



	/**
	 * Creates a plain exception, with no wrapped message or exception.
	 */
	public ArmiException()
	{
		super();
	}


	/**
	 * Creates a plain exception, with the given message, but no wrapped exception.
	 */
	public ArmiException(String message)
	{
		super();
		this.message = message;
	}

	/**
	 * Creates a plain exception, with the given wrapped exception, but no message
	 * specific to this ArmiException.
	 */
	public ArmiException(Throwable e)
	{
		super(e);
	}

	/**
	 * Creates a plain exception, with the given message, and wrapped exception.
	 */
	public ArmiException(String message, Throwable e)
	{
		super(e);
		this.message = message;
	}

	/** Overridden here since Throwable's copy is private, and there is no setter
	 * available for ARMI's de-serialization.
	 * @return The exception's detail message text
	 */
	@Override	public String getMessage()
	{
		return message;
	}


	/**
	 * Implements Externalizable to serialize this object.  This is not normally called by you.
	 *
	 * @serialData The Throwable's fields are written in this order: 1) detailMessage,
	 * 2) the int length of the StackTraceElement array, 3) the String class name,
	 * String file name, String method name, and int line number fields of each
	 * StackTraceElement in the array, starting from index 0, 4) a boolean
	 * indicating if there is a nested <i>cause</i> Throwable, 5) if a nested
	 * cause exists, then it is written in the same steps as above, recursively.
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeUTF(getMessage());
		StackTraceElement[] traces = getStackTrace();
		out.writeInt(traces.length);
		for (StackTraceElement ste : traces)
		{
			out.writeUTF(ste.getClassName());
			out.writeUTF(ste.getFileName());
			out.writeUTF(ste.getMethodName());
			out.writeInt(ste.getLineNumber());
		}
		Throwable cause = getCause();
		out.writeBoolean(cause!=null); // has cause?
		if (cause!=null) { out.writeObject(cause); }
	}


	/**
	 * Implements Externalizable to de-serialize this object.  This is not normally called by you.
	 */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		this.message = in.readUTF();
		int traceSize = in.readInt();
		StackTraceElement[] traces = new StackTraceElement[traceSize];
		for (int i=0; i<traceSize; i++)
		{
			String className = in.readUTF();
			String fileName = in.readUTF();
			String methodName = in.readUTF();
			int lineNumber = in.readInt();
			traces[i] = new StackTraceElement(className, methodName, fileName, lineNumber);
		}
		this.setStackTrace(traces);
		boolean hasCause = in.readBoolean();
		if (hasCause) initCause((Throwable)in.readObject());
	}



	private static final String CAUSED_BY = "; caused by: ";
	@Override public String toString()
	{
		String myToString = super.toString();
		String causeToString=null;
		int causeLength = 0;
		Throwable cause = getCause();
		if (cause!=null) 
		{ 
			causeToString = cause.toString(); 
			causeLength = CAUSED_BY.length() + causeToString.length();
		}
		StringBuilder sb = new StringBuilder(myToString.length()+causeLength);
		sb.append(myToString);
		if (causeLength>0) { sb.append(CAUSED_BY).append(causeToString); }
		return sb.toString();
	}


}
