package com.wos.redeemer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wos.redeemer.entity.RedemptionLog;
import com.wos.redeemer.repository.RedemptionLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WosRedeemService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_SLEEP_MS = 3000L;
    private static final long RETRY_SLEEP_CLAIM_MS = 60000L;

    private final WosSigner signer;
    private final RedemptionLogRepository logRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wos.api.url}")
    private String apiUrl;

    public WosRedeemService(
            WosSigner signer,
            RedemptionLogRepository logRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {

        this.signer = signer;
        this.logRepository = logRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Result redeem(String fid, String kid, String cdk) {

    	System.out.println("start redeem");
        Result lastResult = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            if (attempt > 1) {
                sleepBeforeRetry();
            }

            lastResult = redeemOnce(fid, kid, cdk, attempt);

            if (lastResult.isSuccess()) {
                return lastResult;
            }
        }

        return lastResult;
    }

    private Result redeemOnce(
            String fid,
            String kid,
            String cdk,
            int attempt) {

        long unixTime =
                ZonedDateTime
                        .now(ZoneId.of("Asia/Jakarta"))
                        .toEpochSecond();

        Map<String, Object> payload =
                new LinkedHashMap<String, Object>();

        payload.put("cdk", cdk);
        payload.put("fid", fid);
        payload.put("kid", kid);
        payload.put("time", unixTime);

        // Sign dibuat dari payload SEBELUM sign ditambahkan.
        String sign = signer.makeSign(payload);
        payload.put("sign", sign);

        RedemptionLog log = new RedemptionLog();
        log.setFid(fid);
        log.setKid(kid);
        log.setGiftCode(cdk);
        log.setCreatedAt(LocalDateTime.now());

        try {
            MultiValueMap<String, String> form =
                    new LinkedMultiValueMap<String, String>();

            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                form.add(entry.getKey(), String.valueOf(entry.getValue()));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Arrays.asList(
                    MediaType.APPLICATION_JSON,
                    MediaType.TEXT_PLAIN
            ));
            headers.set(
                    HttpHeaders.ORIGIN,
                    "https://wos-giftcode.centurygame.com"
            );
            headers.set(
                    HttpHeaders.REFERER,
                    "https://wos-giftcode.centurygame.com/"
            );
            headers.set(
                    HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (X11; Linux x86_64) " +
                    "AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) " +
                    "Chrome/153.0.0.0 Safari/537.36"
            );

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<MultiValueMap<String, String>>(
                            form,
                            headers
                    );

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            apiUrl,
                            HttpMethod.POST,
                            request,
                            String.class
                    );

            int status = response.getStatusCodeValue();
            String body = response.getBody();
            boolean success = isAcceptedResponse(status, body);

            log.setHttpStatus(status);
            log.setResponse(body);
            if (!success) {
                log.setErrorMessage(
                        "Attempt " + attempt + "/" + MAX_ATTEMPTS +
                        " not accepted"
                );
            }
            logRepository.save(log);

            return new Result(
                    status,
                    body,
                    sign,
                    null,
                    success,
                    attempt
            );

        } catch (Exception e) {
        	System.out.println("Jeda waktu 20 detik dulu");
        	sleepBeforeRetryClaim();
            log.setErrorMessage(
                    "Attempt " + attempt + "/" + MAX_ATTEMPTS +
                    ": " + e.getMessage()
            );
            logRepository.save(log);

            return new Result(
                    null,
                    "",
                    sign,
                    e.getMessage(),
                    false,
                    attempt
            );
        }
    }

    /**
     * Hanya dua response yang dianggap berhasil:
     *
     * HTTP 200 + msg = SUCCESS
     * HTTP 200 + msg = RECEIVED.
     *
     * Response HTTP 200 lainnya tetap dianggap gagal dan akan retry.
     */
    private boolean isAcceptedResponse(int status, String body) {

    	System.out.println(body);
        if (status != 200 || body == null || body.trim().isEmpty()) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode msgNode = root.get("msg");

            if (msgNode == null || msgNode.isNull()) {
                return false;
            }

            String msg = msgNode.asText();

            return "SUCCESS".equals(msg)
                    || "RECEIVED.".equals(msg);

        } catch (Exception e) {
            return false;
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void sleepBeforeRetryClaim() {
        try {
            Thread.sleep(RETRY_SLEEP_CLAIM_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class Result {

        private final Integer status;
        private final String response;
        private final String sign;
        private final String error;
        private final boolean success;
        private final int attempt;

        public Result(
                Integer status,
                String response,
                String sign,
                String error,
                boolean success,
                int attempt) {

            this.status = status;
            this.response = response;
            this.sign = sign;
            this.error = error;
            this.success = success;
            this.attempt = attempt;
        }

        public Integer getStatus() {
            return status;
        }

        public String getResponse() {
            return response;
        }

        public String getSign() {
            return sign;
        }

        public String getError() {
            return error;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getAttempt() {
            return attempt;
        }
    }
}
