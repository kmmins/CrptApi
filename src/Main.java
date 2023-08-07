import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        int requestLimit = 1;
        TimeUnit timeUnit = TimeUnit.SECONDS;

        CrptApi crptApi = new CrptApi(timeUnit,requestLimit);
        System.out.println(System.currentTimeMillis());
    }
}