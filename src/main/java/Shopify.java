import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;


public class Shopify extends Scrapper {
	private final String baseLink;
	private Set<Product> products; 
	private boolean valid = true;

    public Shopify(String baseLink) throws Exception {
    	super();
    	setURI(URI.create(baseLink));
    	this.baseLink = baseLink;
    	getCookie();
    }
    
    //public functions
    public void cashout (String card, String expM, String expY, String ccv, double value) throws Exception {
    	//calaculate what to add
    	Map<Product, Integer> toBuy = getCashoutCombo(value);
    	//add to cart
    	addToCart(toBuy);
    	//checkout
    	//log reults
    }
    
    //cashout Helper Functions
    private void addToCart(Map<Product, Integer> toBuy) throws Exception {
        for (Entry<Product, Integer>  entry : toBuy.entrySet()) {
        	String boundary = generateWebKitBoundary();
        	
            //add time stamp cookie
            JSONObject cookiesJson = new JSONObject();

            JSONObject saT = new JSONObject();
            saT.put("value", java.time.Instant.now().toString());
            saT.put("path", "/");
            saT.put("domain", "briansphotography.myshopify.com");
            saT.put("secure", true);
            saT.put("http_only", false);
            cookiesJson.put("_shopify_sa_t", saT);
    	
    	    setNewCookie(cookiesJson);
        	
            Map<String, String> headers = new HashMap<>();
            addCommonHeaders(headers);
            headers.put("Accept", "application/javascript");
            headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
            headers.put("Origin", baseLink.endsWith("/") ? baseLink.substring(0, baseLink.length()-1) : baseLink);
            headers.put("Referer", baseLink + "products/" + entry.getKey().getLink());
            headers.put("Sec-Fetch-Dest", "empty");
            headers.put("Sec-Fetch-Mode", "cors");
            headers.put("Sec-Fetch-Site", "same-origin");
            headers.put("X-Requested-With", "XMLHttpRequest");
            
            String body = boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"quantity\"\r\n\r\n" + entry.getValue() + "\r\n" +
            		boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"form_type\"\r\n\r\nproduct\r\n" +
            		boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"utf8\"\r\n\r\n" +
            		"✓\r\n" +
            		boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"id\"\r\n\r\n" + entry.getKey().getId() + "\r\n" +
            		boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"product-id\"\r\n\r\n" + entry.getKey().getProductId() + "\r\n" +
            		boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"section-id\"\r\n\r\n" + entry.getKey().getSectionId() + "\r\n" +
            		boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"sections\"\r\n\r\ncart-notification-product,cart-notification-button,cart-icon-bubble\r\n" +
            		boundary + "\r\n" +
            		"Content-Disposition: form-data; name=\"sections_url\"\r\n\r\n/products/" + entry.getKey().getLink() + "\r\n" +
            		boundary + "--";
            
            Response res = makeCurlRequest(baseLink + "cart/add/", "POST", body, headers);

            //set cookie to array of set-cookie
            setNewCookie(res.getHeaders());
        }
          
    }
    
    private String generateWebKitBoundary() {
        String prefix = "------WebKitFormBoundary";
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int randomLength = 16;
        Random random = new Random();
        StringBuilder result = new StringBuilder(prefix);
        
        for (int i = 0; i < randomLength; i++) {
            int randomIndex = random.nextInt(chars.length());
            result.append(chars.charAt(randomIndex));
        }
        
        return result.toString();
    }
    
    //calculates what to buy to spend the most money
    //and randomizes selection of same price products
    private Map<Product, Integer> getCashoutCombo(final double amount) {
        // Create list to sort products by price
        List<Product> availableProducts = new ArrayList<>(products);
        Random random = new Random();
        Map<Product, Integer> result = new HashMap<>();
        
        // Use wrapper class to track remaining amount
        class AmountTracker {
            double remaining;
            AmountTracker(double initial) {
                this.remaining = initial;
            }
        }
        final AmountTracker tracker = new AmountTracker(amount);
        
        while (tracker.remaining > 0 && !availableProducts.isEmpty()) {
            // Get all products that fit within remaining amount
            List<Product> affordableProducts = availableProducts.stream()
                .filter(p -> p.getCost() <= tracker.remaining)
                .collect(Collectors.toList());
                
            if (affordableProducts.isEmpty()) {
                break;
            }
            
            // Different selection strategies based on remaining amount
            Product selected;
            if (random.nextDouble() < 0.7) {  // 70% chance for weighted random selection
                // Weight products by how well they fit the remaining amount
                Map<Product, Double> weights = new HashMap<>();
                for (Product p : affordableProducts) {
                    double fit = 1.0 - (tracker.remaining - p.getCost()) / tracker.remaining;
                    weights.put(p, fit);
                }
                
                // Weighted random selection
                double totalWeight = weights.values().stream().mapToDouble(w -> w).sum();
                double selection = random.nextDouble() * totalWeight;
                double cumWeight = 0.0;
                selected = affordableProducts.get(0);
                
                for (Map.Entry<Product, Double> entry : weights.entrySet()) {
                    cumWeight += entry.getValue();
                    if (selection <= cumWeight) {
                        selected = entry.getKey();
                        break;
                    }
                }
            } else {  // 30% chance for pure random selection
                selected = affordableProducts.get(random.nextInt(affordableProducts.size()));
            }
            
            // Calculate optimal quantity for selected product
            int maxQuantity = (int)(tracker.remaining / selected.getCost());
            int quantity;
            
            if (maxQuantity > 1) {
                // Randomize quantity but bias towards using more of the remaining amount
                double utilizationBias = random.nextDouble() * 0.4 + 0.6; // 60-100% utilization
                quantity = Math.max(1, (int)(maxQuantity * utilizationBias));
            } else {
                quantity = 1;
            }
            
            // Update result and remaining amount
            result.put(selected, result.getOrDefault(selected, 0) + quantity);
            tracker.remaining -= selected.getCost() * quantity;
            
            availableProducts.remove(selected);
        }
        
        return result;
    }
    
    //core
    private void getCookie() throws Exception {
        Map<String, String> headers = new HashMap<>();
        addCommonHeaders(headers);
        
        headers = addCommonHeaders(headers);
        Response res = makeCurlRequest(baseLink, "GET", null, headers);

        //set cookie to array of set-cookie
        setNewCookie(res.getHeaders());
        
        //set const cookies
        JSONObject cookiesJson = new JSONObject();

	     // shopify_sa_p cookie
	     JSONObject saP = new JSONObject();
	     saP.put("value", "");
	     saP.put("path", "/");
	     saP.put("domain", "briansphotography.myshopify.com");
	     saP.put("secure", true);
	     saP.put("http_only", false);
	     cookiesJson.put("_shopify_sa_p", saP);
	
	     // shopify_pay_redirect cookie
	     JSONObject payRedirect = new JSONObject();
	     payRedirect.put("value", "pending");
	     payRedirect.put("path", "/");
	     payRedirect.put("domain", "briansphotography.myshopify.com");
	     payRedirect.put("secure", true);
	     payRedirect.put("http_only", false);
	     cookiesJson.put("shopify_pay_redirect", payRedirect);
	
	     setNewCookie(cookiesJson);
        
        //find products
        String findProductsRegex = "href=\"/products/([^\"]+)\"";
        Set<String> productLinks = extractMatches(res.getBody(), findProductsRegex);
        products = parseProducts(productLinks);
    }
    
    private Set<Product> parseProducts(Set<String> links) {
    	Set<Product> products = new HashSet<>();
    	
    	for (String link : links) {
    		try {
	            Map<String, String> headers = new HashMap<>();
	            headers.put("Referer", baseLink);
	            headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
	            addCommonHeaders(headers);
	            
	            headers = addCommonHeaders(headers);
	            Response res = makeCurlRequest(baseLink + "products/" + link, "GET", null, headers);
	
	            //set cookie to array of set-cookie
	            setNewCookie(res.getHeaders());
	            
	            String sectionIdRegex = "product-form-installment-(template--\\d+__main)";
	            String idRegex = "name=\"id\"\\s+value=\"(\\d+)\"";
	            String productIdRegex = "\"product\":\\{\"id\":(\\d+),\"gid\"";
	            String nameRegex = "\"variants\":\\[\\{[^}]*\"name\":\"([^\"]+)\"";
	            String priceRegex = "\"price\":\"(\\d+\\.\\d+)\"";
	            
	            Product p = new Product();
	            p.setLink(link);
	            p.setSectionId(extractMatch(res.getBody(), sectionIdRegex));
	            p.setId(Long.parseLong(
	            		extractMatch(res.getBody(), idRegex)
	            ));
	            p.setProductId(Long.parseLong(
	            		extractMatch(res.getBody(), productIdRegex)
	            ));
	            p.setName(extractMatch(res.getBody(), nameRegex));
	            p.setCost(Double.parseDouble(
	            		extractMatch(res.getBody(), priceRegex)
	            ));
	            products.add(p);
    		}
    		catch (Exception e) {
    			e.printStackTrace();
    		}
    	}
    	
    	return products;
    }
    
    private String extractMatch(String source, String regex) {
    	//returning random result;
    	String ret = null;
    	for (String val : extractMatches(source, regex)) {
    		ret = val;
    		break;
    	}
    	return ret;
    }
    
    private Set<String> extractMatches(String source, String regex) {
        Set<String> matches = new HashSet<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(source);
        
        while (matcher.find()) {
            // If the pattern contains a capture group, get group 1, otherwise get the full match
            String match = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            matches.add(match);
        }
        
        return matches;
    }
    
    private Map<String, String> addCommonHeaders(Map<String, String> headers) {
    	headers.put("Accept-Encoding", "zstd");
    	headers.put("Accept", "*/*");
    	headers.put("Accept-Language", "en-US,en;q=0.9");
    	headers.put("Sec-GPC", "1");
    	headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("User-Agent", getUA());
        headers.put("Cookie", getCookieHeader());
        return headers;
    }
}
