public class Product {
    private double cost;
    private String name;
    private String link;
    private long id;
    private long productId;
    private String sectionId;

    // Empty constructor
    public Product() {}
    
    // Getters
    public double getCost() {
        return cost;
    }

    public String getName() {
        return name;
    }

    public String getLink() {
        return link;
    }

    public long getId() {
        return id;
    }

    public long getProductId() {
        return productId;
    }

    public String getSectionId() {
        return sectionId;
    }

    // Setters
    public void setCost(double cost) {
        this.cost = cost;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setProductId(long productId) {
        this.productId = productId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }
}