package com.xniu.rental.pay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AgreementParams;
import com.alipay.api.domain.AlipayOpenAppQrcodeCreateModel;
import com.alipay.api.domain.AlipayFundAuthOperationCancelModel;
import com.alipay.api.domain.AlipayFundAuthOperationDetailQueryModel;
import com.alipay.api.domain.AlipayFundAuthOrderAppFreezeModel;
import com.alipay.api.domain.AlipayFundAuthOrderUnfreezeModel;
import com.alipay.api.domain.AlipayTradePayModel;
import com.alipay.api.domain.AlipayTradeCreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayUserAgreementPageSignModel;
import com.alipay.api.domain.AlipayUserAgreementQueryModel;
import com.alipay.api.domain.AlipayUserAgreementUnsignModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayOpenAppQrcodeCreateRequest;
import com.alipay.api.request.AlipayFundAuthOperationCancelRequest;
import com.alipay.api.request.AlipayFundAuthOperationDetailQueryRequest;
import com.alipay.api.request.AlipayFundAuthOrderAppFreezeRequest;
import com.alipay.api.request.AlipayFundAuthOrderUnfreezeRequest;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.request.AlipayTradePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayUserAgreementPageSignRequest;
import com.alipay.api.request.AlipayUserAgreementQueryRequest;
import com.alipay.api.request.AlipayUserAgreementUnsignRequest;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.pay.config.AlipayProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AlipayGatewayClient {

    private final AlipayProperties properties;

    public AlipayGatewayClient(AlipayProperties properties) {
        this.properties = properties;
    }

    public TradeCreateResult createTrade(String outTradeNo, BigDecimal amount, String subject, String buyerId) {
        ensureReady();
        try {
            var request = new AlipayTradeCreateRequest();
            request.setNotifyUrl(properties.getNotifyUrl());
            var model = new AlipayTradeCreateModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(amount.toPlainString());
            model.setSubject(subject);
            model.setBuyerId(buyerId);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝交易创建失败：" + response.getSubMsg());
            }
            return new TradeCreateResult(response.getTradeNo());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝交易创建异常：" + exception.getErrMsg());
        }
    }

    public AgreementSignResult createAgreementSign(String externalAgreementNo) {
        ensureAgreementReady();
        try {
            var request = new AlipayUserAgreementPageSignRequest();
            request.setNotifyUrl(properties.getAgreementNotifyUrl());
            request.setReturnUrl(properties.getAgreementReturnUrl());
            var model = new AlipayUserAgreementPageSignModel();
            model.setExternalAgreementNo(externalAgreementNo);
            model.setPersonalProductCode(properties.getAgreementPersonalProductCode());
            model.setSignScene(properties.getAgreementSignScene());
            model.setProductCode("GENERAL_WITHHOLDING");
            request.setBizModel(model);
            var response = client().pageExecute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝签约请求创建失败：" + response.getSubMsg());
            }
            return new AgreementSignResult(response.getBody(), response.getAgreementNo(), response.getStatus(), response.getSignTime(), response.getValidTime(), response.getInvalidTime());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝签约请求异常：" + exception.getErrMsg());
        }
    }

    public AgreementQueryResult queryAgreement(String externalAgreementNo, String agreementNo) {
        ensureAgreementReady();
        try {
            var request = new AlipayUserAgreementQueryRequest();
            var model = new AlipayUserAgreementQueryModel();
            model.setExternalAgreementNo(externalAgreementNo);
            model.setAgreementNo(agreementNo);
            model.setPersonalProductCode(properties.getAgreementPersonalProductCode());
            model.setSignScene(properties.getAgreementSignScene());
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝协议查询失败：" + response.getSubMsg());
            }
            return new AgreementQueryResult(response.getAgreementNo(), response.getStatus(), response.getSignTime(), response.getValidTime(), response.getInvalidTime(), response.getSingleQuota());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝协议查询异常：" + exception.getErrMsg());
        }
    }

    public void unsignAgreement(String externalAgreementNo, String agreementNo) {
        ensureAgreementReady();
        try {
            var request = new AlipayUserAgreementUnsignRequest();
            var model = new AlipayUserAgreementUnsignModel();
            model.setExternalAgreementNo(externalAgreementNo);
            model.setAgreementNo(agreementNo);
            model.setPersonalProductCode(properties.getAgreementPersonalProductCode());
            model.setSignScene(properties.getAgreementSignScene());
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝解约失败：" + response.getSubMsg());
            }
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝解约异常：" + exception.getErrMsg());
        }
    }

    public TradePayResult payWithAgreement(String outTradeNo, BigDecimal amount, String subject, String buyerId, String agreementNo) {
        ensureAgreementReady();
        try {
            var request = new AlipayTradePayRequest();
            var model = new AlipayTradePayModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(amount.toPlainString());
            model.setSubject(subject);
            model.setBuyerId(buyerId);
            model.setProductCode(properties.getAgreementPersonalProductCode());
            model.setScene("agreement_pay");
            var agreementParams = new AgreementParams();
            agreementParams.setAgreementNo(agreementNo);
            model.setAgreementParams(agreementParams);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝协议扣款失败：" + response.getSubMsg());
            }
            return new TradePayResult(response.getTradeNo(), response.getTotalAmount());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝协议扣款异常：" + exception.getErrMsg());
        }
    }

    public FundAuthFreezeResult createFundAuthFreeze(String outOrderNo, String outRequestNo, BigDecimal amount, String subject) {
        ensureFundAuthReady();
        try {
            var request = new AlipayFundAuthOrderAppFreezeRequest();
            request.setNotifyUrl(properties.getFundAuthNotifyUrl());
            var model = new AlipayFundAuthOrderAppFreezeModel();
            model.setOutOrderNo(outOrderNo);
            model.setOutRequestNo(outRequestNo);
            model.setAmount(amount.toPlainString());
            model.setOrderTitle(subject);
            model.setProductCode(properties.getFundAuthProductCode());
            model.setSceneCode(properties.getFundAuthSceneCode());
            request.setBizModel(model);
            var response = client().sdkExecute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝资金授权冻结失败：" + response.getSubMsg());
            }
            var orderStr = response.getOrderStr() == null ? response.getBody() : response.getOrderStr();
            return new FundAuthFreezeResult(orderStr, response.getAuthNo(), response.getOperationId(), response.getStatus(), response.getAmount());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝资金授权冻结异常：" + exception.getErrMsg());
        }
    }

    public FundAuthPayResult payWithFundAuth(String outTradeNo, BigDecimal amount, String subject, String buyerId, String authNo) {
        ensureFundAuthReady();
        try {
            var request = new AlipayTradePayRequest();
            var model = new AlipayTradePayModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(amount.toPlainString());
            model.setSubject(subject);
            model.setBuyerId(buyerId);
            model.setProductCode(properties.getFundAuthPayProductCode());
            model.setScene(properties.getFundAuthPayScene());
            model.setAuthNo(authNo);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝授权转支付失败：" + response.getSubMsg());
            }
            return new FundAuthPayResult(response.getTradeNo(), response.getTotalAmount());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝授权转支付异常：" + exception.getErrMsg());
        }
    }

    public FundAuthUnfreezeResult unfreezeFundAuth(String authNo, String outRequestNo, BigDecimal amount, String remark) {
        ensureFundAuthReady();
        try {
            var request = new AlipayFundAuthOrderUnfreezeRequest();
            var model = new AlipayFundAuthOrderUnfreezeModel();
            model.setAuthNo(authNo);
            model.setOutRequestNo(outRequestNo);
            model.setAmount(amount.toPlainString());
            model.setRemark(remark);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝授权解冻失败：" + response.getSubMsg());
            }
            return new FundAuthUnfreezeResult(response.getOperationId(), response.getStatus(), response.getAmount());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝授权解冻异常：" + exception.getErrMsg());
        }
    }

    public FundAuthCancelResult cancelFundAuth(String outOrderNo, String outRequestNo, String authNo, String operationId, String remark) {
        ensureFundAuthReady();
        try {
            var request = new AlipayFundAuthOperationCancelRequest();
            var model = new AlipayFundAuthOperationCancelModel();
            model.setOutOrderNo(outOrderNo);
            model.setOutRequestNo(outRequestNo);
            model.setAuthNo(authNo);
            model.setOperationId(operationId);
            model.setRemark(remark);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝授权撤销失败：" + response.getSubMsg());
            }
            return new FundAuthCancelResult(response.getAction(), response.getAuthNo(), response.getOperationId());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝授权撤销异常：" + exception.getErrMsg());
        }
    }

    public FundAuthQueryResult queryFundAuth(String outOrderNo, String outRequestNo, String authNo, String operationId) {
        ensureFundAuthReady();
        try {
            var request = new AlipayFundAuthOperationDetailQueryRequest();
            var model = new AlipayFundAuthOperationDetailQueryModel();
            model.setOutOrderNo(outOrderNo);
            model.setOutRequestNo(outRequestNo);
            model.setAuthNo(authNo);
            model.setOperationId(operationId);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝授权状态查询失败：" + response.getSubMsg());
            }
            return new FundAuthQueryResult(
                response.getAuthNo(),
                response.getOperationId(),
                response.getStatus(),
                response.getOrderStatus(),
                response.getTotalFreezeAmount(),
                response.getRestAmount()
            );
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝授权状态查询异常：" + exception.getErrMsg());
        }
    }

    public MiniAppQrcodeResult createMiniAppStoreQrcode(String storeCode, String storeName) {
        ensureMiniAppQrcodeReady();
        try {
            var request = new AlipayOpenAppQrcodeCreateRequest();
            var model = new AlipayOpenAppQrcodeCreateModel();
            model.setUrlParam(properties.getMiniAppStorePage());
            model.setQueryParam("storeCode=" + storeCode);
            model.setDescribe(storeName + "门店码");
            model.setSize(properties.getMiniAppQrSize());
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝小程序门店码生成失败：" + response.getSubMsg());
            }
            return new MiniAppQrcodeResult(response.getQrCodeUrl(), response.getQrCodeUrlCircleBlue(), response.getQrCodeUrlCircleWhite());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝小程序门店码生成异常：" + exception.getErrMsg());
        }
    }

    public TradeQueryResult queryTrade(String outTradeNo) {
        ensureReady();
        try {
            var request = new AlipayTradeQueryRequest();
            var model = new AlipayTradeQueryModel();
            model.setOutTradeNo(outTradeNo);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝交易查询失败：" + response.getSubMsg());
            }
            return new TradeQueryResult(response.getTradeNo(), response.getTradeStatus(), new BigDecimal(response.getTotalAmount()));
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝交易查询异常：" + exception.getErrMsg());
        }
    }

    public void refund(String outTradeNo, BigDecimal refundAmount, String refundNo) {
        ensureReady();
        try {
            var request = new AlipayTradeRefundRequest();
            var model = new AlipayTradeRefundModel();
            model.setOutTradeNo(outTradeNo);
            model.setRefundAmount(refundAmount.toPlainString());
            model.setOutRequestNo(refundNo);
            request.setBizModel(model);
            var response = client().execute(request);
            if (!response.isSuccess()) {
                throw BusinessException.badRequest("支付宝退款失败：" + response.getSubMsg());
            }
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝退款异常：" + exception.getErrMsg());
        }
    }

    public boolean verifyNotify(Map<String, String> params) {
        ensureReady();
        try {
            return AlipaySignature.rsaCheckV1(params, properties.getAlipayPublicKey(), properties.getCharset(), properties.getSignType());
        } catch (AlipayApiException exception) {
            throw BusinessException.badRequest("支付宝回调验签异常：" + exception.getErrMsg());
        }
    }

    private AlipayClient client() {
        return new DefaultAlipayClient(
            properties.getServerUrl(),
            properties.getAppId(),
            properties.getPrivateKey(),
            "json",
            properties.getCharset(),
            properties.getAlipayPublicKey(),
            properties.getSignType()
        );
    }

    private void ensureReady() {
        if (!properties.ready()) {
            throw BusinessException.badRequest("支付宝配置未完成，请配置 appId、私钥、公钥和 notifyUrl");
        }
    }

    private void ensureAgreementReady() {
        if (!properties.agreementReady()) {
            throw BusinessException.badRequest("支付宝签约扣款配置未完成，请配置签约产品码、签约场景、回调地址和密钥");
        }
    }

    private void ensureFundAuthReady() {
        if (!properties.fundAuthReady()) {
            throw BusinessException.badRequest("支付宝资金授权配置未完成，请配置资金授权产品码、场景码、回调地址和密钥");
        }
    }

    private void ensureMiniAppQrcodeReady() {
        if (!properties.miniAppQrcodeReady()) {
            throw BusinessException.badRequest("支付宝小程序码配置未完成，请配置 appId、私钥、公钥和小程序门店页路径");
        }
    }

    public record TradeCreateResult(String tradeNo) {
    }

    public record TradeQueryResult(String tradeNo, String tradeStatus, BigDecimal totalAmount) {
    }

    public record AgreementSignResult(String signUrl, String agreementNo, String status, String signTime, String validTime, String invalidTime) {
    }

    public record AgreementQueryResult(String agreementNo, String status, String signTime, String validTime, String invalidTime, String singleQuota) {
    }

    public record TradePayResult(String tradeNo, String totalAmount) {
    }

    public record FundAuthFreezeResult(String orderStr, String authNo, String operationId, String status, String amount) {
    }

    public record FundAuthPayResult(String tradeNo, String totalAmount) {
    }

    public record MiniAppQrcodeResult(String qrCodeUrl, String qrCodeUrlCircleBlue, String qrCodeUrlCircleWhite) {
    }

    public record FundAuthUnfreezeResult(String operationId, String status, String amount) {
    }

    public record FundAuthCancelResult(String action, String authNo, String operationId) {
    }

    public record FundAuthQueryResult(String authNo, String operationId, String status, String orderStatus, String totalFreezeAmount, String restAmount) {
    }
}
