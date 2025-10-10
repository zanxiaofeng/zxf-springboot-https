package zxf.https.client.okhttp;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class OkHttpClientApp {
    public static void main(String[] args) {
        //System.setProperty("javax.net.debug", "all");
        //System.setProperty("java.security.debug", "all");

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

        try {
            System.out.println("*****Https Server(Client Auth) with safe OkHttpClient:");
            OkHttpClient okHttpClient = OkHttpClientFactory.getSafeOkHttpClientWithClientAuth();
            Request request = new Request.Builder().url("https://localhost:8082/home").get().build();
            Call call = okHttpClient.newCall(request);
            System.out.println(call.execute().body().string());
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server(Client Auth) with unsafe OkHttpClient:");
            OkHttpClient okHttpClient = OkHttpClientFactory.getUnsafeOkHttpClientWithClientAuth();
            Request request = new Request.Builder().url("https://localhost:8082/home").get().build();
            Call call = okHttpClient.newCall(request);
            System.out.println(call.execute().body().string());
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        try {
            System.out.println("\n*****Https Server(Client Auth) with default OkHttpClient:");
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder().url("https://localhost:8082/home").get().build();
            Call call = okHttpClient.newCall(request);
            System.out.println(call.execute().body().string());
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }
}
