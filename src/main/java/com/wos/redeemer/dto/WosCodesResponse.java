package com.wos.redeemer.dto;

import java.util.List;

public class WosCodesResponse {

    private List<WosCode> codes;

    public List<WosCode> getCodes() {
        return codes;
    }

    public void setCodes(List<WosCode> codes) {
        this.codes = codes;
    }
}