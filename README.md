# WOS Gift Code Redeemer - Java/Spring Boot

Konversi dari `wos_giftcode_redeemer.py` ke Spring Boot.

## Arsitektur

```text
Client / Postman
       |
       v
Spring Boot 2.7 REST API
       |
       +--> WosSigner (MD5 + URL encoding)
       |
       +--> WosRedeemService
       |        |
       |        +--> WOS Gift Code API
       |
       +--> Aiven MySQL
              |
              +--> accounts
              +--> redemption_logs
```

## Requirements

- Java 8
- Maven 3.9+
- MySQL / Aiven MySQL

## Environment variables

```bash
export DB_URL='jdbc:mysql://HOST:PORT/DATABASE?useSSL=true&requireSSL=true&serverTimezone=Asia/Jakarta'
export DB_USERNAME='AVNADMIN'
export DB_PASSWORD='PASSWORD'
export WOS_SECRET='YOUR_SECRET'
export WOS_API_URL='https://wos-giftcode-api.centurygame.com/api/gift_code'
```

Jangan commit `WOS_SECRET` atau password Aiven ke Git.

## Run

```bash
mvn spring-boot:run
```

## Create account

```bash
curl -X POST http://localhost:8080/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{
    "fid": "123456789",
    "kid": "1234",
    "name": "Account 01",
    "active": true
  }'
```

## Redeem satu account

```bash
curl -X POST http://localhost:8080/api/redeem \
  -H 'Content-Type: application/json' \
  -d '{
    "fid": "123456789",
    "kid": "1234",
    "cdk": "GIFT_CODE"
  }'
```

## Redeem semua account aktif

```bash
curl -X POST http://localhost:8080/api/redeem-all \
  -H 'Content-Type: application/json' \
  -d '{
    "cdk": "GIFT_CODE"
  }'
```

## Logs

```bash
curl http://localhost:8080/api/logs
```

## Aiven

Ambil connection information dari Aiven:

- Host
- Port
- Database name
- Username
- Password

Kemudian bentuk:

```text
jdbc:mysql://HOST:PORT/DATABASE?useSSL=true&requireSSL=true&serverTimezone=Asia/Jakarta
```

Hibernate akan membuat tabel secara otomatis karena:

```properties
spring.jpa.hibernate.ddl-auto=update
```

Untuk production, sebaiknya gunakan migration Flyway/Liquibase dan ubah `ddl-auto` menjadi `validate`.


## Java 8 compatibility

Project ini menggunakan Spring Boot 2.7.18 yang kompatibel dengan Java 8. Tidak menggunakan `record`, `jakarta.*`, atau API Java modern.


## Flow redeem terbaru

Endpoint utama:

```text
POST /api/redeem-today
```

Urutan proses:

```text
woscontrol.com/api/codes
        |
        +-- game_type = wos
        |
        +-- validation_status = validated
        |
        +-- date = hari ini
        |      (jam diabaikan)
        |
        v
Gift code hari ini
        |
        v
SELECT active accounts FROM MySQL
        |
        v
Untuk setiap code
    untuk setiap account
        |
        v
POST multipart ke
wos-giftcode.centurygame.com
        |
        v
redemption_logs
```

Untuk melihat code yang lolos filter tanpa redeem:

```text
GET /api/codes/today
```

Timezone default adalah `Asia/Jakarta` dan bisa diubah:

```bash
WOS_TIMEZONE=Asia/Jakarta
```
