
 /**
  * This template file was generated by Dynatrace client.
  * The Dynatrace community portal can be found here: http://community.dynatrace.com/
  * For information how to publish a plugin please visit https://community.dynatrace.com/community/display/DL/How+to+add+a+new+plugin/
  **/ 

package com.dynatrace.akanav2;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.dynatrace.akanav2.utils.Utils;
import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.Status;
import com.dynatrace.diagnostics.pdk.Status.StatusCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/*
 * Conceptually, this plugin does the following:
 * 1. Log into Akana.
 * 2. Grab the JSON response from a list of endpoints.
 * 3. Log out of Akana.
 * 4. Push the responses into Dynatrace AppMon.
 */
public class AkanaMonitor implements Monitor
{

	private static final Logger log = Logger.getLogger(AkanaMonitor.class.getName());


	@Override
	public Status setup(MonitorEnvironment env) throws Exception
	{
		return new Status(Status.StatusCode.Success);
	}

	@Override
	public Status execute(MonitorEnvironment env) throws Exception
	{
		long lStart = System.currentTimeMillis();
		
		String strProtocol = env.getConfigString(IConstants.CONFIG_PROTOCOL);
		String strEnvironment = env.getConfigString(IConstants.CONFIG_ENVIRONMENT);
		String strIgnoreListRaw = env.getConfigString(IConstants.CONFIG_IGNORE_LIST);
		String strUsername = env.getConfigString(IConstants.CONFIG_USERNAME);
		String strPassword = env.getConfigPassword(IConstants.CONFIG_PASSWORD);
		
		String strAkanaEnvironment = env.getConfigString(IConstants.CONFIG_AKANA_ENVIRONMENT);
		String strAkanaDuration = env.getConfigString(IConstants.CONFIG_DURATION);
		String strAkanaTimeInterval = env.getConfigString(IConstants.CONFIG_TIME_INTERVAL);
		String strAkanaTimeZone = env.getConfigString(IConstants.CONFIG_TIME_ZONE);
		String strAkanaShowSummary = env.getConfigString(IConstants.CONFIG_SHOW_SUMMARY);
		
		// Encode Time Zone
		try
		{
			strAkanaTimeZone = URLEncoder.encode(strAkanaTimeZone, "UTF-8");
		}
		catch (Exception e)
		{
			log.severe("Exception caught parsing time zone: " + e.getMessage());
			e.printStackTrace();
			return new Status(StatusCode.ErrorInternalConfigurationProblem);
		}

		if (Utils.isNullOrEmpty(strEnvironment))
		{
			log.severe("Environment is missing (eg. api.mysite.com). Plugin misconfigured.");
			return new Status(StatusCode.ErrorInternalConfigurationProblem);
		}
		if (Utils.isNullOrEmpty(strUsername) || Utils.isNullOrEmpty(strPassword))
		{
			log.severe("Username or password is missing (eg. api.mysite.com). Plugin misconfigured.");
			return new Status(StatusCode.ErrorInternalConfigurationProblem);
		}
		
		log.fine("Protocol: " + strProtocol);
		log.fine("Environment: " + strEnvironment);
		log.fine("Ignore List: " + strIgnoreListRaw);
		log.fine("Username: " + strUsername);
		log.fine("Akana Environment:" + strAkanaEnvironment);
		log.fine("Akana Duration:" + strAkanaDuration);
		log.fine("Akana Time Interval:" + strAkanaTimeInterval);
		log.fine("Akana Time Zone:" + strAkanaTimeZone);
		log.fine("Akana Show Summary:" + strAkanaShowSummary);

		List<String> oIgnoreList = splitList(strIgnoreListRaw); // Hold list of API Version IDs to Ignore.
		
		Map<String, JsonArray> oEndpointJSONMap = new HashMap<String, JsonArray>(); // "523fgs23.mydomain","JsonArray"
		CloseableHttpClient oHttpClient = HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).setConnectTimeout(IConstants.HTTP_TIMEOUT).build()).build();
		CloseableHttpResponse oResponse = null;
		
		/*
		 * Step 1: Log into Akana.
		 */
		log.fine("===== Step 1: Login =====");
		
		HttpPost oLoginPost = buildLoginPOST(strProtocol, strEnvironment, strUsername, strPassword);
		
		try
		{
			oResponse = oHttpClient.execute(oLoginPost);
		}
		catch (Exception e)
		{
			log.severe("Exception caught executing login POST: " + e.getMessage());
			e.printStackTrace();
		}
		
		log.info("[AG] Step 1: Login Done. Response Code: " + oResponse.getStatusLine());
	
		// Get Cookie and Csrf tokens from Login response.
		Map<String,String> oCookieMap = cookieTransformation(oResponse);
		
		if (oCookieMap == null)
		{
			log.severe("Cookie map is null.");
			return new Status(Status.StatusCode.ErrorInfrastructure);
		}
		
		String strCookie = oCookieMap.get(IConstants.COOKIE_VALUE);
		String strTokenKey = oCookieMap.get(IConstants.TOKEN_KEY); 
		String strTokenValue = oCookieMap.get(IConstants.TOKEN_VALUE);
		
		if (Utils.isNullOrEmpty(strCookie) || Utils.isNullOrEmpty(strTokenKey) || Utils.isNullOrEmpty(strTokenKey))
		{
			// Cannot progress. End execution.
			log.severe("Cookie or Token is invalid. Exit.");
			return new Status(Status.StatusCode.ErrorInfrastructure);
		}
		
		// Close Login Response
		try
		{
			oResponse.close();
		}
		catch (Exception e)
		{
			log.severe("Exception caught closing login response: " + e.getMessage());
		}
		
		/*
		 * Step 2: Grab list of API Version IDs from Akana
		 */
		log.fine("===== Step 2: Grab list of API Version IDs from Akana =====");
		log.fine("===== Ignoring following API Version IDs:");
		for (String strIgnore : oIgnoreList) log.fine("===== " + strIgnore);

		JsonObject oRootObject = getRawJSONObject(oCookieMap, oHttpClient, oIgnoreList, strProtocol, strEnvironment);
		List<String> oAPIVersionIDs = getAPIVersionIDs(oRootObject, oIgnoreList);
		Map<String,String> oAPIVersionIDToHumanReadableMap = getAPIVersionToHumanReadableMap(oRootObject, oIgnoreList); // Only used to map the APIVersionIDs "523fgs23.kingfisher" to the human readable names ("Product API v1").
		
		/*
		 * Step 3: Grab the JSON response from a list of endpoints.
		 * 
		 * Transform the cookies from the login step to get the Cookie and Csrf values.
		 * So that we can set them on the subsequent requests when we retrieve the JSON.
		 */
		
		log.fine("===== Step 3: Grab the JSON response from the list of API Version IDs. =====");
		
		for (String strAPIVersionID : oAPIVersionIDs)
		{
			HttpGet oGet = buildEndpointGet(strProtocol, strEnvironment, strAPIVersionID, strCookie, strTokenKey, strTokenValue, strAkanaEnvironment, strAkanaDuration, strAkanaTimeInterval, strAkanaTimeZone, strAkanaShowSummary);
			
			if (oGet == null)
			{
				log.severe("strAPIVersionID GET is null");
				return new Status(StatusCode.ErrorInternalException);
			}
			
			try
			{
				CloseableHttpResponse oTmpResponse = oHttpClient.execute(oGet);
				if (oTmpResponse.getStatusLine().getStatusCode() != 200)
				{
					log.info("Akana gave a non-200 response. Return code is: " + oTmpResponse.getStatusLine().getStatusCode() + ". Skipping this endpoint: " + strAPIVersionID);
					
					// Close Response.
					try
					{
						oTmpResponse.close();
					}
					catch (Exception e)
					{
						log.severe("Exception caught closing endpoint response: " + e.getMessage());
					}
					
					continue;
				}
				
				String strContent = EntityUtils.toString(oTmpResponse.getEntity());
				log.fine("JSON Content: " + strContent);
				
				JsonParser oParser = new JsonParser();
				JsonObject oVersionIDRootObject = oParser.parse(strContent).getAsJsonObject();
				
				JsonArray oMetricArray = oVersionIDRootObject.getAsJsonObject("Summary").getAsJsonArray("Metric");
				/*
				 * Store Metric Array for each endpoint.
				 * This metric array contains each statistic
				 * eg.
				 * "minResponseTime":100
				 * "avgResponseTime":444
				 * "maxResponseTime": 4344
				 * etc...
				 * 
				 * Use this in step 4
				 */
				oEndpointJSONMap.put(strAPIVersionID, oMetricArray);
				
				// Close Response.
				try
				{
					oTmpResponse.close();
				}
				catch (Exception e)
				{
					log.severe("Exception caught closing endpoint response: " + e.getMessage());
				}
			}
			catch (Exception e)
			{
				log.severe("Exception caught calling endpoint: " + e.getMessage());
				e.printStackTrace();
			}
			
		} // End foreach endpoint.
		
		
		
		/*
		 * Step 4: Logout
		 */

		log.fine("===== Step 4: Logout. =====");
		
		String strAkanaLogoutURI = strProtocol+ "://" + strEnvironment + "/api/login/endsession";
		//log.info(strAkanaLogoutURI);
		HttpGet oLogoutGET = new HttpGet(strAkanaLogoutURI);
		try
		{
			CloseableHttpResponse oLogoutResponse = oHttpClient.execute(oLogoutGET);
			log.info("Logout Response: " + oLogoutResponse.getStatusLine().getStatusCode());
			
			// Close Response
			oLogoutResponse.close();
		}
		catch (Exception e)
		{
			log.severe("Exception caught logging out: " + e.getMessage());
			e.printStackTrace();
		}

		
		/*
		 * Step 5: Push the responses into Dynatrace AppMon.
		 *
		 * At this point, the endpoint map should equal the number of endpoints.
		 * If the endpoint map size is less than the endpoint list size, we've skipped an endpoint
		 * due to a non-200 response code.
		 * 
		 * Each entry is the endpoint URL as a string, JSON object so we can easily push the values into AppMon
		 */
		
		log.fine("===== Step 5: Push the responses into Dynatrace AppMon. =====");
		
		/*
		 * We now have a list of endpoints and each endpoint has a JSON
		 * representation associated with it.
		 * Each JSON contains multiple statistics.
		 * 
		 * So we need to cycle through each subscribed measure and get the Parameter value.
		 * That parameter is the thing that maps to the JSON. eg. "minResponseTime".
		 * 
		 * Then we need to loop through each endpoint, get the value of the param and push into
		 * AppMon as a dynamic measure whereby the dynamic part is the endpoint.
		 * 
		 * Thus we end up with a representation such as:
		 * "minResponseTime":
		 *  -- endpoint1.domain
		 *  -- endpoint2.domain
		 *  
		 * Whereby "endpoint1.domain" and "endpoint2.domain" are the splits.
		 */
		Collection<MonitorMeasure> oMonitorMeasures = env.getMonitorMeasures();
		
		for (MonitorMeasure oMonitorMeasure : oMonitorMeasures)
		{
			String strParameterValue = oMonitorMeasure.getParameter(IConstants.CONFIG_PARAM_KEY); // eg. "minResponseTime"
			log.fine("Setting stats for: " + strParameterValue);
			
			for (String strAPIVersionID : oEndpointJSONMap.keySet())
			{
				log.fine("--- Setting stats for: " + oAPIVersionIDToHumanReadableMap.get(strAPIVersionID) + " --- ");
				
				double dValue = getParamValueFromJSON(strParameterValue, oEndpointJSONMap, strAPIVersionID, oAPIVersionIDToHumanReadableMap);
				if (dValue < 0) dValue = 0.0; // Don't push -1 to AppMon. It spoils the look of my charts!
				
				MonitorMeasure oDynamicMeasure = env.createDynamicMeasure(oMonitorMeasure, "API Endpoint", oAPIVersionIDToHumanReadableMap.get(strAPIVersionID));
				oDynamicMeasure.setValue(dValue);
				
			} // End foreach endpoint.

		}

		long lEnd = System.currentTimeMillis();
		
		closeConnections(oHttpClient, oLoginPost, oLogoutGET);
		
		return new Status(Status.StatusCode.Success);
	}

	@Override
	public void teardown(MonitorEnvironment env) throws Exception
	{
		
	}
	
	//===================
	// PRIVATE METHODS
	//===================
	private HttpPost buildLoginPOST(String strProtocol, String strEnvironment, String strUsername, String strPassword)
	{
		String strLoginURL = strProtocol+"://"+ strEnvironment + "/api/login";
		//log.info(strLoginURL);

		HttpPost oPost = new HttpPost(strLoginURL);
		
		oPost.addHeader("Accept", "application/json, text/javascript, */*; q=0.01");
		oPost.addHeader("Content-Type","application/json; charset=UTF-8");
		
		try
		{
			StringEntity oPayloadLogin = new StringEntity("{\n"
				+ "  \"email\":\"" +strUsername+ "\",\n"
				+ "  \"password\":\"" +strPassword+"\"\n"
				+ "}");
		
			oPost.setEntity(oPayloadLogin);
		}
		catch (Exception e)
		{
			log.info("Problem building login POST: " + e.getMessage());
		}
		
		return oPost;
	}
	
	private List<String> splitList(String strInput)
	{
		List<String> oEndpointList = new ArrayList<String>();
		try
		{
			String lines[] = strInput.split("\\r?\\n");
		
			int iEndpointCount = 0;
			while (iEndpointCount < lines.length)
			{
				oEndpointList.add(lines[iEndpointCount]);
				iEndpointCount++;
			}
		}
		catch (Exception e)
		{
			log.info("Exception caught parsing endpoint list. Are you sure you provided some?");
		}
		
		return oEndpointList;
	}
	
	/*
	 * Cookie: Strip everything before ;path and return
	 * Token: Strip everything before ;path then return Key and Value seperately.
	 *  eg. token Csrf-Token_tenant=TOKEN-VALUE;path=/
	 *      token key = Csrf-Token_tenant
	 *      token value: TOKEN-VALUE
	 */
	private Map<String,String> cookieTransformation(CloseableHttpResponse oResponse)
	{
		Map<String,String> oMap = new HashMap<String,String>();

		Header[] oResponseHeaders = oResponse.getAllHeaders();
		
		Header oCookie = null;
		Header oToken = null;
		
		for (int iCount = 0; iCount < oResponseHeaders.length; iCount++)
		{
		     Header oHeader = oResponseHeaders[iCount];
		     //log.info("Response Header: " + oHeader.getName() + " | " + oHeader.getValue()) ;
		     
		     if (oHeader.getValue().startsWith("AtmoAuthToken")) oCookie = oHeader;
		     if (oHeader.getValue().startsWith("Csrf-Token")) oToken = oHeader;
		}
	
		if (oCookie == null || oToken == null)
		{
			log.info("Missing Cookie or Token. Returning Null. We will not be able to progress.");
			log.info("Cookie Val: " + oCookie + " Token: " + oToken);
			return null;
		}
		
		try
		{
			String strCookieVal = oCookie.getValue().split(";path")[0];
		
			String strTokenKey = oToken.getValue().split("=")[0];
			String strTokenVal = oToken.getValue().split(";path")[0];
		
			strTokenVal = strTokenVal.substring(strTokenVal.indexOf('=')+1);
			
			oMap.put(IConstants.COOKIE_VALUE, strCookieVal);
			oMap.put(IConstants.TOKEN_KEY, strTokenKey);
			oMap.put(IConstants.TOKEN_VALUE, strTokenVal);
		}
		catch (Exception e)
		{
			//log.severe("Problem during cookie transformation: " + e.getMessage());
			return null;
		}
		
		return oMap;
	}
	
	
	private JsonObject getRawJSONObject(Map<String,String> oCookieMap, CloseableHttpClient oHttpClient, List<String> oIgnoreList, String strProtocol, String strEnvironment)
	{
		String strCookie = oCookieMap.get(IConstants.COOKIE_VALUE);
		String strTokenKey = oCookieMap.get(IConstants.TOKEN_KEY); 
		String strTokenValue = oCookieMap.get(IConstants.TOKEN_VALUE);
		
		HttpGet oGet = new HttpGet(strProtocol +"://" + strEnvironment + "/api/apis/versions");

		oGet.addHeader("Cookie", strCookie);
		oGet.addHeader("X-" + strTokenKey, strTokenValue);
		oGet.addHeader("Accept","application/vnd.soa.v72+json");
		
		JsonObject oRootObject = null;
		
		// Send GET
		try
		{
			CloseableHttpResponse oResponse = oHttpClient.execute(oGet);
		
			String strContent = EntityUtils.toString(oResponse.getEntity());
			//log.info(strContent);
			JsonParser oParser = new JsonParser();
			oRootObject = oParser.parse(strContent).getAsJsonObject();
		}
		catch (Exception e)
		{
			log.info("Exception caught getting raw JSON object: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	
		return oRootObject;
	}

	private HttpGet buildEndpointGet(String strProtocol, String strEnvironment, String strAPIVersionID, String strCookie, String strTokenKey, String strTokenValue, String strAkanaEnvironment, String strAkanaDuration, String strAkanaTimeInterval, String strAkanaTimeZone, String strAkanaShowSummary)
	{
		String strEndpoint = buildEndpoint(strProtocol, strEnvironment, strAPIVersionID, strAkanaEnvironment, strAkanaDuration, strAkanaTimeInterval, strAkanaTimeZone, strAkanaShowSummary);
		
		HttpGet oGet = new HttpGet(strEndpoint);
		oGet.addHeader("Cookie", strCookie);
		oGet.addHeader("X-" + strTokenKey, strTokenValue);
		
		return oGet;
	}
	
	 /* Returns a correctly formatted endpoint URI
	 * https://api.domain.com/api/apis/versions/VERSION-ID/metrics?Environment=Production&Duration=5m&TimeInterval=1m&TimeZone=Europe%2FLondon&ShowSummary=true
	 */
	private String buildEndpoint(String strProtocol, String strEnvironment, String strVersionID, String strAkanaEnvironment, String strAkanaDuration, String strAkanaTimeInterval, String strAkanaTimeZone, String strAkanaShowSummary)
	{
		
		if (Utils.isNullOrEmpty(strProtocol) || Utils.isNullOrEmpty(strEnvironment) || Utils.isNullOrEmpty(strVersionID)) return null;
		strProtocol = Utils.removeLeadingAndTrailingSlashes(strProtocol);
		strEnvironment = Utils.removeLeadingAndTrailingSlashes(strEnvironment);
		strVersionID = Utils.removeLeadingAndTrailingSlashes(strVersionID);

		StringBuilder oBuilder = new StringBuilder();
		oBuilder.append(strProtocol);
		oBuilder.append("://");
		oBuilder.append(strEnvironment);
		oBuilder.append("/api/apis/versions/");
		oBuilder.append(strVersionID);
		oBuilder.append("/metrics?Environment="+ strAkanaEnvironment +"&Duration="+ strAkanaDuration +"&TimeInterval="+ strAkanaTimeInterval +"&TimeZone="+ strAkanaTimeZone +"&ShowSummary="+ strAkanaShowSummary +"");
		
		log.info("buildEndpoint(): " + oBuilder.toString());
		
		return oBuilder.toString();
	}
	

	private List<String> getAPIVersionIDs(JsonObject oRootObject, List<String> oIgnoreList)
	{
		List<String> oReturnList = new ArrayList<String>();
		
		JsonArray oAPIVersionArray = oRootObject.get("APIVersion").getAsJsonArray();
			
		// For each APIVersion object, get it's ID and add to the return list.
		int iCount = 0;
		while (iCount < oAPIVersionArray.size())
		{
			String strAPIVersionID = oAPIVersionArray.get(iCount).getAsJsonObject().get("APIVersionID").getAsString();
			
			// Add to the list if we AREN'T ignoring it.
			if (!oIgnoreList.contains(strAPIVersionID))
			{
				//log.info("Adding: " + strAPIVersionID);
				oReturnList.add(strAPIVersionID);
			}
			
			iCount++;
		}
		
		return oReturnList;
	}

	/*
	 * Returns a Map of APIVersionID and Human Readable pairs.
	 * eg. "0079d2x-1e2c-4f55-9990-705566ed2f6e.kingfisher","Address API (v1)"
	 */
	private Map<String,String> getAPIVersionToHumanReadableMap(JsonObject oRootObject, List<String> oIgnoreList)
	{
		Map<String, String> oReturnMap = new HashMap<String, String>();
		JsonArray oAPIVersionArray = oRootObject.get("APIVersion").getAsJsonArray();
		
		// For each APIVersion object, get it's ID and add to the return list.
		int iCount = 0;
		while (iCount < oAPIVersionArray.size())
		{
			String strAPIVersionID = oAPIVersionArray.get(iCount).getAsJsonObject().get("APIVersionID").getAsString();
			String strAPIName = oAPIVersionArray.get(iCount).getAsJsonObject().get("Name").getAsString();
			
			// Add to the list if we AREN'T ignoring it.
			if (!oIgnoreList.contains(strAPIVersionID))
			{
				//log.info("Putting: " + strAPIVersionID + ", " + strAPIName);
				oReturnMap.put(strAPIVersionID,strAPIName);
			}
			
			iCount++;
		}
		
		return oReturnMap;
	}
	
	private void closeConnections(HttpClient oClient, HttpPost oPost, HttpGet oGet)
	{
		 oClient = null;
		 oPost = null;
		 oGet = null;
	}
	
	private double getParamValueFromJSON(String strParamValue, Map<String,JsonArray> oEndpointJSONMap, String strAPIVersionID, Map<String,String> oAPIVersionIDToHumanReadableMap)
	{
		JsonArray oAPIVersionIDMetrics = oEndpointJSONMap.get(strAPIVersionID);

		log.fine(oAPIVersionIDMetrics.toString());
		
		/*
		 * Each oResultElement contains X arrays such as:
		 * [
         *   {"Name":"avgResponseTime","Value":590},
		 *   {"Name":"minResponseTime","Value":1},
		 *   {"Name":"maxResponseTime","Value":2471},
		 *	 {"Name":"totalCount","Value":45},
		 *	 {"Name":"successCount","Value":45},
		 *	 {"Name":"faultCount","Value":0}
		 *	]
		 *
		 * Get each of these as a JsonObject then if the "Name" matches the input, return the value
		 */
		
		log.fine(oAPIVersionIDToHumanReadableMap.get(strAPIVersionID));
		for (JsonElement oResultElement : oAPIVersionIDMetrics)
		{
			JsonObject oResultObj = oResultElement.getAsJsonObject();
			String strNameValue = oResultObj.get("Name").getAsString();
			double dMetricValue = oResultObj.get("Value").getAsDouble();
			
			if (strNameValue.equals(strParamValue))
			{
				log.fine("Found match for: " + strNameValue + " returning: " + dMetricValue);
				return dMetricValue;
			}
		}

		//String strMetricName = oAPIVersionIDMetrics.get("Name").getAsString();
		//double dMetricValue = oAPIVersionIDMetrics.get("Value").getAsDouble();
		
		//log.info("Got: " + strMetricName + " for: " + strAPIVersionID + " with a value of: " + dMetricValue);
		
		log.severe("No metric found in JSON. We shouldn't get here unless plugin is misconfigured to have an input which isn't present in the JSON.");
		
		return -1; // We should never get here.
	}
	
}
