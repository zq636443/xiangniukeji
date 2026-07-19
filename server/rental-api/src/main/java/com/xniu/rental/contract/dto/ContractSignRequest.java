package com.xniu.rental.contract.dto;

public record ContractSignRequest(
    String provider,
    String externalFlowId,
    String signUrl
) {
}
