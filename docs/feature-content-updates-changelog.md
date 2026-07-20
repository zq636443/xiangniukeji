# feature/content-updates 分支改动清单

## 1. 分支信息

- 分支：`feature/content-updates`
- 基线分支：`main`
- 基线提交：`ffc1391 chore: initialize xniu rental platform`
- 远程仓库：`https://github.com/zq636443/xiangniukeji.git`
- 整理日期：`2026-07-20`
- 业务代码及既有文档改动范围：137 个文件
- 代码量变化（不含本清单）：约 7294 行新增、718 行删除

## 2. 登录与账号权限

### 2.1 统一运营后台登录

- 取消登录页独立的“总部登录”入口。
- 登录页仅保留“商户登录”和“出资方登录”。
- 商户登录改为统一运营后台登录接口 `/api/auth/workspace/login`。
- 平台管理员、财务账号登录后自动进入总部管理后台。
- 商户老板、门店经理、门店运营、门店员工、维修人员、仓库人员登录后自动进入商户工作台。
- 登录表单不再预填管理员账号和密码。
- 统一登录接口加入匿名访问白名单，并保留原总部、商户登录接口兼容其他端。

### 2.2 商户账号新建订单权限

- 新增独立权限点 `order.create`。
- 新增账号级直接权限表 `auth_account_permission`。
- 登录后的有效权限由角色权限和账号直接权限合并计算。
- 总部后台“系统管理 > 账号管理”增加“新建订单”权限开关。
- 只有被平台管理员单独授权的商户体系账号可以新建订单或批量导入订单。
- 商户创建订单时强制校验商户归属、门店范围、门店商品、SKU 和资产范围。
- 平台账号、商户账号、消费者账号的订单创建入口增加账号类型隔离，防止跨入口绕过权限。

## 3. 商品、链接与 SKU

- 原“SKU 管理”调整为“链接管理”。
- 一个商品链接可以配置多种规格。
- 规格名称支持自定义，每个规格作为一个 SKU。
- 原“套餐管理”调整为“SKU 管理”。
- 新增和编辑时使用“SKU”名称，不再使用“套餐名称”。
- 每个 SKU 增加独立价格 `price_amount`。
- 门店商品引用商品链接及其 SKU，门店发布时同步 SKU 价格。
- 商品链接编码前缀调整为 `LINK`，SKU 编码前缀调整为 `SKU`。
- 门店商品、外部补录订单、订单导入、用户端商品展示中的“套餐”文案统一调整为“SKU”。
- 新增商户安全的门店商品查询接口，商户端不再复用总部全局商品接口。
- 新增商品链接与 SKU 集成测试，覆盖名称、价格、门店发布和订单金额。

## 4. 自动续租

### 4.1 SKU 续租规则

- 门店 SKU 配置增加“自动续租”开关。
- 增加续租周期单位、续租周期值和续租金额。
- 默认按照原租期、期数和每期金额生成续租参数。
- 关闭自动续租时不保存续租周期和金额。
- 开启自动续租时校验续租周期必须大于 0、续租金额必须大于 0。

### 4.2 订单续租快照

- 订单创建时冻结自动续租开关、周期单位、周期值和续租金额。
- 订单增加续租次数 `renewal_count`。
- 商品后续修改续租规则不会反向改变历史订单。

### 4.3 自动续租执行

- 新增到期订单扫描服务 `OrderRenewalService`。
- 对到期且未归还、开启自动续租的订单生成续租账单。
- 新增续租账单类型、续租租金明细类型和续租生成批次类型。
- 避免同一订单重复生成未结清续租账单。
- 到期订单进入逾期状态并参与补缴、代扣和逾期处理。
- 续租账单支付成功后，预计归还时间按续租周期顺延，续租次数加一。
- 支付成功后如无其他到期未付账单，订单恢复租赁中状态。
- 支付宝主动支付、资金授权扣费、协议代扣成功均接入续租成功处理。
- 协议代扣失败时保留续租账单并进入待补缴/逾期流程。
- 新增总部后台手动运行自动续租扫描入口和执行结果展示。
- 续租租金进入收益台账和月结计算。

## 5. 订单创建、批量导入与订单信息

### 5.1 订单客户信息

- 正式订单增加客户姓名 `customer_name`。
- 正式订单增加客户电话 `customer_phone`。
- 历史订单从账号资料回填客户姓名和电话。
- 订单列表和详情补充客户姓名、联系电话、门店、商品链接、SKU、车架号和电池号。

### 5.2 自定义下单时间

- 订单增加业务下单时间 `ordered_at`。
- 新建订单允许选择当前时间或过去日期。
- 历史订单使用原创建时间回填业务下单时间。
- 列表排序、展示、导入和导出统一使用业务下单时间。

### 5.3 总部与商户新建订单

- 总部后台继续支持单笔新建订单。
- 商户 Web 后台和商户支付宝小程序增加新建订单入口。
- 商户新建订单受 `order.create` 权限控制。
- 商户只能选择授权门店的上架商品、SKU 和空闲资产。
- 创建成功后自动生成完整账单计划。
- 用户账号 ID 改为可选，客户姓名和联系电话可独立录入。

### 5.4 订单批量导入

- 总部订单管理增加批量导入和模板下载。
- 商户订单管理增加批量导入和模板下载。
- 模板支持用户账号 ID、客户姓名、联系电话、门店商品编码、SKU 编码、车架号、电池号、预计取车时间和下单时间。
- 支持 `YYYY-MM-DD`、`YYYY-MM-DD HH:mm` 和带秒时间格式。
- 每行独立事务处理，单行失败不影响其他行。
- 返回总行数、成功数、失败数和逐行错误原因。
- 批量导入同样执行门店范围、商品、SKU、资产状态和账号权限校验。

### 5.5 订单检索、序号与导出

- 总部和商户订单列表增加序号列。
- 支持按订单号、客户姓名、联系电话、门店、商品链接、SKU、车架号和电池号搜索。
- 保留订单状态、门店和用户账号筛选。
- 总部和商户订单列表增加 CSV 导出。
- 导出内容包含客户信息、商品/SKU、资产号、金额、预计归还时间和下单时间。
- 数据库增加订单门店/状态/下单时间、客户姓名、车架和电池索引。

## 6. 未付款发货

- 商户或门店可对待支付订单选择“免付款发货”。
- 发货时直接绑定车架、电池或车电一体资产。
- 发货后订单进入租赁中状态并生成取车交接记录、资产使用记录和状态日志。
- 该流程不要求用户先完成付款。
- 商户支付宝小程序增加确认提示和“免付款发货”操作入口。
- 普通已付款取车流程仍保持原有待取车状态校验。

## 7. 核销金额

- 核销记录增加独立字段 `verification_amount`。
- 券面/参考金额与实际核销金额分开保存。
- 客户在核销准备时可填写实际核销金额，也可以稍后补录。
- 客户忘记填写时，商户、门店或平台管理员可以补录。
- 核销订单生成前允许修改；订单生成后禁止再次修改。
- 未填写实际核销金额时禁止完成验券或消费核销。
- 实际核销金额允许与 SKU 价格不同，但不能小于 0。
- 生成订单时，订单租金、SKU 明细金额和分润结算基数统一使用实际核销金额。
- 用户支付宝小程序、商户支付宝小程序和总部核销管理页均增加核销金额展示和编辑入口。

## 8. 分润规则与月结

### 8.1 PROFIT_V2 分润模型

- 分润计算升级为 `PROFIT_V2`。
- 以实际核销金额或实际结算金额作为分润基数。
- 默认先扣渠道核销扣点 5%。
- 默认再扣租赁平台扣点 3%。
- 剩余金额默认按以下比例分配：
  - 门店运营 15%。
  - 维修基金 10%。
  - 渠道引流 20%。
  - 出资方 55%。
- 新增来源渠道、规则优先级和计算版本。
- 新增渠道、平台、门店运营、维修基金、渠道引流和出资方六类收益明细。
- 分润快照完整冻结命中规则、比例和金额。
- 历史快照保留 `LEGACY_V1`，不会因新版规则上线而重算。
- 月结和收益台账适配新版分润金额及续租租金。

### 8.2 门店独立分润规则

- 分润规则从统一规则调整为每个门店独立规则。
- 已有门店按照当前有效规则自动初始化门店规则。
- 新建门店自动继承当前平台默认规则。
- 结算时门店规则优先于门店商品、商品链接和平台规则。
- 平台管理员可在“分润结算 > 门店分润规则”直接编辑每个门店的比例。
- 商户账号即使拥有结算权限，也不能修改门店分润规则。
- 门店规则修改只影响之后生成的新快照，不回写历史订单和历史月结数据。
- 删除无业务数据门店时同步清理门店分润规则。

## 9. 资产管理

### 9.1 资产批量录入

- 总部资产台账增加批量录入和模板下载。
- 商户 Web 后台增加门店资产批量录入和模板下载。
- 新增 `asset.import` 权限，默认授予平台管理员、商户老板和门店经理。
- 模板支持资产类型、车架号/电池号、出资方编码、门店编码、采购金额、报废残值和采购日期。
- 支持中文资产类型和英文枚举值。
- 商户导入时锁定当前门店，禁止跨门店导入。
- 每行独立事务，返回逐行成功或失败原因。

### 9.2 入库字段调整

- 取消资产入库时手工填写固定维保费。
- 新资产固定维保费按 0 保存。
- 报废残值调整为非必填，可保存为空。
- 采购金额仍为必填项。

### 9.3 车电一体资产

- 新增资产类型 `INTEGRATED_VEHICLE`。
- 车电一体资产只绑定车架号，不需要独立电池号。
- 订单创建、批量导入、正式履约、免付款发货、外部补录订单和资产更换均支持车电一体。
- 选择车电一体后自动清空并禁用独立电池选择。
- 从普通车架切换到车电一体时自动解绑原电池并关闭电池使用记录。
- 资产使用轨迹和出资方收益归属支持车电一体资产。

### 9.4 资产检索、序号与导出

- 总部和商户资产列表增加序号列。
- 支持按资产编码、车架号、电池号、出资方、商户和门店搜索。
- 保留资产类型、状态、门店等筛选。
- 总部和商户资产列表增加 CSV 导出。
- 数据库增加资产类型/状态/门店和商户/状态联合索引。

## 10. 外部补录订单兼容调整

- 外部补录订单中的“套餐”文案调整为“SKU”。
- 商户端改用安全的商户门店商品查询接口。
- 外部补录订单支持绑定车电一体资产。
- 车电一体仅填写车架资产，电池资产留空。
- 批量补录说明同步更新 SKU 和车电一体规则。
- 外部订单资产校验统一使用商品链接的资产需求配置。

## 11. 各端页面变化

### 11.1 总部管理后台 `admin-web`

- 登录页统一运营入口。
- 系统账号管理增加账号级新建订单权限开关。
- 商品管理调整为链接管理、SKU 管理和门店商品。
- 订单管理增加客户信息、资产号、批量导入、模板、自定义下单时间、搜索、序号和导出。
- 资产管理增加批量导入、模板、车电一体、可选残值、搜索、序号和导出。
- 核销管理增加实际核销金额展示和补录。
- 分润结算增加新版分润预览、门店独立规则编辑和新版快照字段。
- 自动扣款页增加自动续租扫描操作。
- 账单、出资方、履约、外部订单等页面适配续租、SKU、车电一体和新版分润字段。

### 11.2 商户 Web 工作台

- 按账号权限显示新建订单入口。
- 支持单笔新建订单、批量导入和模板下载。
- 支持自定义历史下单时间。
- 支持订单搜索、序号和 CSV 导出。
- 支持资产批量导入、模板、搜索、序号和 CSV 导出。
- 商品、订单、履约、收益页面适配 SKU、车电一体、自动续租和新版分润。

### 11.3 商户支付宝小程序

- 按 `order.create` 权限显示新建订单。
- 支持选择门店商品、SKU 和空闲资产创建订单。
- 支持车电一体资产。
- 增加免付款发货。
- 增加核销金额补录。
- 订单和分润展示适配客户信息、自动续租和 PROFIT_V2。

### 11.4 用户支付宝小程序

- 核销准备时可填写实际核销金额。
- 未填写时可稍后补录。
- 核销记录显示参考金额和实际核销金额。
- 订单和商品展示适配 SKU 名称、价格及新版字段。

## 12. 数据库迁移

| 版本 | 文件 | 内容 |
| --- | --- | --- |
| V27 | `V27__voucher_verification_amount.sql` | 增加实际核销金额并补充商户核销权限 |
| V28 | `V28__auto_renewal_schema.sql` | 增加 SKU 和订单自动续租字段 |
| V29 | `V29__merchant_order_create_permission.sql` | 增加 `order.create` 和账号直接权限表 |
| V30 | `V30__profit_sharing_v2.sql` | 增加新版六项分润规则和版本化快照 |
| V31 | `V31__order_customer_and_asset_display.sql` | 增加订单客户姓名和联系电话 |
| V32 | `V32__asset_batch_import_permission.sql` | 增加资产批量导入权限 |
| V33 | `V33__order_business_time.sql` | 增加业务下单时间 |
| V34 | `V34__optional_asset_residual_value.sql` | 报废残值调整为可空 |
| V35 | `V35__product_link_sku_price.sql` | 增加 SKU 价格并迁移原门店价格 |
| V36 | `V36__asset_order_search_indexes.sql` | 增加资产与订单检索索引 |
| V37 | `V37__store_profit_rules.sql` | 初始化每个门店的独立分润规则 |

## 13. 新增主要代码文件

### 前端

- `admin-web/src/components/AssetBatchImportModal.tsx`
- `admin-web/src/components/OrderBatchImportModal.tsx`
- `admin-web/src/utils/csv.ts`

### 资产

- `AssetBatchImportRequest.java`
- `AssetBatchImportResponse.java`
- `AssetBatchImportRowRequest.java`
- `AssetBatchImportRowResultResponse.java`

### 账号权限

- `SystemAccountPermissionUpdateRequest.java`

### 订单与续租

- `AdminOrderRenewalController.java`
- `OrderBatchImportRequest.java`
- `OrderBatchImportResponse.java`
- `OrderBatchImportRowRequest.java`
- `OrderBatchImportRowResultResponse.java`
- `RenewalRunRequest.java`
- `RenewalRunResponse.java`
- `OrderBatchImportService.java`
- `OrderCreationService.java`
- `OrderRenewalService.java`

### 商品

- `MerchantProductController.java`

### 分润

- `StoreProfitRuleUpdateRequest.java`
- `SettlementCalculationVersion.java`
- `ProfitSharingCalculator.java`

### 核销

- `VoucherVerificationAmountRequest.java`

## 14. 测试变化

- 新增 `AssetBatchImportIntegrationTests`。
- 新增 `AuthWorkspaceLoginIntegrationTests`。
- 新增 `MerchantOrderCreationPermissionIntegrationTests`。
- 新增 `OrderBatchImportIntegrationTests`。
- 新增 `ProductLinkSkuIntegrationTests`。
- 新增 `ProfitSharingCalculatorTests`。
- 扩展 `RentalBusinessFlowIntegrationTests`，覆盖：
  - 未付款发货。
  - 自动续租账单生成。
  - 协议代扣失败与成功续租。
  - 核销金额与 SKU 价格不同。
  - 车电一体履约。
  - 新版分润和门店规则。
  - 历史快照冻结。
- 扩展外部订单、系统管理和门店删除测试。

## 15. 当前验证结果

- 后端：`mvn test`，41 个测试全部通过。
- 总部/商户 Web：`npm run build` 通过。
- 商户支付宝小程序：`npm run typecheck`、`npm run build:mp-alipay` 通过。
- 用户支付宝小程序：`npm run typecheck`、`npm run build:mp-alipay` 通过。
- Flyway：数据库迁移已执行到 V37。
- 本地后端：`http://127.0.0.1:8090/actuator/health` 返回 `UP`。
- 本地前端：`http://127.0.0.1:5173/` 可正常访问。
- 已人工验证：
  - 订单和资产搜索、序号与导出入口。
  - 门店分润规则列表、编辑和保存。
  - 分润预览命中门店规则。
  - 统一登录页无总部入口且账号密码为空。
  - 管理员从“商户登录”进入总部管理后台。

## 16. 完整文件范围

### `admin-web`（18 个文件）

- `src/App.tsx`
- `src/components/AssetBatchImportModal.tsx`
- `src/components/OrderBatchImportModal.tsx`
- `src/pages/AgreementDeductManagement.tsx`
- `src/pages/AssetFulfillmentManagement.tsx`
- `src/pages/AssetManagement.tsx`
- `src/pages/BillManagement.tsx`
- `src/pages/ExternalOrderManagement.tsx`
- `src/pages/InvestorWorkspace.tsx`
- `src/pages/MerchantWorkspace.tsx`
- `src/pages/OrderManagement.tsx`
- `src/pages/ProductManagement.tsx`
- `src/pages/SettlementManagement.tsx`
- `src/pages/SystemManagement.tsx`
- `src/pages/VoucherManagement.tsx`
- `src/types/api.ts`
- `src/utils/csv.ts`
- `tsconfig.tsbuildinfo`

### `merchant-mini`（2 个文件）

- `src/pages/index/index.vue`
- `src/types/api.ts`

### `user-mini`（2 个文件）

- `src/pages/index/index.vue`
- `src/types/api.ts`

### `docs`（本清单创建前已有 2 个修改文件）

- `docs/开发进度.md`
- `docs/系统管理权限体系设计.md`

### `server/rental-api`（113 个文件）

- 资产模块：控制器、批量导入 DTO、资产类型、仓储、入库/调拨/履约/维修服务。
- 认证模块：登录控制器、账号权限 DTO、权限查询、系统管理仓储、账号类型隔离、统一登录和直接权限服务。
- 账单模块：续租账单类型、明细类型、生成批次、仓储和账单服务。
- 外部订单模块：创建 DTO 和资产/SKU 校验服务。
- 出资方模块：资产查询适配车电一体和新版收益。
- 商户模块：门店规则自动初始化及删除处理。
- 订单模块：控制器、创建 DTO/响应/模型、仓储、创建服务、批量导入和自动续租。
- 支付模块：协议代扣、资金授权、主动支付接入续租成功处理。
- 商品模块：链接、SKU、门店商品、价格和续租配置。
- 分润模块：PROFIT_V2 计算、门店规则、快照、收益和月结。
- 核销模块：实际核销金额的三端录入、校验、订单生成和分润基数。
- 数据库：V27 至 V37 共 11 个迁移。
- 测试：6 个新增测试文件及 4 个扩展测试文件。

以上范围覆盖当前 `feature/content-updates` 分支相对 `main` 的全部业务改动。
