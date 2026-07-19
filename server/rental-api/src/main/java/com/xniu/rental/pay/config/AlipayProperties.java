package com.xniu.rental.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "xniu.alipay")
public class AlipayProperties {

    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String serverUrl = "https://openapi.alipay.com/gateway.do";
    private String charset = "UTF-8";
    private String signType = "RSA2";
    private String notifyUrl;
    private String agreementNotifyUrl;
    private String agreementReturnUrl;
    private String agreementPersonalProductCode;
    private String agreementSignScene;
    private String fundAuthNotifyUrl;
    private String fundAuthProductCode = "PRE_AUTH_ONLINE";
    private String fundAuthSceneCode = "RENT_CAR";
    private String fundAuthPayProductCode = "PRE_AUTH";
    private String fundAuthPayScene = "bar_code";
    private String miniAppStorePage = "pages/index/index";
    private String miniAppQrSize = "s";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getAlipayPublicKey() {
        return alipayPublicKey;
    }

    public void setAlipayPublicKey(String alipayPublicKey) {
        this.alipayPublicKey = alipayPublicKey;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getAgreementNotifyUrl() {
        return agreementNotifyUrl;
    }

    public void setAgreementNotifyUrl(String agreementNotifyUrl) {
        this.agreementNotifyUrl = agreementNotifyUrl;
    }

    public String getAgreementReturnUrl() {
        return agreementReturnUrl;
    }

    public void setAgreementReturnUrl(String agreementReturnUrl) {
        this.agreementReturnUrl = agreementReturnUrl;
    }

    public String getAgreementPersonalProductCode() {
        return agreementPersonalProductCode;
    }

    public void setAgreementPersonalProductCode(String agreementPersonalProductCode) {
        this.agreementPersonalProductCode = agreementPersonalProductCode;
    }

    public String getAgreementSignScene() {
        return agreementSignScene;
    }

    public void setAgreementSignScene(String agreementSignScene) {
        this.agreementSignScene = agreementSignScene;
    }

    public String getFundAuthNotifyUrl() {
        return fundAuthNotifyUrl;
    }

    public void setFundAuthNotifyUrl(String fundAuthNotifyUrl) {
        this.fundAuthNotifyUrl = fundAuthNotifyUrl;
    }

    public String getFundAuthProductCode() {
        return fundAuthProductCode;
    }

    public void setFundAuthProductCode(String fundAuthProductCode) {
        this.fundAuthProductCode = fundAuthProductCode;
    }

    public String getFundAuthSceneCode() {
        return fundAuthSceneCode;
    }

    public void setFundAuthSceneCode(String fundAuthSceneCode) {
        this.fundAuthSceneCode = fundAuthSceneCode;
    }

    public String getFundAuthPayProductCode() {
        return fundAuthPayProductCode;
    }

    public void setFundAuthPayProductCode(String fundAuthPayProductCode) {
        this.fundAuthPayProductCode = fundAuthPayProductCode;
    }

    public String getFundAuthPayScene() {
        return fundAuthPayScene;
    }

    public void setFundAuthPayScene(String fundAuthPayScene) {
        this.fundAuthPayScene = fundAuthPayScene;
    }

    public String getMiniAppStorePage() {
        return miniAppStorePage;
    }

    public void setMiniAppStorePage(String miniAppStorePage) {
        this.miniAppStorePage = miniAppStorePage;
    }

    public String getMiniAppQrSize() {
        return miniAppQrSize;
    }

    public void setMiniAppQrSize(String miniAppQrSize) {
        this.miniAppQrSize = miniAppQrSize;
    }

    public boolean ready() {
        return hasText(appId) && hasText(privateKey) && hasText(alipayPublicKey) && hasText(notifyUrl);
    }

    public boolean agreementReady() {
        return hasText(appId)
            && hasText(privateKey)
            && hasText(alipayPublicKey)
            && hasText(agreementNotifyUrl)
            && hasText(agreementPersonalProductCode)
            && hasText(agreementSignScene);
    }

    public boolean fundAuthReady() {
        return hasText(appId)
            && hasText(privateKey)
            && hasText(alipayPublicKey)
            && hasText(fundAuthNotifyUrl)
            && hasText(fundAuthProductCode)
            && hasText(fundAuthSceneCode)
            && hasText(fundAuthPayProductCode)
            && hasText(fundAuthPayScene);
    }

    public boolean miniAppQrcodeReady() {
        return hasText(appId)
            && hasText(privateKey)
            && hasText(alipayPublicKey)
            && hasText(miniAppStorePage);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
