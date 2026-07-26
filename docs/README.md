# 途派熊电车租赁平台

本目录包含第一版系统的工程骨架。前端三个项目完全独立，各自维护自己的依赖和锁文件：

- `user-mini`：支付宝用户版小程序，Vue 3 + uni-app。
- `merchant-mini`：支付宝商户版小程序，Vue 3 + uni-app。
- `admin-web`：统一 Web 后台，React + Vite + Ant Design；同一套工程内按账号类型拆分为“总部模式”和“商户模式”。
- `server/rental-api`：后端业务服务，Spring Boot 模块化单体。
- `docs`：PRD、技术架构、开发计划、API、数据库、部署等文档。

开发顺序以 [开发实施计划](./开发实施计划.md) 为准。

核心文档：

- [产品 PRD](./电车租赁小程序PRD.md)
- [API 落地核查](./PRD_API落地核查.md)
- [系统管理权限体系设计](./系统管理权限体系设计.md)
- [结算中心业务规则](./结算中心业务规则.md)
- [配件仓与维保结算规则](./配件仓与维保结算规则.md)
- [技术选型架构](./技术选型架构.md)
- [开发实施计划](./开发实施计划.md)
- [开发进度](./开发进度.md)
- [本地开发运行说明](./本地开发运行说明.md)

常用命令：

```bash
cd user-mini && npm install && npm run build:mp-alipay
cd merchant-mini && npm install && npm run build:mp-alipay
cd admin-web && npm install && npm run build
cd server/rental-api && docker compose -f docker-compose.dev.yml up -d
cd server/rental-api && mvn test
```
