package zxf.springboot.https.client;


import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import zxf.springboot.https.client.http.OkHttpClientFactory;

public class Application {
    public static void main(String[] args) {
        System.setProperty("javax.net.debug", "all");
        System.setProperty("java.security.debug", "all");

        try {
            System.out.println("*****Https Server with safe OkHttpClient:");
            OkHttpClient okHttpClient = OkHttpClientFactory.getSafeOkHttpClient();
            Request request = new Request.Builder().url("https://localhost:8080/home").get().build();
            Call call = okHttpClient.newCall(request);
            System.out.println(call.execute().body().string());
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server with unsafe OkHttpClient:");
            OkHttpClient okHttpClient = OkHttpClientFactory.getUnsafeOkHttpClient();
            Request request = new Request.Builder().url("https://localhost:8080/home").get().build();
            Call call = okHttpClient.newCall(request);
            System.out.println(call.execute().body().string());
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server with default OkHttpClient:");
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder().url("https://localhost:8080/home").get().build();
            Call call = okHttpClient.newCall(request);
            System.out.println(call.execute().body().string());
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }
}
