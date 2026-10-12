package com.wos.redeemer.controller;

import com.wos.redeemer.dto.WosCode;
import com.wos.redeemer.entity.Account;
import com.wos.redeemer.entity.RedemptionLog;
import com.wos.redeemer.repository.AccountRepository;
import com.wos.redeemer.repository.FriendsRepository;
import com.wos.redeemer.repository.RedemptionLogRepository;
import com.wos.redeemer.service.WosCodeDiscoveryService;
import com.wos.redeemer.service.WosRedeemService;

import io.micrometer.core.instrument.util.StringUtils;

import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api")
public class RedeemController {

    private final AccountRepository accountRepository;
    private final RedemptionLogRepository logRepository;
    private final WosCodeDiscoveryService codeService;
    private final WosRedeemService redeemService;
    private final FriendsRepository friendsRepository;
    
    
    public RedeemController(
            AccountRepository accountRepository,
            RedemptionLogRepository logRepository,
            WosCodeDiscoveryService codeService,
            WosRedeemService redeemService,
            FriendsRepository friendsRepository) {

        this.accountRepository = accountRepository;
        this.logRepository = logRepository;
        this.codeService = codeService;
        this.redeemService = redeemService;
        this.friendsRepository = friendsRepository;
    }

    @GetMapping("/accounts")
    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }

    @PostMapping("/accounts")
    public Account createAccount(
            @RequestBody Account account) {

        return accountRepository.save(account);
    }

    /*
     * Test discovery saja.
     *
     * Tidak melakukan redeem.
     */
    @GetMapping("/codes/today")
    public List<WosCode> getTodayCodes() {

        return codeService
                .getTodayValidatedWosCodes();
    }

    /*
     * Flow:
     *
     * 1. Ambil codes dari woscontrol.com
     * 2. Filter WOS
     * 3. Filter validated
     * 4. Filter tanggal hari ini
     * 5. Ambil account aktif dari MySQL
     * 6. Redeem setiap code ke setiap account
     */
    @PostMapping("/redeem-today")
    public TodayRedeemResponse redeemToday(@RequestParam(name = "gc",defaultValue = "",required = false)String gc) {

        List<WosCode> codes = StringUtils.isNotBlank(gc)?Arrays.asList(new WosCode(gc)):
                codeService
                        .getTodayValidatedWosCodes();

        List<Account> accounts =
                accountRepository
                        .findByActiveTrue();
        
        List<Account> friends =
                accountRepository
                        .findByActiveTrue();

       
        int success = 0;
        int failed = 0;

        List<RedeemResult> results =
                Collections.synchronizedList(new ArrayList<>());

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(3);

        try {
            List<Callable<Void>> tasks = new ArrayList<>();

            for (WosCode code : codes) {
                for (Account account : accounts) {
                    tasks.add(() -> {
                        WosRedeemService.Result result =
                                redeemService.redeem(
                                        account.getFid(),
                                        account.getKid(),
                                        code.getGiftcode()
                                );

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }

                        results.add(new RedeemResult(
                                account.getFid(),
                                account.getKid(),
                                code.getGiftcode(),
                                result.getStatus(),
                                result.getResponse(),
                                result.getError(),
                                result.getAttempt()
                        ));

                        return null;
                    });
                }

                for (Account friend : friends) {
                    tasks.add(() -> {
                        WosRedeemService.Result result =
                                redeemService.redeem(
                                        friend.getFid(),
                                        friend.getKid(),
                                        code.getGiftcode()
                                );

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }
                        
                        results.add(new RedeemResult(
                                friend.getFid(),
                                friend.getKid(),
                                code.getGiftcode(),
                                result.getStatus(),
                                result.getResponse(),
                                result.getError(),
                                result.getAttempt()
                        ));

                        return null;
                    });
                }
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);

            for (Future<Void> future : futures) {
                future.get();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } finally {
            executor.shutdown();
        }

        success = successCount.get();
        failed = failedCount.get();

        return new TodayRedeemResponse(
                codes.size(),
                accounts.size() + friends.size(),
                codes.size() * (accounts.size() + friends.size()),
                success,
                failed,
                new ArrayList<>(results)
        );
    }

    @GetMapping("/logs")
    public List<RedemptionLog> getLogs() {

        return logRepository
                .findTop100ByOrderByCreatedAtDesc();
    }

    public static class TodayRedeemResponse {

        private final int codeCount;
        private final int accountCount;
        private final int totalRequest;
        private final int success;
        private final int failed;
        private final List<RedeemResult> results;

        public TodayRedeemResponse(
                int codeCount,
                int accountCount,
                int totalRequest,
                int success,
                int failed,
                List<RedeemResult> results) {

            this.codeCount = codeCount;
            this.accountCount = accountCount;
            this.totalRequest = totalRequest;
            this.success = success;
            this.failed = failed;
            this.results = results;
        }

        public int getCodeCount() {
            return codeCount;
        }

        public int getAccountCount() {
            return accountCount;
        }

        public int getTotalRequest() {
            return totalRequest;
        }

        public int getSuccess() {
            return success;
        }

        public int getFailed() {
            return failed;
        }

        public List<RedeemResult> getResults() {
            return results;
        }
    }

    public static class RedeemResult {

        private final String fid;
        private final String kid;
        private final String giftcode;
        private final Integer httpStatus;
        private final String response;
        private final String error;
        private final int attempt;

        public RedeemResult(
                String fid,
                String kid,
                String giftcode,
                Integer httpStatus,
                String response,
                String error,
                int attempt) {

            this.fid = fid;
            this.kid = kid;
            this.giftcode = giftcode;
            this.httpStatus = httpStatus;
            this.response = response;
            this.error = error;
            this.attempt = attempt;
        }

        public String getFid() {
            return fid;
        }

        public String getKid() {
            return kid;
        }

        public String getGiftcode() {
            return giftcode;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }

        public String getResponse() {
            return response;
        }

        public String getError() {
            return error;
        }

        public int getAttempt() {
            return attempt;
        }
    }

    public static class RedeemRequest {

        @NotBlank
        private String fid;

        @NotBlank
        private String kid;

        @NotBlank
        private String cdk;

        public String getFid() {
            return fid;
        }

        public void setFid(String fid) {
            this.fid = fid;
        }

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }

        public String getCdk() {
            return cdk;
        }

        public void setCdk(String cdk) {
            this.cdk = cdk;
        }
    }
}
