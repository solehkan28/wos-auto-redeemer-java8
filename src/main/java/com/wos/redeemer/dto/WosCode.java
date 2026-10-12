package com.wos.redeemer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WosCode {

    private String giftcode;
    private String date;

    @JsonProperty("validation_status")
    private String validationStatus;

    @JsonProperty("game_type")
    private String gameType;

    @JsonProperty("image_url")
    private String imageUrl;

    private String source;
    private String description;

    public WosCode() {}
    
    public WosCode(String giftcode) {
		super();
		this.giftcode = giftcode;
	}

	public String getGiftcode() {
        return giftcode;
    }

    public void setGiftcode(String giftcode) {
        this.giftcode = giftcode;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
