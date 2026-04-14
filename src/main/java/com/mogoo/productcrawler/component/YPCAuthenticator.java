package com.mogoo.productcrawler.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogoo.productcrawler.config.YPCConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class YPCAuthenticator {

    private final YPCConfig ypcConfig;
    private final ObjectMapper objectMapper;
    private final AtomicReference<String> cachedCookie = new AtomicReference<>();

    // Cookie 持久化文件名
    private static final String COOKIE_FILE = "ypc_cookie_cache.txt";

    /**
     * 程序启动时，优先从本地 txt 加载 Cookie
     */
    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(COOKIE_FILE);
            if (Files.exists(path)) {
                String savedCookie = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!savedCookie.isEmpty()) {
                    cachedCookie.set(savedCookie);
                    log.info(">>> 已从本地文件加载持久化 Cookie");
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("读取本地 Cookie 文件异常，将使用配置默认值: {}", e.getMessage());
        }
        // 如果文件不存在或读取失败，使用 yml 中的初始值
        cachedCookie.set(ypcConfig.getCookie());
    }

    /**
     * 外部 Service 获取 Cookie 的入口
     */
    public String getCookie() {
        return cachedCookie.get();
    }

    /**
     * 只有当 Service 判定数据异常时才调用此方法
     */
    public synchronized void refresh(String failedCookie) {
        // 双重检查防止并发重复登录
        if (failedCookie != null && !failedCookie.equals(cachedCookie.get())) {
            log.info("Cookie 已被其他线程更新，跳过登录");
            return;
        }

        try {
            log.info(">>> 正在执行自动登录以刷新持久化 Cookie...");
            String sessionCookie = executeLogin();

            // 组装完整的 Cookie 链（包含站点标识和新 Session）
            String fullCookie = "cluster_id=guanmai-production; group_id=3359; " + sessionCookie;

            // 1. 更新内存，让当前运行中的任务立刻能用
            cachedCookie.set(fullCookie);

            // 2. 覆盖写入 txt 文件，保证下次重启不用再登录
            saveToFile(fullCookie);

            log.info(">>> 自动登录成功！新 Cookie 已同步至内存与本地文件");
        } catch (Exception e) {
            log.error(">>> 自动登录失败: {}", e.getMessage());
        }
    }

    /**
     * 模拟抓包中的 Form 表单登录
     */
    private String executeLogin() throws Exception {
        Map<String, String> params = Map.of(
                "username", ypcConfig.getUsername(),
                "password", ypcConfig.getPassword()
        );

        String formBody = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        try (var client = createUnsafeClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://bshop.guanmai.cn/login"))
                    .header("Cookie", "cluster_id=guanmai-production; group_id=3359; cms_key=jskyg")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("x-guanmai-client", "GmBshop/4.0.0 19d0871fd28dd44c5f08b60f919dc5e2")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Origin", "https://bshop.guanmai.cn")
                    .header("Referer", "https://bshop.guanmai.cn/login")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 如果 body 返回用户未注册，这里会抛出异常进入 catch
                JsonNode resNode = objectMapper.readTree(response.body());
                if (resNode.path("code").asInt() != 0) {
                    throw new RuntimeException("业务登录失败: " + resNode.path("msg").asText());
                }

                List<String> setCookies = response.headers().allValues("set-cookie");
                if (setCookies.isEmpty()) throw new RuntimeException("未发现 set-cookie 响应头");

                return setCookies.stream()
                        .map(c -> c.split(";")[0])
                        .collect(Collectors.joining("; "));
            }
            throw new RuntimeException("HTTP 响应异常: " + response.statusCode());
        }
    }

    private void saveToFile(String cookie) {
        try {
            Files.writeString(Paths.get(COOKIE_FILE), cookie, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("写入 Cookie 文件失败: {}", e.getMessage());
        }
    }

    private HttpClient createUnsafeClient() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new java.security.SecureRandom());
        return HttpClient.newBuilder().sslContext(sslContext).connectTimeout(Duration.ofSeconds(10)).build();
    }
}