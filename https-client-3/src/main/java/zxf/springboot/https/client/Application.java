package zxf.springboot.https.client;


import okhttp3.OkHttpClient;
import okhttp3.Request;

public class Application {
    public static void main(String[] args) {
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .get()
                .url("https://localhost:8080/home")
                .build();
    }
}
