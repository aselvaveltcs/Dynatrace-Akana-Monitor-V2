package com.dynatrace.akanav2;

public class IConstants
{
	public static final String CONFIG_PROTOCOL = "protocol";
	public static final String CONFIG_ENVIRONMENT = "environment"; // Represents "developer.kingfisher.com"
	public static final String CONFIG_IGNORE_LIST = "ignoreList";
	public static final String CONFIG_USERNAME = "username";
	public static final String CONFIG_PASSWORD = "password";
	public static final String CONFIG_PARAM_KEY = "JSON_Name"; // Used to map the JSON to AppMon measure eg. "minResponseTime"
	
	public static String CONSTANT_COOKIE = "cookie";
	public static String CONSTANT_TOKEN = "token";
	
	public static String COOKIE_VALUE = "cookieVal";
	public static String TOKEN_KEY = "tokenKey";
	public static String TOKEN_VALUE = "tokenValue";
	
	public static int HTTP_TIMEOUT = 5000;
	
	public static final String CONFIG_AKANA_ENVIRONMENT = "akanaEnvironment"; // Represents the "Environment" parameter during buildEndpointGet() method.
	public static final String CONFIG_DURATION = "duration"; // Represents the "Duration" parameter during buildEndpointGet() method.
	public static final String CONFIG_TIME_INTERVAL = "timeInterval"; // Represents the "TimeInterval" parameter during buildEndpointGet() method.
	public static final String CONFIG_TIME_ZONE = "timeZone"; // Represents the "TimeZone" parameter during buildEndpointGet() method.
	public static final String CONFIG_SHOW_SUMMARY = "showSummary"; // Represents the "ShowSummary" parameter during buildEndpointGet() method.

}
