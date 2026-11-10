-- 由 scripts/build-agent-knowledge.py 生成；仅导入articles白名单。

-- 已运行迁移不可重写；规则变更后应使用后续版本迁移。

INSERT INTO agent_knowledge_document(document_id, version, title, reviewed_at, source_path, content_hash, status)
VALUES('KB-ACCOUNT-SUPPORT', 2, '账户与智能客服', '2026-10-28', 'docs/knowledge-base/articles/05-account-support.md', 'b72a99c206205526a8e00eb2ec0002a513aec4e5cb9d54f964d0f7817d988d7e', 'ACTIVE');

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-001', 'KB-ACCOUNT-SUPPORT', 2, '用户端怎么登录？未注册邮箱怎么办？', '手机号登录；在哪里注册；邮箱验证码；登录邮箱修改。', '当前用户端使用邮箱和密码登录，未注册邮箱会进入邮箱验证码验证流程。填写自己可使用的邮箱，并按页面完成验证；具体验证码有效期和是否允许重发以服务端结果为准，页面冷却倒计时不等于验证码有效期。个人资料中的登录邮箱目前是只读信息，没有用户端自助换绑邮箱入口。', '邮箱是否已注册、验证码是否有效、账号是否可用以及邮件是否实际收到，都不能由知识文档判断。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-002', 'KB-ACCOUNT-SUPPORT', 2, '下单前必须先填真实姓名、证件和收货地址吗？', '没实名能下单吗；必须上传身份证吗；为什么客服问个人信息；要先填地址吗。', '当前项目的用户购票确认流程没有实现“必须先完成实名、证件上传和收货地址才能提交”的统一门槛；不能仅因系统有资料或地址页面，就说这些信息已经是必填购票条件。也不能据此推断真实演出的入场实名要求。智能客服不应索取密码、验证码或完整证件号码；如需维护资料，请到个人中心操作。', '以当前业务页面和服务端校验为准。具体演出的实名入场或证件政策，当前知识中没有依据时应说明未知。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-003', 'KB-ACCOUNT-SUPPORT', 2, '怎么修改密码？可以把验证码或密码发给客服处理吗？', '让客服改密码；忘记密码；找回密码；修改登录密码。', '当前账户安全页的修改密码流程需要验证当前密码，再设置6至20位新密码。不要将当前密码、新密码、验证码或登录凭据发给客服。现有修改密码功能不等于已经实现了忘记密码找回或重置流程；无法提供当前密码时，不能编造一个不存在的找回入口。', '修改是否成功以页面反馈及后续登录结果为准，不让模型宣称已经修改，也不承诺修改密码会自动退出全部设备。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-004', 'KB-ACCOUNT-SUPPORT', 2, '为什么默认地址不能删除？怎么换默认地址？', '地址删不掉；删除默认地址；第一个地址自动默认；修改默认收货地址。', '当前系统不允许直接删除默认收货地址。需要更换时，可先新增或选择另一个本人地址并设置为默认，再删除原来的非默认地址。首个地址会被设为默认；编辑地址信息不会因为没有重新提交默认标记，就自动取消其默认身份。当前不应承诺可以直接删除最后一个默认地址。', '实际地址列表、当前默认地址和每次保存/删除结果，需要查询本人地址及页面反馈；客服目前只能查询经过裁剪的地址信息，不能代为修改。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-005', 'KB-ACCOUNT-SUPPORT', 2, '智能客服现在能做什么？可以代下单、取消或退款吗？', 'AI帮我买票；帮我取消订单；替我申请退款；修改收货地址。', '已启用阶段四客服交易功能的环境中，客服可以查询演出、场次、票档、实时库存、本人订单、本人申请及资格，也可以根据明确的票档和数量提出购票草稿，或根据本人待支付订单提出取消草稿。草稿不会预留库存或执行交易，必须由你核对服务端卡片并点击确认。聊天里说“确认”不等于点击卡片。付款仍在原订单支付页面进行；客服不能申请退款、修改资料或地址。原运行实例是否升级及开放，以页面和服务端状态为准。', '个人订单、库存、资格、付款或退款都要实际查询；某类查询未提供给当前客服时，应引导到相应页面，不能假称已查到。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-006', 'KB-ACCOUNT-SUPPORT', 2, '刷新后还能看到客服历史吗？离开页面会停止查询吗？', '客服记录丢了；切换会话；刷新后继续；后台查询；AI忘了之前说的话。', '已启用新版客服时，会话与消息保存在当前账号下，可切换会话、读取历史并在刷新后恢复。模型每次使用最近20条成功消息作为上下文，这不等于只能保存20条历史，也不保证它记住所有旧问题。离开或切换页面只会断开浏览器连接，不代表已受理的后台查询停止；回到该会话可继续查看原运行结果。', '当前会话、历史、后台运行及是否超时，必须查询服务端。查询断开不等于模型一定停止，也不等于新的消息已经成功执行。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-007', 'KB-ACCOUNT-SUPPORT', 2, '清空或删除客服会话，会把订单、购票申请一起取消吗？', '清空聊天等于取消订单吗；删会话会退款吗；对话重新开始；删除历史。', '不会。清空或删除会话针对客服历史和上下文，不是交易取消或退款。聊天运行尚未结束时系统拒绝清空或删除；运行结束后清空会使未确认卡片失效，但不能撤销已经点击确认的交易。已确认交易仍须通过原申请或订单核验；清空后不能靠新建会话重复提交未知结果的申请。清空操作本身不调用模型。取消订单可按当前实际可用的订单页或客服确认卡片入口处理。', '清空或删除是否成功、订单是否取消，需要分别核验，不能将一个操作结果套到另一个对象上。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-008', 'KB-ACCOUNT-SUPPORT', 2, '客服显示维护中、查询超时或达到工具上限，是什么意思？', 'AI打不开；功能维护中；一直正在查询；没有回复；工具调用上限；恢复原请求。', '维护中表示当前环境没有开放客服聊天，不能据此判断购票和订单功能也已关闭。已开放的新版客服默认每次运行总时限60秒、最多8次工具调用，问题过大或服务异常时可能提前结束或失败；可以缩小问题范围。连接中断时，已知运行优先查询原结果，首事件前中断则按页面提供的“恢复原请求”操作，不应自动新建一遍咨询。', '当前开关、实际预算配置、运行是否成功及失败原因都以服务端为准。不能承诺客服何时恢复，也不能把超时当作“没有订单”或“库存售罄”。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-009', 'KB-ACCOUNT-SUPPORT', 2, '客服给出的确认卡片会锁库存吗？过期还能确认吗？', 'AI卡片算抢到票吗；聊天说确认；卡片到期；先占着票；放弃卡片。', '确认卡片只是服务端生成的操作草稿，不锁库存、不保证购买资格，也不是订单。请核对演出、场次、票档、数量和金额后点击确认；聊天中回复“确认”不会执行。待确认卡片有效期为5分钟，来源回复必须成功结束；已失效、放弃或被替代的卡片不能继续执行，需要重新核对。即使确认成功受理，也要等申请实际生成订单后再付款。', '卡片是否可确认、实际截止时间、最新库存、资格及申请和订单状态，以服务端为准，浏览器倒计时不能代替核验。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-010', 'KB-ACCOUNT-SUPPORT', 2, '点击卡片后网络断开或结果未知，可以再买一遍吗？', '确认超时；按钮点了没反应；未知结果；刷新重新下单；换会话重试。', '先查询原卡片、原申请或本人订单，不要重新提交一笔。已确认购票使用固定请求标识，重复点击不会创建新的执行权；网络中断不证明原交易失败。原结果未解决时，同账号同目标不能通过新卡片或新会话换标识重试。结果查询只核对已有业务，不会再次发起交易。取消确认中也不等于已经取消，须查看订单最终状态。', '必须查询原申请及订单；查不到或查询失败不能直接当成未受理，不保证何时恢复或成功。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-011', 'KB-ACCOUNT-SUPPORT', 2, '确认时票价变化，客服会按新价格自动买票吗？', '卡片价格和实际价格不一致；涨价后自动提交；重新确认金额；旧卡片失效。', '不会按不同于确认卡片的价格静默提交。确认时会重新核对价格和主要演出信息，可靠受理事务也会比对即将固化的单价与确认单价。价格变化时拒绝旧卡片，需要新卡片重新核对并点击确认；已经按原价格受理的同一请求不会因后来改价而重新收费或生成另一笔申请。付款金额仍以实际订单为准。', '卡片、受理结果及订单金额须分别核验；不得只依据旧聊天文本推断当前票价或交易成功。', 1);
