package com.wos.redeemer.service;

import com.wos.redeemer.dto.WosCode;
import com.wos.redeemer.dto.WosCodesResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class WosCodeDiscoveryService {

    private final RestTemplate restTemplate;

    @Value("${wos.codes.url}")
    private String codesUrl;

    @Value("${wos.timezone:Asia/Jakarta}")
    private String timezone;

    public WosCodeDiscoveryService(
            RestTemplate restTemplate) {

        this.restTemplate = restTemplate;
    }


    public List<WosCode> getTodayValidatedWosCodes() {

        HttpHeaders headers = new HttpHeaders();

        headers.set(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/154.0.0.0 Safari/537.36"
        );

        headers.set(
                "Accept",
                "application/json,text/plain,*/*"
        );

        headers.set(
                "Referer",
                "https://woscontrol.com/"
        );

        HttpEntity<Void> entity =
                new HttpEntity<Void>(headers);

        ResponseEntity<WosCodesResponse> response =
                restTemplate.exchange(
                        codesUrl,
                        HttpMethod.GET,
                        entity,
                        WosCodesResponse.class
                );

        WosCodesResponse body = response.getBody();

        if (body == null || body.getCodes() == null) {
            return Collections.emptyList();
        }

//        final LocalDate today =
//                LocalDate.now(
//                        ZoneId.of(timezone)
//                );

        final LocalDate today = LocalDate.of(2026, 10, 10);

        
        return body.getCodes()
                .stream()
                .filter(this::isWos)
                .filter(this::isValidated)
                .filter(code ->
                        isSameDate(
                                code.getDate(),
                                today
                        )
                )
                .filter(code ->
                        code.getGiftcode() != null
                        && !code.getGiftcode()
                                .trim()
                                .isEmpty()
                )
                .collect(Collectors.toList());
    }

    private LocalDate parseDate(String dateText) {
        String cleanDate = dateText.replace(
                " (Coordinated Universal Time)",
                ""
        );

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "EEE MMM dd yyyy HH:mm:ss z",
                        Locale.ENGLISH
                );

        return ZonedDateTime.parse(
                cleanDate,
                formatter
        ).toLocalDate();
    }
    
    private boolean isWos(WosCode code) {

        return code.getGameType() != null &&
                "wos".equalsIgnoreCase(
                        code.getGameType().trim()
                );
    }

    private boolean isValidated(WosCode code) {

        return code.getValidationStatus() != null &&
                "validated".equalsIgnoreCase(
                        code.getValidationStatus().trim()
                );
    }

    
    private boolean isSameDate(
            String value,
            LocalDate today) {

        if (value == null ||
                value.trim().isEmpty()) {

            return false;
        }

        /*
         * API example:
         *
         * Wed Oct 07 2026 10:10:08 GMT+0000
         * (Coordinated Universal Time)
         *
         * We only take the date/time before GMT.
         */

        try {

            int gmtIndex = value.indexOf("GMT");

            if (gmtIndex == -1) {
                return false;
            }

            String dateOnly =
                    value.substring(0, gmtIndex).trim();

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern(
                            "EEE MMM dd yyyy HH:mm:ss",
                            Locale.ENGLISH
                    );

            LocalDate apiDate =
                    LocalDateTime.parse(
                            dateOnly,
                            formatter
                    ).toLocalDate();

            System.out.println(
                    "API Date : " + apiDate
                    + " | Today : " + today
            );

            return today.equals(apiDate);

        } catch (DateTimeParseException e) {

            System.out.println(
                    "Failed parse date: " + value
            );

            return false;
        }
    }
}
