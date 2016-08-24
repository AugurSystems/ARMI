/*
 * Copyright 2012, Augur Systems, Inc.  All rights reserved.
 */
package com.augursystems.armi.demo;
import java.util.*;


/**
 * This is a GetOpts replacement that takes the String[] passed into any main()
 * method and parses through it (via the constructor) to find flags (any word starting with a hyphen), and 
 * values (any non-flag word after a flag).  
 * Flags don't have to have values; their mere presence can be detected via the isSet() method.  
 * Values (possibly null) are retrieved via the get() method.  
 * The ordered java.util.List of flags found is available via getFlags(); useful for iterating.
 */
class GetOpts 
{
private final static boolean debug = false;
private HashMap<String,String> vals;
private String flag;
private List<String> remainders;
private List<String> flags;

/** 
 * Creates new GetOpts from given args; adjacent words (not immediately following a flag) go into remainders List.
 * @param args  The String[] command line arguments, usually as received from main() method
 */
GetOpts(String[] args)
{ 
	this(args, false);
}

/** 
 * Creates new GetOpts from given args; has option to treat adjacent words as a space-separated 
 * multi-word phrase for the preceding flag; otherwise extra words go into remainders List.
 * 
 * @param args  The String[] command line arguments, usually as received from main() method
 * @param concatenate  A boolean indicating how adjacent words (not flags) should be treated;
 *   if true, adjacent words are concatenated to form a phrase to be the value of the preceding flag;
 *   if false, any extra words (not assigned to a preceding flag) will be stored in the remainders List.
 *   Typical usage: true when flags should accept phrases, false when a trailing list of filenames is
 *   expected.  Note that any empty (no value expected) flag will inadvertently capture the first 
 *   name of a trailing list of filenames; the value stored in that flag would have to be manually 
 *   added to the list of remainders by the user.
 */
GetOpts(String[] args, boolean concatenate) 
{
	String val;
	vals = new HashMap<String,String>(10);
	flags = new ArrayList<String>(Math.min(args.length,10));
	remainders = new ArrayList<String>(args.length);
	if (args==null) return;
	for (int i=0; i<args.length; i++)
	{
		if (args[i].startsWith("-")) 
		{
			flag = args[i].substring(1);
			if (debug) System.out.println("Set flag: " + flag);
			vals.put(flag, null); // set flag
			flags.add(flag);
		}
		else
		{
			// store value & clear flag for next arg
			/*  THIS IS TO CONCATENATE WORDS...
			if (flag !=null) 
			{
				String s = get(flag);
				if (s!=null) vals.put(flag, s + " " + args[i]); // concatenate with single space
				else vals.put(flag, args[i]); 
				//flag=null;		// use when no concatenation
			}
			else remainders.add(args[i]);
			*/
			if (flag !=null) 
			{
				val = get(flag); // get current value of flag, if any
				if (val!=null) 
				{
					if (concatenate) val = val+" "+args[i];
					else remainders.add(args[i]);
				}
				else val = args[i];
				//
				if (debug) System.out.println(flag+"="+val);
				vals.put(flag, val);
				if (!concatenate) flag=null;		// use when no concatenation
			}
			else remainders.add(args[i]);
		}
	}
}

/**
 * @return The java.util.List of flags, in the order found; useful for iterating;
 * all flags are stripped of any leading '-' hyphen.
 */
List<String> getFlags()
{
	return flags;
}

/** @return The boolean indicating if the given flag appeared in the args[];
 * the flag can be specified with/without it's preceeding hyphen.
 */
boolean isSet(String flag)
{
	if (flag.startsWith("-")) flag = flag.substring(1);
	return vals.containsKey(flag);
}

/** Sets the given possibly null flag to the given possibly null value */
void set(String flag, String value)
{
	if (flag.startsWith("-")) flag = flag.substring(1);
	vals.put(flag, value);
}

/** @return The possibly null String value associated with this flag */
String get(String flag)
{
	if (flag.startsWith("-")) flag = flag.substring(1);
	return vals.get(flag);
}

/** @return The integer value of the given flag; 0 if null, or not a number */
byte getByte(String flag)
{
	try { return Byte.parseByte(get(flag)); }
	catch(NumberFormatException nfe) { return (byte)0; }
}

/** @return The integer value of the given flag; 0 if null, or not a number */
int getInt(String flag)
{
	try { return Integer.parseInt(get(flag)); }
	catch(NumberFormatException nfe) { return 0; }
}

/** @return The long value of the given flag; 0 if null, or not a number */
long getLong(String flag)
{
	try { return Long.parseLong(get(flag)); }
	catch(NumberFormatException nfe) { return 0L; }
}

/** @return The integer value of the given flag; 0 if null, or not a number */
float getFloat(String flag)
{
	try { return Float.parseFloat(get(flag)); }
	catch(NumberFormatException nfe) { return 0f; }
}

/** @return The integer value of the given flag; 0 if null, or not a number */
double getDouble(String flag)
{
	try { return Double.parseDouble(get(flag)); }
	catch(NumberFormatException nfe) { return 0d; }
}

/** @return The integer value of the given flag; 0 if null, or not a number */
boolean getBoolean(String flag)
{
	try { return Boolean.parseBoolean(get(flag)); }
	catch(NumberFormatException nfe) { return false; }
}


/** @return The possibly null String value associated with the given flag1;
 * if flag1 is unset, return value of flag2; this is useful when there are two
 * names for the same flag (short/long versions) 
 */
String get(String flag1, String flag2)
{
	if (isSet(flag1)) return vals.get(flag1);
	else return vals.get(flag2);
}


/** @return The int number of flags (words prefixed by hyphen) found in the args[]  */
int getFlagCount()
{
	return vals.size();
}


/** 
 * @return The non-null List of String(s): remainder of args after the last flag/value pair;
 * or more specifically, the array of args which didn't map to a flag; possibly
 * non-adjacent words, although this would be a bad command line.
 */
List<String> getRemainders()
{
	return remainders;
}



}

