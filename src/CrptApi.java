import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

public class CrptApi {
    private final TokenBucket tb;
    private String tokenData;
    private LocalDateTime startTokenLifeTime;
    private final Gson gson = new Gson();
    private final HttpResponse.BodyHandler<String> handler = HttpResponse.BodyHandlers.ofString();
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.tb = new TokenBucket(timeUnit.toMillis(1), requestLimit);
    }

    public void postLpIntroduceGoods(Document document, String signature) throws IOException, InterruptedException {
        DocType docType = null;
        switch (document.getDocument_format()) {
            case MANUAL:
                docType = DocType.LP_INTRODUCE_GOODS;
                break;
            case XML:
                docType = DocType.LP_INTRODUCE_GOODS_XML;
                break;
            case CSV:
                docType = DocType.LP_INTRODUCE_GOODS_CSV;
                break;
        }
        postDocument(document, signature, docType);
    }

    private void postDocument(Document document, String signature, DocType docType) throws IOException, InterruptedException {
        DocumentDto dto = new DocumentDto(
                document.getDocument_format(),
                document.getProduct_document(),
                document.getProduct_group(),
                signature,
                docType);

        authorizeIfNeeded();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(dto)))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + tokenData)
                .build();

        tb.Consume();
        HttpResponse<String> response = client.send(request, handler);
        int status = response.statusCode();
        if (status != 200) {
            throw new RuntimeException("Сервер вернул ошибку (код " + status + ") : " + response.body());
        }
    }

    private synchronized void authorizeIfNeeded() throws IOException, InterruptedException {
        if (tokenData != null) {
            if (LocalDateTime.now().isAfter(startTokenLifeTime.plusHours(10))) {
                return;
            }
        }
        HttpRequest requestGetUuidData = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/auth/cert/key"))
                .GET()
                .build();
        HttpResponse<String> responseGetUuidData = client.send(requestGetUuidData, handler);
        int statusGet = responseGetUuidData.statusCode();
        if (statusGet != 200) {
            throw new RuntimeException("Сервер вернул ошибку (код " + statusGet + ") : " + responseGetUuidData.body());
        }
        AuthorizationDto an = gson.fromJson(responseGetUuidData.body(), AuthorizationDto.class);

        String signedTokenData = signTokenData(an.getData());
        an.setData(signedTokenData);

        HttpRequest requestPostUuidData = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/auth/cert/"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(an)))
                .build();
        HttpResponse<String> responsePostUuidData = client.send(requestPostUuidData, handler);
        int statusPost = responsePostUuidData.statusCode();
        if (statusPost != 200) {
            throw new RuntimeException("Сервер вернул ошибку (код " + statusPost + ") : " + responsePostUuidData.body());
        }
        TokenDto tn = gson.fromJson(responsePostUuidData.body(), TokenDto.class);
        startTokenLifeTime = LocalDateTime.now();
        tokenData = tn.getToken();
    }

    private class AuthorizationDto {
        private String uuid;
        private String data;

        public AuthorizationDto(String uuid, String data) {
            this.uuid = uuid;
            this.data = data;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    private String signTokenData(String data) {
        //заглушка для метода присоединения УКЭП
        return "<Подписанные данные в base64>";
    }

    private class TokenDto {
        private String token;

        public TokenDto(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    private class DocumentDto {
        private DocFormat document_format;
        private String product_document;
        private ProductGroup product_group;
        private String signature;
        private DocType type;

        public DocumentDto() {
        }

        public DocumentDto(DocFormat document_format,
                           String product_document,
                           ProductGroup product_group,
                           String signature,
                           DocType type) {
            this.document_format = document_format;
            this.product_document = product_document;
            this.product_group = product_group;
            this.signature = signature;
            this.type = type;
        }

        public DocFormat getDocument_format() {
            return document_format;
        }

        public void setDocument_format(DocFormat document_format) {
            this.document_format = document_format;
        }

        public String getProduct_document() {
            return product_document;
        }

        public void setProduct_document(String product_document) {
            this.product_document = product_document;
        }

        public ProductGroup getProduct_group() {
            return product_group;
        }

        public void setProduct_group(ProductGroup product_group) {
            this.product_group = product_group;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public DocType getType() {
            return type;
        }

        public void setType(DocType type) {
            this.type = type;
        }
    }

    private class Document {
        private ProductGroup product_group;
        private DocFormat document_format;
        private String product_document;

        public ProductGroup getProduct_group() {
            return product_group;
        }

        public DocFormat getDocument_format() {
            return document_format;
        }

        public String getProduct_document() {
            return product_document;
        }
    }

    private enum DocFormat {
        MANUAL,
        XML,
        CSV
    }

    private enum ProductGroup {
        clothes,
        shoes,
        tobacco,
        perfumery,
        tires,
        electronics,
        pharma,
        milk,
        bicycle,
        wheelchairs
    }

    private enum DocType {
        AGGREGATION_DOCUMENT,
        AGGREGATION_DOCUMENT_CSV,
        AGGREGATION_DOCUMENT_XML,
        DISAGGREGATION_DOCUMENT,
        DISAGGREGATION_DOCUMENT_CSV,
        DISAGGREGATION_DOCUMENT_XML,
        REAGGREGATION_DOCUMENT,
        REAGGREGATION_DOCUMENT_CSV,
        REAGGREGATION_DOCUMENT_XML,
        LP_INTRODUCE_GOODS,
        LP_INTRODUCE_GOODS_CSV,
        LP_INTRODUCE_GOODS_XML,
        LP_SHIP_GOODS,
        LP_SHIP_GOODS_CSV,
        LP_SHIP_GOODS_XML,
        LP_ACCEPT_GOODS,
        LP_ACCEPT_GOODS_XML,
        LK_REMARK,
        LK_REMARK_CSV,
        LK_REMARK_XML,
        LK_RECEIPT,
        LK_RECEIPT_XML,
        LK_RECEIPT_CSV,
        LP_GOODS_IMPORT,
        LP_GOODS_IMPORT_CSV,
        LP_GOODS_IMPORT_XML,
        LP_CANCEL_SHIPMENT,
        LP_CANCEL_SHIPMENT_CSV,
        LP_CANCEL_SHIPMENT_XML,
        LK_KM_CANCELLATION,
        LK_KM_CANCELLATION_CSV,
        LK_KM_CANCELLATION_XML,
        LK_APPLIED_KM_CANCELLATION,
        LK_APPLIED_KM_CANCELLATION_CSV,
        LK_APPLIED_KM_CANCELLATION_XML,
        LK_CONTRACT_COMMISSIONING,
        LK_CONTRACT_COMMISSIONING_CSV,
        LK_CONTRACT_COMMISSIONING_XML,
        LK_INDI_COMMISSIONING,
        LK_INDI_COMMISSIONING_CSV,
        LK_INDI_COMMISSIONING_XML,
        LP_SHIP_RECEIPT,
        LP_SHIP_RECEIPT_CSV,
        LP_SHIP_RECEIPT_XML,
        OST_DESCRIPTION,
        OST_DESCRIPTION_CSV,
        OST_DESCRIPTION_XML,
        CROSSBORDER,
        CROSSBORDER_CSV,
        CROSSBORDER_XML,
        LP_INTRODUCE_OST,
        LP_INTRODUCE_OST_CSV,
        LP_INTRODUCE_OST_XML,
        LP_RETURN,
        LP_RETURN_CSV,
        LP_RETURN_XML,
        LP_SHIP_GOODS_CROSSBORDER,
        LP_SHIP_GOODS_CROSSBORDER_CSV,
        LP_SHIP_GOODS_CROSSBORDER_XML,
        LP_CANCEL_SHIPMENT_CROSSBORDER
    }

    private class TokenBucket {

        private int countAvailable;
        private long windowSizeInMilliSeconds;
        private long lastRefillTime;
        private long nextRefillTime;
        private int bucketSize;

        public TokenBucket(long windowSizeInMilliSeconds, int bucketSize) {
            this.windowSizeInMilliSeconds = windowSizeInMilliSeconds;
            this.bucketSize = bucketSize;
        }

        private boolean tryConsume() {
            refill();
            if (this.countAvailable > 0) {
                this.countAvailable--;
                return true;
            }
            return false;
        }

        public synchronized void Consume() throws InterruptedException {
            while (!tryConsume()) {
                Thread.sleep(nextRefillTime - System.currentTimeMillis());
            }
        }

        private void refill() {
            if (System.currentTimeMillis() < this.nextRefillTime) {
                return;
            }
            this.lastRefillTime = System.currentTimeMillis();
            this.nextRefillTime = this.lastRefillTime + this.windowSizeInMilliSeconds;
            this.countAvailable = this.bucketSize;
        }
    }
}
