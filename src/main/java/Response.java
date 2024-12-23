import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;

public class Response {
    public Response(HttpResponse<byte[]> res, String body) {
        this.headers = res.headers();
        this.code = res.statusCode();
        this.body = body;
    }
    public String getBody() {
        return body;
    }
    public HttpHeaders getHeaders() {
        return headers;
    }
    public int getStatusCode() {
    	return code;
    }
    public void setError(String error) {
    	this.error = error;
    }
    public String getError() {
    	return error;
    }
    
    private final HttpHeaders headers;
    private final String body;
    private final int code;
    private String error = "";
}
