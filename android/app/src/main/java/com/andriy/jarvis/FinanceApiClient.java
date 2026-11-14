package com.andriy.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Read-only, user-initiated Money Tracker API connectivity test.
 * The vendor's screenshot identifies the documentation and test pages,
 * but does not identify a transaction write endpoint or JSON schema.
 * Never guess an endpoint or claim that a saved token authorizes writes.
 */
public final class FinanceApiClient {
    public static final String ORIGIN="https://money.quhou123.com";
    public static final String DOCS_URL=ORIGIN+"/Api/docs";
    public static final String TEST_URL=ORIGIN+"/Api/test";
    public interface Callback { void result(boolean reachable,String message); }
    private FinanceApiClient(){}

    public static void check(Context context,Callback cb) {
        if(cb==null)return;
        Context app=context.getApplicationContext();
        new Thread(()->{
            String token=FinanceApiSecrets.load(app);
            if(token==null) {
                onMain(cb,false,"Спочатку збережи новий токен API на Pixel.");
                return;
            }
            HttpURLConnection conn=null;
            try {
                // Fixed official HTTPS host and path. No user-controlled URLs,
                // redirects, or requests to third-party domains with a JWT.
                URL url=new URL(TEST_URL);
                conn=(HttpURLConnection)url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(false);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization","Bearer "+token);
                conn.setRequestProperty("Accept","application/json");
                conn.setRequestProperty("Cache-Control","no-store");
                int status=conn.getResponseCode();
                String mime=conn.getContentType();
                if(status==401 || status==403) {
                    onMain(cb,false,"API відхилив авторизацію (HTTP "+status+
                        "). Перевипусти токен у Менеджері фінансів.");
                } else if(status>=200 && status<300) {
                    boolean json=mime!=null &&
                        (mime.contains("application/json") || mime.contains("+json"));
                    onMain(cb,true,json
                        ? "Сервер API відповів HTTP "+status+
                            ". Формат операцій ще треба підтвердити в документації."
                        : "Сторінка тестування доступна (HTTP "+status+
                            "), але це не доказ, що токен дозволяє записувати операції.");
                } else if(status>=300 && status<400) {
                    onMain(cb,false,"Тестова адреса API перенаправляє (HTTP "+
                        status+"). Джарвіс не пересилає токен за редиректом.");
                } else {
                    onMain(cb,false,"Тестова адреса API відповіла HTTP "+status+
                        ". Перевір токен та документацію.");
                }
            }catch(Exception ignored){
                onMain(cb,false,"Немає HTTPS-зв’язку із сервером Менеджера фінансів. "+
                    "Перевір інтернет і спробуй ще раз.");
            }finally{
                if(conn!=null)conn.disconnect();
            }
        },"jarvis-money-api-check").start();
    }

    private static void onMain(Callback cb,boolean available,String message) {
        new Handler(Looper.getMainLooper()).post(()->cb.result(available,message));
    }

    /** Deliberately disabled until /Api/docs provides an exact endpoint,
     * account/category identifiers and write acknowledgement semantics. */
    public static boolean transactionWritesVerified() { return false; }
}
