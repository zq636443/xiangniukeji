package com.xniu.rental.contract.dto;

import jakarta.validation.constraints.NotBlank;

public record ContractTemplateRequest(
    @NotBlank(message = "请输入模板编码") String templateCode,
    @NotBlank(message = "请输入模板名称") String templateName,
    @NotBlank(message = "请选择合同类型") String contractType,
    @NotBlank(message = "请输入版本号") String versionNo,
    String providerTemplateId,
    @NotBlank(message = "请输入模板内容") String content,
    String remark
) {
}
