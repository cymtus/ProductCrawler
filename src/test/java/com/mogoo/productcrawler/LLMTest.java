package com.mogoo.productcrawler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@SpringBootTest
public class LLMTest {
    @Test
    public void helloworld() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();
        String answer = model.chat("你是谁");
        System.out.println(answer); // Hello World
    }

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Test
    public void testSpringBoot() {
        var answer = openAiChatModel.chat("你是谁");
        System.out.println(answer);
    }

    // 1. 复用 Mapper 提高性能
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 2. 使用 Record 定义数据模型，简洁且安全
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse(int code, List<Product> data, String msg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(String name, List<Sku> skus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sku(
            String name,
            @JsonProperty("std_sale_price") double priceInFen,
            @JsonProperty("std_unit_name") String unit,
            int stocks
    ) {
        // 在 Record 中定义业务逻辑：分转元
        public double getPriceInYuan() {
            return priceInFen / 100.0;
        }
    }


    @Test
    public void testYPCUrl() {
        var url = "https://bshop.guanmai.cn/product/sku/get?level=1&type=1&category_id=A659113";
        var cookie = "cluster_id=guanmai-production;cms_key=jskyg;group_id=3359;open_id=oASQovx7m2RqmO9DPe5JGvLsCns8;sessionid=up8um6uabks86iig20ivi2ap5veubvew;b3Blbmlk=1775108177.3785026;gr_user_id=7e5fba37-fcfa-4045-9fe9-c5cd2cd6ca5c;9beedda875b5420f_gr_last_sent_cs1=3897367;Hm_lvt_d02cd7e3028015e0088f63c017c81147=1775108178,1776029621;HMACCOUNT=EF682B7E69B14551;9beedda875b5420f_gr_session_id=9e5b5872-e0b6-48e1-8f82-65c530381f20;9beedda875b5420f_gr_last_sent_sid_with_cs1=9e5b5872-e0b6-48e1-8f82-65c530381f20;9beedda875b5420f_gr_session_id_sent_vst=9e5b5872-e0b6-48e1-8f82-65c530381f20;9beedda875b5420f_gr_cs1=3897367;Hm_lpvt_d02cd7e3028015e0088f63c017c81147=1776029797";

        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Cookie", cookie)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            processData(response.body());

        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
        }
    }

    public static void processData(String jsonResponse) {
        try {
            // 直接反序列化为对象列表
            var response = MAPPER.readValue(jsonResponse, ApiResponse.class);

            if (response.code() != 0) {
                System.err.printf("接口请求逻辑失败: [%d] %s%n", response.code(), response.msg());
                return;
            }

            // 使用 Stream API 优雅打印
            System.out.println("=".repeat(60));
            System.out.printf("%-20s | %-10s | %-8s | %-8s%n", "商品规格", "单价(元)", "单位", "库存");
            System.out.println("-".repeat(60));

            response.data().stream()
                    .filter(p -> p.skus() != null)
                    .flatMap(p -> p.skus().stream())
                    .forEach(sku -> System.out.printf("%-20s | %-10.2f | %-8s | %-8d%n",
                            sku.name(),
                            sku.getPriceInYuan(),
                            sku.unit(),
                            sku.stocks()
                    ));

            System.out.println("=".repeat(60));

        } catch (Exception e) {
            System.err.println("解析过程中发生异常: " + e.getMessage());
        }
    }
}



