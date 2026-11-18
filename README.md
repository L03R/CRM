# 弦木 CRM

面向吉他及乐器配件工贸企业的客户关系管理系统，采用 **Vue 3 + Spring Boot 3 + MyBatis + MySQL**，前后端分离开发。

项目以“弦木乐器工贸有限公司”为业务背景，管理木吉他、电吉他、拾音器、音箱、琴包、琴弦等产品，贯通线索获取、客户维护、商机、报价、订单、交付和售后反馈。

## 项目特点

- **销售流程闭环**：从市场录入线索到成交、分批发货及售后追溯，业务记录相互关联。
- **角色权限与数据范围**：员工可拥有多个角色，菜单、操作及具体数据分别受权限约束。
- **按品类配置产品属性**：多级分类支持属性继承与覆盖，型号和销售 SKU 使用不同属性模板。
- **交易内容留痕**：保留报价版本、客户反馈、内容快照和正式订单变更历史。
- **真实接口与数据库**：业务页面调用后端接口，数据保存在 MySQL。
- **清晰的分层代码**：Java 按 Controller、Service、Mapper 等分包，SQL 集中在 XML 中。

## 功能模块

| 模块 | 主要功能 |
|---|---|
| 工作台 | 工作概览、自动业务待办、个人日程、日历和列表视图 |
| 线索管理 | 录入、分配、跟进、退回待分配、转客户、标记无效及关闭 |
| 客户管理 | 客户档案、多联系人、主要联系人、跟进、转交、公海申请与审核、领取 |
| 销售管理 | 商机阶段与筛选、报价版本、客户反馈、最终报价、订单生成与维护 |
| 产品管理 | 多级分类、动态属性、属性继承与覆盖、型号、销售 SKU、上下架 |
| 交付协同 | 负责人分配、生产备货进度、延期与异常、分批发货、错误更正、收货完成、售后记录 |
| 数据分析 | 线索转化分析、客户与跟进分析、销售商机分析 |
| 系统设置 | 员工、部门、角色、权限、操作日志 |

当前菜单结构：

~~~text
工作台
├── 工作概览
├── 待办事项
└── 我的日程
线索管理
客户管理
├── 客户档案
├── 联系人
├── 客户公海
└── 跟进记录
销售管理
├── 商机管理
├── 报价管理
└── 销售订单
产品管理
├── 产品档案
└── 产品分类
交付协同
├── 订单交付进度
├── 发货记录
└── 售后记录
数据分析
├── 线索转化分析
├── 客户与跟进分析
└── 销售商机分析
系统设置
├── 员工管理
├── 部门管理
├── 角色管理
├── 权限管理
└── 操作日志
~~~

具体可见菜单由当前员工的有效角色和权限决定。

## 业务流程与规则

~~~text
市场录入线索 → 经理分配 → 销售联系
                         ├── 尚待核实：继续跟进或退回待分配
                         ├── 无效信息：标记无效或关闭
                         └── 确认合作价值：转为客户，明确负责人
                                           ↓
                          具体采购需求 → 商机 → 报价与客户反馈
                                           ↓
                              已接受的最终报价 → 订单草稿
                                           ↓
                                正式确认 → 生产或备货
                                           ↓
                                 分批发货 → 确认收货
                                           ↓
                           售后问题登记 → 转交 → 跟进与结果记录
~~~

- 一个客户可有多个联系人、多个商机，每份报价绑定具体客户和商机。
- 同一商机可保留多份报价及修订版本，最终报价按商机唯一，且须经客户接受。
- 接受报价和正式下单分开处理，订单确认前核对收货信息、承诺交期及下单依据。
- 正式订单按权限变更或取消并保留历史；开始执行后，还需衔接生产、仓储的执行影响确认。
- 发货按订单明细累计，限制超量发货；错误登记通过更正留痕。
- 暂无采购需求的客户可继续维护，需要重新分配时由经理审核进入公海。
- 售后用于记录问题、关联来源和跟踪反馈，具体维修、退货、补发或退款由相关部门处理。

系统定位于客户经营和销售、交付协同。生产工序、库存账、采购及财务核算由专业系统或其他业务流程承担。

## 技术栈

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3、JavaScript、Vue Router 4、Pinia |
| UI 与交互 | Element Plus、Element Plus Icons、Axios |
| 前端构建 | Vite 5、npm |
| 后端 | Java 21、Spring Boot 3.5.16、Spring MVC |
| 认证与授权 | Spring Security、RBAC、数据库会话 Token、BCrypt |
| 数据访问 | MyBatis（Spring Boot Starter 3.0.5）、XML SQL、Spring 事务 |
| 数据库 | MySQL、InnoDB；项目验证环境为 MySQL 8.4 |
| 校验与辅助 | Jakarta Validation、Lombok |
| 构建与测试 | Maven、JUnit 5、Spring Boot Test、MockMvc、Spring Security Test |

## 项目结构

~~~text
CRM/
├── frontend/
│   ├── src/
│   │   ├── api/                  HTTP 接口封装
│   │   ├── components/           公共组件及业务表单
│   │   ├── config/               业务枚举和显示配置
│   │   ├── layout/               顶栏与侧栏
│   │   ├── router/               动态路由和页面映射
│   │   ├── store/                登录状态与权限
│   │   ├── utils/                工具方法
│   │   └── views/                业务页面
│   ├── public/
│   ├── package.json
│   └── vite.config.js
├── backend/
│   ├── src/main/java/com/crm/
│   │   ├── controller/           参数接收与权限声明
│   │   ├── service/              业务规则和事务
│   │   ├── mapper/               数据访问接口
│   │   ├── entity/               数据库实体
│   │   ├── dto/                  请求对象
│   │   ├── vo/                   响应对象
│   │   ├── config/               框架配置与初始化
│   │   ├── security/             认证及数据范围
│   │   └── common/               通用响应和异常处理
│   ├── src/main/resources/
│   │   ├── mapper/               MyBatis XML
│   │   └── application.yml
│   ├── src/test/                 接口与业务回归测试
│   └── pom.xml
├── database/                     建表、升级及测试数据 SQL
├── scripts/                      测试场景创建与验证脚本
├── .gitignore
├── AGENTS.md                     项目代码约定
└── README.md
~~~

Service 使用具体类承载业务，SQL 放在 Mapper XML 中；后端统一返回包含 code、message、data 的响应。

## 本地开发

### 环境要求

| 软件 | 要求 |
|---|---|
| JDK | 21 |
| Maven | 与当前 Spring Boot 构建兼容的 Maven 3.x |
| Node.js | 20 或更高版本，使用当前维护的 LTS 版本 |
| MySQL | 建议使用已验证的 8.4 环境 |
| Python | 仅执行 scripts 中的辅助脚本时需要 |

### 1. 准备数据库

后端不会自动建表，运行前需要存在与当前代码版本匹配的 crm 数据库、表结构及基础权限数据。

database 目录保存历次建表和增量升级 SQL，当前没有统一的一键安装入口。部分脚本依赖前置表、权限或迁移记录，部分初始化脚本重复执行会补回初始授权。**不要按文件名排序后直接批量重跑，也不要在已有业务库上重新执行基础建表脚本。**

已有运行环境迁移时，可从受信任的源数据库导出完整表结构及所需数据，再在新环境导入；登录会话应排除迁移。全新空库安装需要按基础组织/RBAC、产品属性、导航、客户流程、联系人、报价订单、交付售后的依赖顺序整理执行。首次空库安装仍需独立验证。

测试业务数据、正式业务数据和数据库备份应分别管理。SQL 中的 USE crm 使用固定库名，切换测试数据库时需同步核对。

### 2. 配置后端连接

配置文件：[backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)。支持通过环境变量覆盖连接信息。

Windows PowerShell 示例，在启动后端的同一终端执行：

~~~powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/crm?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "你的数据库用户"
$env:DB_PASSWORD = "你的数据库密码"
~~~

生产配置放在服务器上单独管理。仓库中的开发默认值应按实际环境覆盖。

本机如保留 backend/.mvn/maven.config，需确认其中 Maven 仓库路径适用于当前电脑。该文件已被忽略，新克隆环境可使用 Maven 默认仓库，或自行配置本机仓库。

### 3. 启动后端

在项目根目录打开终端：

~~~bash
cd backend
mvn spring-boot:run
~~~

默认端口为 8080。也可以在 IDEA 中导入 backend/pom.xml，使用 JDK 21 运行 com.crm.CrmApplication。

### 4. 首次创建管理员

仅适用于数据库基础组织、角色和权限已就绪，但管理员账号尚未创建的环境。

在 PowerShell 设置独立的管理员初始密码，再在 backend 目录启动一次：

~~~powershell
$env:CRM_ADMIN_PASSWORD = "自行设置的管理员初始密码"
mvn spring-boot:run "-Dspring-boot.run.arguments=--crm.bootstrap.enabled=true"
~~~

管理员员工编号为 ADMIN001。初始化只创建缺失账号，已有账号的密码和角色不会被覆盖。创建成功后停止这次启动，再使用普通启动命令；日常运行保持 crm.bootstrap.enabled=false。

### 5. 启动前端

在项目根目录另开终端：

~~~bash
cd frontend
npm ci
npm run dev
~~~

访问 **http://127.0.0.1:5173/login**。开发环境通过 Vite 将 /api 请求代理到 http://127.0.0.1:8080。

前端接口基础路径默认是 /api，需要自定义时使用 VITE_API_BASE。Vite 环境变量在构建时生效，前端环境文件不用于保存服务端秘密。

登录页的快捷登录组件仅在开发模式加载，需要对应测试账号已经存在且密码匹配。npm run build 的正式构建不包含此组件；正式环境使用员工编号和密码登录。

## 认证与权限

- 账号使用员工编号，姓名允许重复。
- 员工关联多个角色，角色关联目录、菜单和操作权限。
- 前端按权限生成菜单、注册页面及展示操作入口。
- 后端通过 Spring Security 校验操作权限，再按本人、部门、部门及下级或全部等范围限制业务数据。
- 数据范围按授予该操作的角色合并；生产和仓储的执行记录还受负责人关系限制。
- 密码使用 BCrypt 哈希；登录 Token 随机生成，数据库保存摘要，默认有效期为 8 小时。
- 授权变更在后续请求中重新校验；停用、离职或改密等操作会按业务规则撤销会话。

常用认证接口：

| 方法 | 路径 | 作用 |
|---|---|---|
| POST | /api/auth/login | 员工编号与密码登录 |
| GET | /api/auth/profile | 当前员工及有效权限 |
| GET | /api/auth/menus | 当前员工菜单树 |
| POST | /api/auth/logout | 退出当前会话 |

登录请求体包含 employeeNo 和 password；成功后读取 data.token。后续请求携带：

~~~http
Authorization: Bearer <token>
~~~

未登录或会话失效返回 401，无操作权限返回 403。未登录访问受保护路径时，401 本身不能证明请求路径正确；应进一步验证登录及携带 Token 的接口调用。

## 构建与测试

后端在 backend 目录执行：

~~~bash
mvn test
mvn package -DskipTests
~~~

生成 backend/target/crm-server-0.0.1-SNAPSHOT.jar。-DskipTests 仅用于已完成测试后的单独打包；首次验证应先运行测试。

前端在 frontend 目录执行：

~~~bash
npm ci
npm run build
~~~

生成 frontend/dist/。

后端测试通过 MockMvc 和真实 MyBatis SQL 验证功能，需连接匹配版本的 MySQL，并具备基础角色、部门、产品及属性等测试前置数据。写测试使用事务回滚，自增序号仍可能增长；建议在独立测试库运行。

**最近一次完整验收记录为 2026-11-17**：两轮各 167 项后端测试通过，前端构建通过，并检查了 24 个菜单页面和多岗位接口。该结果是当次环境的验证快照，不代表每次克隆后无需准备数据库即可通过，也不替代并发、浏览器兼容及生产部署专项验收。

测试辅助脚本：

| 文件 | 用途 |
|---|---|
| database/crm-test-products.sql | 空产品表的一次性测试基础数据，包含固定 ID |
| scripts/seed-test-data.py | 通过真实接口创建场景，依赖匹配的测试账号与产品 |
| scripts/verify-test-data.py | 对已创建场景执行读取、筛选、权限和状态检查 |

这些脚本包含测试环境假设和示例凭据，需要先阅读并配置；场景清单放在本地 .tmp，不随仓库上传。脚本不是正式环境初始化入口，不应直接对已有正式数据执行。

## Ubuntu 部署概要

支持 Ubuntu 22.04 下直接运行 JAR，由 systemd 托管。部署时准备 Java 21、MySQL，以及用于前端静态资源和接口转发的 Nginx。

1. 在开发环境完成测试，打包后端 JAR 和前端 dist。
2. 准备匹配的数据库，配置专用数据库账号及服务器环境变量。
3. 通过 systemd 启动后端，配置开机启动、异常重启和日志。
4. 将 dist 内的 index.html、assets 等文件上传到前端部署目录。
5. Nginx 提供页面，将 /api/ 转发至本机后端；正式对外访问配置 HTTPS。

Nginx 示例，假设前端部署于 /opt/crm/frontend，后端通过 SERVER_PORT=8000、SERVER_ADDRESS=127.0.0.1 启动：

~~~nginx
server {
    listen 80;
    server_name example.com;

    root /opt/crm/frontend;
    index index.html;
    client_max_body_size 6m;

    location /api/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
~~~

域名、路径和端口按实际环境修改。root 必须直接对应 index.html 所在目录；proxy_pass 末尾不加 /，保留后端需要的 /api/ 前缀。try_files 支持 Vue 页面刷新和深层路由。

启动后应验证静态资源、登录以及携带 Token 的业务接口，而不只检查首页 HTML 是否返回 200。当前售后图片保存在数据库中，备份时需包含相应数据。

## 版本管理与使用说明

- .gitignore 排除依赖、构建产物、临时目录、日志、部分备份及本地环境配置，保留源码、锁文件和业务 SQL。
- 当前忽略规则还排除了 backend 下的 Markdown，因此本说明不依赖这些本地开发文档。
- 忽略规则不会隐藏源码中的字符串，也不会自动移除已被 Git 跟踪的文件；提交前应检查暂存内容。
- 示例账号、初始化代码及脚本中的测试凭据应与正式环境有效密码分离。真实密码、私钥、Token 和业务库备份不提交到仓库。
- 已完成主要业务闭环，后续重点包括统一数据库安装升级、备份恢复演练、登录失败限制、依赖更新及性能验证。
- 当前仓库未声明开源许可证。
