import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.github.luben.zstd.ZstdInputStream;

import java.util.ArrayList;
import java.util.Base64;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;

//TODO Add rotating proxy

public class Scrapper {
    protected CookieManager cookie = new CookieManager();
    protected URI uri;
    protected Map<String, String> authHeaders = new HashMap<>();
    private String userAgent;
    private static String jsonPath = "Resources/UserAgents.json";
    private static JSONArray userAgents;
    private static List<String[]> proxyList = new ArrayList<String[]>();
    private static String proxyFileStr;
    private static char delimiter;
    private static int attempts;
    
    protected Scrapper() {
    	generateUA();
    }
    
    public static void setProxyFile(String filePath) {
    	proxyFileStr = filePath;
    }
    
    public static void setDelimiter(char delimiterChar) {
    	delimiter = delimiterChar;
    }
    
    public static void setAttempts(int attemptsInt) {
    	attempts = attemptsInt;
    }
    
    private static String[] getProxy() {
    	if (proxyList.isEmpty()) {
    		loadProxies();
    	}
    	Random random = new Random();
    	int randomIndex = random.nextInt(proxyList.size());
    	String[] tmp = proxyList.get(randomIndex);
    	return tmp;
    }
    
    private static void loadProxies() {
    	try {
        	File f = new File(proxyFileStr);
            StringBuilder content = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
    	    catch (FileNotFoundException e) {
    	        System.err.println("Error: File not found - " + f.getAbsolutePath());
    	        throw e;
    	    }
            catch (IOException e) {
    	        System.err.println("Error: Unable to read the file - " + e.getMessage());
    	        throw e;
    	    }
            
            String data[] = content.toString().split("\n");
            
            for (String str : data) {
            	try {
            		String split[] = str.split("\\Q" + delimiter + "\\E");
            		proxyList.add(split);
            	}
            	catch (Exception e) {
            		System.err.println("Failed to read line of proxies");
            		e.printStackTrace();
            	}
            }
    	}
    	catch (Exception e) {
    		System.err.println("Failed to read proxy File");
    		e.printStackTrace();
    	}
    }
    
    protected String getUA() {
    	return userAgent;
    }
    
    private void generateUA() {
        JSONArray userAgents = getUserAgents();

        if (!userAgents.isEmpty()) {
            Random random = new Random();
            int randomIndex = random.nextInt(userAgents.size());
            JSONObject randomUserAgentJson = (JSONObject) userAgents.get(randomIndex);
            String userAgentTmp = (String) randomUserAgentJson.get("ua");
            
            userAgent = userAgentTmp;
        } else {
            System.err.println("No user agents found in file.");
        }
    }
    
    public static void setJSONFile(String path) {
    	jsonPath = path;
    }
    
    private static JSONArray getUserAgents() {
    	if (userAgents == null)
    		readJsonFile();
    	return userAgents;
    }
    
    private static void readJsonFile() {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(jsonPath))) {
            String currentLine;
            while ((currentLine = br.readLine()) != null) {
                contentBuilder.append(currentLine).append("\n");
            }
            JSONParser jsonParser = new JSONParser();
            userAgents = (JSONArray) jsonParser.parse(contentBuilder.toString());
        }
        catch (Exception e) {
            System.err.println("Failed to read the UserAgent file: " + e.getMessage());
        }
    }
    
    protected void setNewAuthHeader(String name, String value) {
    	authHeaders.put(name, value);
    }
    
    protected void addAuthHeaders(Map<String, String> headers) {
    	headers.putAll(authHeaders);
    }
    
    protected void setURI(URI uri) {
    	this.uri = uri;
    }
    
    protected String getCookieHeader() {
    	return cookie.getCookieStore().getCookies().stream()
                .map(HttpCookie::toString)
                .collect(Collectors.joining("; "));
    }
    
    protected List<String> removeDuplicates(List<String> list) {
    	for (int i = 0; i < list.size(); i++) {
    		for (int j = 0; j < list.size(); j++) {
    			if (i != j && list.get(i).equals(list.get(j))) {
    				list.remove(i);
    				i = 0;
    				j = 0;
    			}
    		}	
    	}
    	return list;
    }
    
    protected void setNewCookie(HttpHeaders head) {
        List<String> cookies = head.allValues("Set-Cookie");
        for (String cookieStr : cookies) {
        	try {
        		List<HttpCookie> httpCookies = HttpCookie.parse(cookieStr);
	            for (HttpCookie httpCookie : httpCookies) {
	                cookie.getCookieStore().add(uri, httpCookie);
	            }
        	}
	        catch (IllegalArgumentException e) {
	            System.err.println("Invalid cookie string: " + cookieStr);
	        }
        }
    }
    
    protected void setNewCookie(String json) throws Exception{
    	JSONParser parser = new JSONParser();
    	setNewCookie((JSONObject) parser.parse(json));
    }
    
    protected void setNewCookie(JSONObject json) {
        for (Object key : json.keySet()) {
            String cookieName = (String) key;
            JSONObject cookieData = (JSONObject) json.get(cookieName);

            //make name correct 
            if (cookieName.contains("_js_"))
                cookieName = cookieName.substring(4);

            // Strip quotes if value comes with them
            String value = (String) cookieData.get("value");
            if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            // Only get attributes if they exist in map
            String path = cookieData.containsKey("path") ? (String) cookieData.get("path") : null;
            String domain = cookieData.containsKey("domain") ? (String) cookieData.get("domain") : null;
            boolean secure = cookieData.containsKey("secure") ? (boolean) cookieData.get("secure") : false;
            boolean httpOnly = cookieData.containsKey("http_only") ? (boolean) cookieData.get("http_only") : false;

            // Create the cookie
            HttpCookie tmp = new HttpCookie(cookieName, value);
            
            // Only set optional attributes if they were in the map
            if (path != null) tmp.setPath(path);
            if (domain != null) tmp.setDomain(domain);
            if (secure) tmp.setSecure(secure);
            if (httpOnly) tmp.setHttpOnly(httpOnly);
            
            // Disable version 1 protocol to avoid quotes
            tmp.setVersion(0);

            cookie.getCookieStore().add(uri, tmp);
        }
    }

    protected String getCookieValue(String cookieName) {
    	CookieStore cookieStore = cookie.getCookieStore();
        List<HttpCookie> cookies = cookieStore.get(uri);

        for (HttpCookie c : cookies) {
            if (c.getName().equals(cookieName)) {
                return c.getValue();
            }
        }
        return null; // Return null if cookie not found
    }
    
    protected static Response makeCurlRequest(String urlString, String method, String body, Map<String, String> headers) throws Exception {
    	return makeCurlRequest(urlString, method, body, headers, 0);
    }
    
    
    protected static Response makeCurlRequest(String urlString, String method, String body, Map<String, String> headers, int minChars) throws Exception {
    	//try until attempts run out or it returns a value
    	Response res = null;
    	for (int i = 0; i < attempts; i++) {
    		try {
    			String proxyInfo[] = getProxy();
                String proxyHost = proxyInfo[0];
                int proxyPort = Integer.parseInt(proxyInfo[1]);
                String username = proxyInfo[2];
                String password = proxyInfo[3];
                
                // Create the proxy selector
                ProxySelector proxySelector = ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));

                // Build the HttpClient with the proxy
                HttpClient client = HttpClient.newBuilder()
                        .proxy(proxySelector)
                        .build();

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(new URI(urlString))
                        .method(method, body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());

                // Add basic authentication for the proxy
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                requestBuilder.header("Proxy-Authorization", "Basic " + encodedAuth);

                // Add custom headers
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        requestBuilder.header(entry.getKey(), entry.getValue());
                    }
                }

                HttpRequest request = requestBuilder.build();

                // Send the request and get the response
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                String responseBodyString;

                // Check for ZStd encoding
                if ("zstd".equals(response.headers().firstValue("Content-Encoding").orElse(null))) {
                    // Decompress Zstd-encoded content
                    ZstdInputStream zstdInputStream = new ZstdInputStream(new ByteArrayInputStream(response.body()));
                    Reader reader = new InputStreamReader(zstdInputStream);
                    StringBuilder sb = new StringBuilder();
                    char[] buffer = new char[1024];
                    int bytesRead;

                    while ((bytesRead = reader.read(buffer)) != -1) {
                        sb.append(buffer, 0, bytesRead);
                    }
                    reader.close();

                    responseBodyString = sb.toString();
                } else {
                    // Convert response body to String if not encoded
                    responseBodyString = new String(response.body(), StandardCharsets.UTF_8);
                }
                
                if (response.statusCode() == 200) {
                	if ((minChars != 0 && responseBodyString.length() > minChars)
                		|| minChars == 0)
                		return new Response(response, responseBodyString);
                }
                else if (response.statusCode() == 407) {
                	System.err.println("Proxy Authentication Failed");
                	res = new Response(response, responseBodyString);
                }
                else {
                	res = new Response(response, responseBodyString);
                }
    		}
    		catch (IOException e) {
    			//if its this error retry
    		}
    	}
    	return res;
    }
    
    protected String randomNumberStr(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            int digit = random.nextInt(10); // Generates a random digit from 0 to 9
            sb.append(digit);
        }
        
        return sb.toString();
    }
}
