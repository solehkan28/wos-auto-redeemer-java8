package com.wos.redeemer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class WosSigner {

    @Value("${wos.secret}")
    private String secret;

    public String makeSign(Map<String, Object> data) {

        List<String> keys =
                new ArrayList<String>(data.keySet());

        Collections.sort(keys);

        List<String> parts =
                new ArrayList<String>();

        for (String key : keys) {

            Object rawValue = data.get(key);

            String value =
                    String.valueOf(rawValue);

            String encoded;

            try {

                encoded =
                        URLEncoder.encode(
                                value,
                                "UTF-8"
                        );

            } catch (UnsupportedEncodingException e) {

                throw new IllegalStateException(
                        "UTF-8 encoding error",
                        e
                );
            }

            /*
             * Python:
             *
             * urllib.parse.quote(
             *     str(value),
             *     safe="~()*!.'"
             * )
             */

            encoded = encoded
                    .replace("+", "%20")
                    .replace("%7E", "~")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%2A", "*")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%2E", ".");

            parts.add(
                    key + "=" + encoded
            );
        }

        String raw =
                String.join("&", parts) + secret;

        return md5(raw);
    }

    private String md5(String value) {

        try {

            MessageDigest md =
                    MessageDigest.getInstance("MD5");

            byte[] digest =
                    md.digest(
                            value.getBytes("UTF-8")
                    );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : digest) {

                result.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return result.toString();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "MD5 error",
                    e
            );
        }
    }
}
