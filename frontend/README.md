# CRM 前端

Vue 3 + Vite + Element Plus，所有 API 通过 /api 代理到本机后端 8080。

## 启动

在 frontend 目录执行：

```powershell
npm run dev
```

访问 http://127.0.0.1:5173/login。默认仅监听本机。
`npm run build` 生成 dist。

## 真实登录与权限

登录使用数据库员工编号和密码，不再提供模拟登录或任意账号登录。
管理员：ADMIN001 / Admin@123456。
开发模式登录页提供 11 个真实测试账号的一键登录按钮，含采购仓储和产品资料岗位，使用已初始化的密码请求真实接口。
按钮独立放在 src/views/login/DevQuickLogin.vue；正式构建不包含此组件及其测试密码。
后续不需要时删除该文件及登录页中的组件引用、quickLogin 方法即可。
其他测试账号和初始化方式见 [后端认证说明](../backend/AUTH.md)。

登录、退出、个人信息和菜单分别访问 /api/auth/login、/logout、/profile、/menus。
后端返回菜单树，前端使用已开发组件的固定映射注册菜单路由；路由切换时刷新权限。
按钮通过 userStore.hasPerm('权限编码') 判断，实际接口由后端鉴权。
401 清理登录信息并跳转登录页；403 表示当前账号没有权限。

## 页面数据

目录已按工作台、线索、客户、销售、产品、交付、数据分析、系统设置分组。岗位授权、迁移与页面状态见 [目录结构](../backend/NAVIGATION.md)。工作台按工作概览、待办事项、我的日程分开展示；未开发业务页面明确标记待开发，原产品与设置功能继续使用。

| 页面 | 数据来源 | 当前能力 |
|---|---|---|
| 登录/菜单 | 数据库账号、角色、权限、会话 | 真实登录和权限展示 |
| 部门管理 | /api/depts | 查询、新增、修改、删除 |
| 产品分类 | /api/products/categories、/attribute-definitions | 多级分类及产品/SKU属性配置、继承、覆盖、启停 |
| 产品档案 | /api/products、/skus、/categories/{id}/attributes | 按品类动态属性、条件必填、型号及SKU维护、详情、上下架 |
| 员工管理 | /api/system/users、/page | 分页、新增、修改/调部门、状态/离职、重置密码、角色分配 |
| 操作日志 | /api/system/logs | 管理员分页查询成功变更记录 |
| 角色管理 | /api/system/roles、/permissions | 查询、新增、修改、删除、权限树配置 |
| 权限管理 | /api/system/permissions/tree 等 | 管理员维护权限项、层级、菜单展示及启停状态 |
| 线索管理 | /api/leads | 录入、分配、沟通、退回待分配、转客户、标记无效/关闭 |
| 联系人 | /api/contacts | 多联系人、搜索、主要联系人、启停、客户详情维护与跟进关联 |
| 客户档案/公海/跟进 | /api/customers、/pool、/follows | 维护、跟进、转交、申请与审核公海、区域领取 |
| 商机管理 | /api/opportunities | 具体采购意向、阶段、预计金额与日期、赢单/丢单 |
| 报价管理 | /api/quotations | 客户/商机绑定、SKU报价、版本、反馈、最终选定、预览及打印另存PDF |
| 销售订单 | /api/orders | 草稿明细编辑、交付要求、确认、经理变更与取消、历史内容 |
| 交付进度 / 发货记录 | /api/deliveries、/api/shipments | 人员分配、状态与异常、分批发货、错误更正、收货完成 |
| 售后记录 | /api/after-sales | 来源追溯、转交、反馈、图片、结束与重开，接入待办日程 |

已删除 src/mock 下的模拟员工、部门、角色和权限数据，以及 Vite 模拟 API 拦截。
业务页面写死的示例记录、统计数字和登录页示例统计也已移除。
线索/客户/商机测试记录现在来自数据库，均带「【演示】」，完整测试路径见 [WORKFLOW.md](../backend/WORKFLOW.md)。
员工管理、个人改密、日志及产品分页已完成，见 [DELIVERY.md](../backend/DELIVERY.md)。角色维护见 [ROLE.md](../backend/ROLE.md)。

## 代码位置

产品属性使用 src/components/ProductAttributes.vue 根据后端模板渲染，型号和 SKU 共用。木吉他的电箱/缺角字段联动显示，结构摘要根据三块板材的工艺计算。历史参数未知时显示“待完善”，不填入虚构参数。src/components/CategoryAttributes.vue 提供分类属性配置，继承字段显示来源，写操作沿用 product:category:edit 权限，见 [产品分类属性](../backend/PRODUCT_ATTRIBUTES.md)。

- src/api：真实 HTTP 请求
- src/store/user.js：当前员工、权限、菜单
- src/router/index.js：登录和页面权限校验
- src/layout：菜单及顶栏
- src/views/system：员工、角色、部门页面
- src/views/error/forbidden.vue：无权限页面

在同一浏览器多个标签页切换账号会共享 localStorage。对比时先退出再登录，或用不同浏览器配置/隐身窗口。

工作台、个人日程和三个数据分析页面已接入 `/api/dashboard`，不提供公司公告。财务目录停用，旧组件仅兼容保留。日程提醒只在打开“我的日程”时展示。统计口径和权限规则见 [DASHBOARD.md](../backend/DASHBOARD.md)。

工作概览默认登录页为 `/workbench`，独立待办 `/workbench/todos` 支持分页和筛选，日程 `/workbench/schedule` 提供日历/列表切换。工作台首页不再堆放详细图表及快捷入口。

联系人页面和客户详情共用 ContactWorkspace。客户编辑不再单独维护主要联系人字段，历史跟进展示当时联系对象。迁移与权限规则见 [CONTACT.md](../backend/CONTACT.md)。

报价详情从保存快照展示，客户与商机中可查看各自报价，最终报价按每条商机唯一，详见 [QUOTATION.md](../backend/QUOTATION.md)。

订单草稿可以调整明细和交付条件；正式变更与正式取消默认由管理员、销售经理处理，历史与原报价保留。执行 `crm-order-maintenance.sql`，见 [订单说明](../backend/ORDER_CONSTRAINTS.md)。

报价草稿及已作废报价、订单草稿及已取消订单支持逻辑删除，分别需要quotation:delete和order:delete权限。已发送报价和未取消的正式订单继续走原业务流程，见 [逻辑删除说明](../backend/SOFT_DELETE.md)。
