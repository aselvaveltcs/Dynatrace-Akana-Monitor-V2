package com.dynatrace.akanav2.utils;

public class Utils
{
	public static boolean isNullOrEmpty(String strInput)
	{
		if (strInput == null || strInput.isEmpty()) return true;
		return false;
	}
	
	public static String removeLeadingAndTrailingSlashes(String strInput)
	{
		if (isNullOrEmpty(strInput)) return null;
		
		if (strInput.startsWith("/") || strInput.startsWith("\\")) strInput = strInput.substring(1);
		if (strInput.endsWith("/") || strInput.endsWith("\\")) strInput = strInput.substring(0,strInput.length()-1);
		
		return strInput;
	}
}
