import java.util.Properties;

public class Start {
    public static void main(String[] args) {
    	try {
        	System.out.println("Configuring");
            // Configure system properties
            Properties props = System.getProperties();
            props.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");

            // Configure Scrapper settings
            Scrapper.setAttempts(5);
            Scrapper.setDelimiter(':');
            Scrapper.setJSONFile("Resources/UserAgents.json");
            Scrapper.setProxyFile("Resources/proxies.txt");
            
            
    		Shopify shop = new Shopify("https://briansphotography.myshopify.com/");
    		shop.cashout(null, null, null, null, 80.20);
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
