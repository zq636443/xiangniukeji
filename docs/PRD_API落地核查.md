# PRD API 落地核查

## 1. 核查结论

当前 PRD 的主链路可以落地，但需要把能力分成四类：

| 类型 | 结论 |
| --- | --- |
| 支付宝小程序登录、支付 | 可落地，使用支付宝小程序官方能力。 |
| 支付宝签约扣款 | 可落地，但不是支付宝自动到点扣款，需要平台服务端定时任务主动发起扣款；扣款周期有产品限制。 |
| 所有租期逾期扣费 | 必须进入自动扣费流程，但扣费通道按账单周期和授权方式选择。 |
| 抖音、美团核销 | 平台有对应开放能力，但需要开放平台权限、商家/门店授权或服务商资质。 |
| 身份证 OCR、电子签 | 可落地，但需要单独开通支付宝 OCR/身份认证能力，电子签建议接 e 签宝或支付宝生态电子合同能力。 |
| 商户、门店、SKU、资产、出资方、分润、逾期汇总 | 不依赖第三方 API，属于平台自研后台和数据库能力。 |

最关键的风险点：

- 支付宝周期/商家扣款最短周期为 7 天，不适合小于 7 天的逾期扣费账单。
- 支付宝不会替商户自动跑账单定时器，必须由我们自己的服务端在账单日主动调用扣款接口。
- 周期/商家扣款、芝麻免押、预授权、OCR、电子合同、抖音/美团核销都需要提前申请产品权限，不能等开发完再申请。
- 第一版只做支付宝小程序是正确的，微信端先不要进入研发范围。

## 2. 支付宝能力映射

### 2.1 登录与用户体系

| PRD 功能 | 落地方式 | 外部 API / 能力 | 状态 |
| --- | --- | --- | --- |
| 支付宝小程序登录 | 前端获取 authCode，服务端换取 user_id | `my.getAuthCode`、`alipay.system.oauth.token` | 可落地 |
| 用户信息绑定 | 服务端用 user_id 绑定平台 user_id | 平台自研用户表 | 可落地 |
| 手机号授权 | 视支付宝小程序能力和授权范围接入 | 支付宝小程序授权能力 | 需确认授权范围 |

官方依据：

- 支付宝小程序 `my.getAuthCode`：https://opendocs.alipay.com/mini/api/openapi-authorize
- 支付宝 OAuth 换取授权令牌：https://opendocs.alipay.com/open/009zb5

### 2.2 普通支付

| PRD 功能 | 落地方式 | 外部 API / 能力 | 状态 |
| --- | --- | --- | --- |
| 线下直租首期支付 | 服务端创建交易，小程序唤起收银台 | `alipay.trade.create` + `my.tradePay` | 可落地 |
| 签单费支付 | 同普通支付 | `alipay.trade.create` + `my.tradePay` | 可落地 |
| 押金支付 | 普通支付或预授权 | `alipay.trade.create` / 资金授权 | 可落地 |
| 补缴、逾期费 | 主动支付账单 | `alipay.trade.create` + `my.tradePay` | 可落地 |
| 退款 | 后台发起退款 | `alipay.trade.refund` | 可落地 |
| 对账 | 下载支付宝账单后和平台账单核对 | `alipay.data.dataservice.bill.downloadurl.query` | 可落地 |

官方依据：

- 小程序支付 `my.tradePay`：https://opendocs.alipay.com/mini/api/openapi-pay
- 统一收单交易创建 `alipay.trade.create`：https://opendocs.alipay.com/mini/05x9kv
- 支付宝小程序支付接入指南：https://opendocs.alipay.com/mini/05x9ku

### 2.3 签约与自动扣款

| PRD 功能 | 落地方式 | 外部 API / 能力 | 状态 |
| --- | --- | --- | --- |
| 用户签约自动扣款 | 用户在支付宝完成协议签约 | `alipay.user.agreement.page.sign` 或小程序签约能力 | 可落地，需开通 |
| 支付并签约 | 首期支付时同步完成签约 | 支付并签约场景 | 可落地，需开通 |
| 独立签约后扣款 | 先签约，后续按账单扣款 | 独立签约后扣款场景 | 可落地，需开通 |
| 后续账单自动扣款 | 平台服务端定时任务主动扣款 | 签约协议号 + 支付宝扣款接口 | 可落地，但需自研账单调度 |
| 签约状态查询 | 后台定期或操作时查询协议状态 | `alipay.user.agreement.query` | 可落地 |
| 用户解约处理 | 接收解约回调或主动查询 | `alipay.user.agreement.unsign` / 异步通知 | 可落地 |

官方依据：

- 周期扣款产品介绍：https://opendocs.alipay.com/open/20190319114403226822
- 独立签约后扣款场景：https://opendocs.alipay.com/open/00a05b
- 支付并签约场景：https://opendocs.alipay.com/open/041bxs
- 个人协议页面签约接口：https://opendocs.alipay.com/open/8bccfa0b_alipay.user.agreement.page.sign
- 到期是否自动扣款说明：https://opendocs.alipay.com/support/01rg2q
- 周期/商家扣款规则设置说明：https://opendocs.alipay.com/support/01rg2d
- 周期扣款接入流程说明：https://opensupport.alipay.com/support/helpcenter/109/201602484737
- 统一收单交易支付接口 `alipay.trade.pay`：https://opendocs.alipay.com/apis/api_1/alipay.trade.pay

必须按以下规则实现：

- 平台需要建设 `账单生成任务`、`扣款任务`、`失败重试任务`、`逾期汇总任务`。
- 支付宝允许商家在约定扣款日前 5 天至扣款日当天发起扣款。
- 周期/商家扣款最短周期为 7 天。
- 后续扣款必须由商户服务端调用 `alipay.trade.pay` 并传入签约协议号 `agreement_no`。
- 扣款失败不能自动视为坏账，需要进入逾期订单汇总。

### 2.4 统一逾期扣费

PRD 要求所有租期的订单逾期后都进入自动扣费流程。这里需要区分“业务要求”和“支付产品能力”：所有逾期都要扣费，但不能全部依赖支付宝周期/商家扣款，因为周期/商家扣款最短周期为 7 天。

可落地方案：

| 方案 | 说明 | 建议 |
| --- | --- | --- |
| 周期/商家扣款 | 适用于 7 天及以上账单周期，由平台服务端按账单日主动扣款 | 用于周租、月租、自定义长周期 |
| 芝麻免押/预授权扣费 | 用户租车前完成信用授权或资金冻结，归还/逾期时按实际费用扣除 | 用于小于 7 天逾期费和短周期补扣 |
| 用户主动补缴 | 逾期后生成补缴账单，用户点击支付 | 必须保留 |
| 人工处理 | 多次失败后进入逾期汇总和催缴 | 必须保留 |

统一策略：

1. 所有租赁订单取车前必须确认逾期扣费通道。
2. 7 天及以上账单周期优先使用支付宝周期/商家扣款。
3. 小于 7 天的逾期费用优先使用芝麻免押/资金授权扣费。
4. 授权额度不足、扣款失败或协议失效时，生成补缴账单。
5. 多次失败进入逾期订单汇总和人工催缴。

官方依据：

- 芝麻免押产品介绍：https://opendocs.alipay.com/open/03w0a6
- 创建免押订单接口：https://opendocs.alipay.com/open/03w0ab
- 预授权支付产品介绍：https://opendocs.alipay.com/open/repo-0243e2
- 线上资金授权冻结接口：https://opendocs.alipay.com/open/064jhe

## 3. 身份认证、OCR 与电子签

### 3.1 身份证 OCR / 实名

| PRD 功能 | 落地方式 | 外部 API / 能力 | 状态 |
| --- | --- | --- | --- |
| 身份证正反面上传 | 小程序上传图片，服务端存储 | 自研文件服务 | 可落地 |
| 身份证 OCR | 调用支付宝 OCR 或第三方 OCR | 支付宝服务端 OCR / 小程序 OCR 插件 | 可落地，需开通 |
| 人脸核身 | 调用支付宝身份认证或第三方认证 | 支付宝身份认证/蚂蚁人脸认证 | 可落地，需开通 |
| 实名信息留存 | 脱敏存储实名信息 | 平台自研 | 可落地 |

官方依据：

- 支付宝服务端 OCR 接口：https://opendocs.alipay.com/open/cf0277e4_datadigital.fincloud.generalsaas.ocr.server.detect
- 支付宝小程序 OCR 身份证插件：https://opendocs.alipay.com/mini/plugin/ocr-id
- 支付宝身份认证产品介绍：https://opendocs.alipay.com/open/02zlo2

### 3.2 电子合同

| PRD 功能 | 落地方式 | 外部 API / 能力 | 状态 |
| --- | --- | --- | --- |
| 合同模板管理 | 后台维护模板和变量 | 自研 + 电子签平台模板能力 | 可落地 |
| 发起合同签署 | 创建签署流程，返回签署链接 | e 签宝/支付宝电子合同能力 | 可落地，需开通 |
| 查询签署状态 | 定时查询或接收回调 | 电子签平台 API | 可落地 |
| 下载签署版 PDF | 合同签署完成后下载归档 | 电子签平台 API | 可落地 |

官方依据：

- 支付宝 e 签宝电子合同接入准备：https://opendocs.alipay.com/open/00pq4d
- 创建电子合同签署流程接口：https://opendocs.alipay.com/open/04g3b0
- 签署流程查询接口：https://opendocs.alipay.com/open/04g4o6
- 获取合同下载地址接口：https://opendocs.alipay.com/open/04ffrn

落地建议：

- 第一版不要自研电子签，直接接 e 签宝或支付宝生态电子合同能力。
- 合同不写死资产编号，资产编号进入交接单/变更单/归还单。

## 4. 抖音、美团核销

| PRD 功能 | 落地方式 | 外部 API / 能力 | 状态 |
| --- | --- | --- | --- |
| 抖音券码验券 | 调用抖音生活服务验券接口 | `certificate.prepare` / `certificate.verify` | 可落地，需权限 |
| 抖音券状态查询 | 调用券状态查询接口 | `certificate.query` | 可落地，需权限 |
| 美团团购券验券 | 调用美团团购券准备/核销接口 | 美团团购券 API | 可落地，需权限 |
| 美团核销记录查询 | 调用美团核销查询接口 | 美团团购券查询 API | 可落地，需权限 |

官方依据：

- 抖音验券准备：https://developer.open-douyin.com/docs/resource/zh-CN/local-life/develop/OpenAPI/life.capacity.fulfilment/certificate.prepare
- 抖音验券：https://developer.open-douyin.com/docs/resource/zh-CN/local-life/develop/OpenAPI/general-capabilities/life.capacity.fulfilment/certificate.verify
- 抖音券状态查询：https://developer.open-douyin.com/docs/resource/zh-CN/local-life/develop/OpenAPI/general-capabilities/life.capacity.fulfilment/certificate.query
- 美团团购券验券准备：https://developer.meituan.com/docs/api/tuangou-coupon-prepare
- 美团团购券核销：https://developer.meituan.com/docs/api/tuangou-coupon-consume

风险：

- 这些不是普通小程序能力，必须确认开放平台资质、商家授权和门店授权。
- 第一版如果权限未拿到，可先做“券码录入 + 人工核销登记”，但不能宣称自动核销已完成。

## 5. 平台自研 API 清单

以下能力不需要第三方 API，但必须由我们后端提供 REST API / 管理后台接口。

### 5.1 商户与门店

| 模块 | 建议 API |
| --- | --- |
| 商户管理 | `POST /admin/merchants`、`GET /admin/merchants`、`PATCH /admin/merchants/{id}` |
| 门店管理 | `POST /admin/stores`、`GET /admin/stores`、`PATCH /admin/stores/{id}` |
| 门店二维码 | `POST /admin/stores/{id}/qrcode` |
| 门店商品展示 | `GET /mini/stores/{store_id}/products` |

### 5.2 商品、SKU、套餐

| 模块 | 建议 API |
| --- | --- |
| 全局 SKU | `POST /admin/skus`、`GET /admin/skus`、`PATCH /admin/skus/{id}` |
| 门店 SKU | `POST /admin/store-skus`、`GET /admin/store-skus`、`PATCH /admin/store-skus/{id}` |
| 批量上架 | `POST /admin/store-skus/batch-publish` |
| 套餐管理 | `POST /admin/packages`、`PATCH /admin/packages/{id}` |
| 分润规则 | `POST /admin/share-rules`、`PATCH /admin/share-rules/{id}` |

### 5.3 订单、账单、扣款

| 模块 | 建议 API |
| --- | --- |
| 创建订单 | `POST /mini/orders` |
| 查询订单 | `GET /mini/orders/{id}`、`GET /admin/orders` |
| 创建账单 | `POST /internal/bills/generate` |
| 支付宝支付下单 | `POST /pay/alipay/trade-create` |
| 支付回调 | `POST /pay/alipay/notify` |
| 签约回调 | `POST /pay/alipay/agreement-notify` |
| 扣款任务 | `POST /internal/deductions/run` |
| 扣款失败重试 | `POST /admin/bills/{id}/retry-deduct` |
| 逾期汇总 | `GET /admin/overdue-orders` |

### 5.4 资产与出资方

| 模块 | 建议 API |
| --- | --- |
| 资产台账 | `POST /admin/assets`、`GET /admin/assets`、`PATCH /admin/assets/{id}` |
| 出资方管理 | `POST /admin/investors`、`GET /admin/investors`、`PATCH /admin/investors/{id}` |
| 资产归属 | `POST /admin/assets/{id}/ownership` |
| 资产交接 | `POST /admin/orders/{id}/asset-handover` |
| 资产更换 | `POST /admin/orders/{id}/asset-change` |
| 资产归还 | `POST /admin/orders/{id}/asset-return` |
| 出资方收益 | `GET /admin/investor-income` |

### 5.5 合同与实名

| 模块 | 建议 API |
| --- | --- |
| 上传身份证 | `POST /mini/identity/id-card-images` |
| OCR 识别 | `POST /identity/ocr/id-card` |
| 实名认证 | `POST /identity/certify/init`、`POST /identity/certify/query` |
| 合同模板 | `POST /admin/contract-templates` |
| 发起签署 | `POST /contracts/signflows` |
| 签署回调 | `POST /contracts/notify` |
| 下载归档 | `POST /contracts/{id}/archive` |

## 6. 必须调整的 PRD 落地口径

已建议同步到 PRD：

- 支付宝自动扣款不是平台“配置后自动发生”，必须由平台服务端定时任务发起。
- 支付宝周期/商家扣款不适合小于 7 天的逾期扣费；短周期逾期要走免押/预授权/主动补缴。
- 第一版只做支付宝小程序，不做微信支付和微信扣款。
- 电子合同和 OCR 不是基础小程序能力，需要单独开通或接第三方。

## 7. 上线前商务/技术开通清单

必须上线前完成：

- 企业支付宝账号。
- 支付宝小程序应用。
- JSAPI 支付 / 小程序支付产品开通。
- 周期扣款或商家扣款产品开通。
- 芝麻免押或资金预授权产品开通。
- OCR / 身份认证能力开通。
- e 签宝或支付宝电子合同能力开通。
- 抖音生活服务开放平台权限。
- 美团技术服务合作中心/团购券核销权限。
- 支付宝回调域名、密钥、证书、验签配置。
- 账单定时任务、扣款幂等、失败重试、逾期汇总、对账任务。

## 8. 最终判断

第一版可以真实落地，但推荐第一版边界收敛为：

- 支付宝小程序。
- 支付宝登录和支付。
- 所有租期逾期都进入自动扣费流程。
- 支付宝周期/商家扣款用于 7 天及以上账单周期。
- 小于 7 天的逾期费用使用芝麻免押/预授权或主动补缴。
- 电子合同接 e 签宝/支付宝生态电子合同。
- 抖音、美团核销在拿到权限后接自动 API；权限未拿到时只做人工登记入口。

这样不会纸上谈兵，也不会在开发完成后被支付能力限制卡死。
