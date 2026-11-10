-- 由 scripts/build-agent-knowledge.py 生成；仅导入articles白名单。

-- 已运行迁移不可重写；规则变更后应使用后续版本迁移。

INSERT INTO agent_knowledge_document(document_id, version, title, reviewed_at, source_path, content_hash, status)
VALUES('KB-PURCHASE', 1, '选票与购买条件', '2026-10-27', 'docs/knowledge-base/articles/01-purchase.md', '08dea770769479dc7f43124fbacc4d8d12c7063cc4a71dda7b8a763954720776', 'ACTIVE');

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PUR-001', 'KB-PURCHASE', 1, '演出、场次、票档分别是什么？', '怎么选票；场次和票档有什么区别；为什么要选时间。', '演出是你想看的活动；场次是该演出的具体时间安排；票档是某个场次下不同的票种或价格档位。购票时先确认演出，再选场次、票档和数量，最后在确认页核对信息并提交申请。看到某个演出的名称或起价，不代表每个场次、每个票档都能按同一价格购买。', '具体场次、票档是否启用及当前价格，必须读取演出详情与票档信息。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PUR-002', 'KB-PURCHASE', 1, '一次最多买几张？能连续下单凑更多张吗？', '限购几张；能不能买六张；再买一单；重复购买提示。', '当前系统单次购票数量为1至5张。同时，同一账号对同一票档存在有效申请或订单时，会受到重复购买限制，不能简单理解为每次买5张就能无限次叠加购买。遇到已有申请或订单的提示，应先查看原申请与本人订单，确认其状态后再操作。', '是否存在有效占用、原申请是否已释放以及当前是否可再次购买，需要业务查询，不能仅凭这条说明判断。不要承诺取消后必定立即可以买到。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PUR-003', 'KB-PURCHASE', 1, '资格检查通过，是不是已经抢到票了？', '有资格是不是就成功；检查可以购买但提交失败；资格通过能锁票吗。', '不是。资格检查只反映查询当时的数量、售票状态、售票时间、库存和已有购买占用等条件，不会替你预留库存，也不会创建订单。检查通过后，库存仍可能被其他申请占用；只有提交申请并查询其后续结果，才能确认是否已受理和是否建单。', '当前资格、库存、申请和订单状态分别查询；不能用之前的一次检查结果保证购买成功。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PUR-004', 'KB-PURCHASE', 1, '页面看见有票，为什么提交时提示库存不足或暂不可售？', '明明有库存；刚才还能买；有票却抢不到；库存正在恢复。', '页面展示和实际提交不是同一个时刻。其他用户可能先完成申请，演出、场次或票档也可能被下架或已结束售票。库存服务恢复期间，系统还可能暂时拒绝受理，避免按不可靠的数据继续售票。请刷新演出页核对信息；如果已经提交过申请，应先查询原申请，不要只因页面报错就反复创建新申请。', '库存不足、不可售、服务异常不是同一原因，应依据实际返回说明和查询结果回答；查询失败不能说成已经售罄。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PUR-005', 'KB-PURCHASE', 1, '这个项目支持选座、改签或转赠吗？', '能选连座吗；座位在哪里选；换场次；转让门票；电子票怎么入场。', '当前购票确认页明确不支持选座，现有用户流程是选择场次、票档和数量。当前项目也没有可供用户操作的改签、转赠或入场核销流程。不能据此承诺自动连座、具体座位、改签条件或转赠成功；如后续新增这些功能，应以新增的页面和规则为准。', '具体演出的线下入场、实名核验等安排不属于当前系统已经定义的规则，不能借用其他平台政策补全。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PUR-006', 'KB-PURCHASE', 1, '展示的演出、日期和价格，是官方售票承诺吗？', '这里能买到真实演出票吗；演出时间准不准；是不是官方票务；沙箱票。', '本项目用于学习和本地演示，展示排期、票价和库存不代表官方售票信息。浏览到一条演出或成功走完演示订单流程，不能证明你获得了真实演出的入场资格。不要将项目中的演示记录或测试付款当作正式票务凭证。', '当前运行环境及支付是否为沙箱，需要结合实际页面和部署说明确认；不能根据某条历史验收记录推断当前连接的渠道。', 1);

INSERT INTO agent_knowledge_document(document_id, version, title, reviewed_at, source_path, content_hash, status)
VALUES('KB-ORDERS', 2, '申请与订单', '2026-10-27', 'docs/knowledge-base/articles/02-orders.md', '2acceff5f2ca97f35d4e87c336d0c0007076e2cfd5ae006ed479e3495c26195f', 'ACTIVE');

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-001', 'KB-ORDERS', 2, '显示申请已受理，为什么还没有订单？', '受理了却没出票；订单创建中；抢票成功但订单列表没有。', '“申请已受理”表示系统已经接收并记录本次购票申请，不表示订单已经创建，也不表示已经付款。当前系统会继续异步处理建单；建单前可能已经返回申请编号或订单号，但订单ID还没有生成。请在购票结果页继续查询，确认建单成功后再进入支付。不要仅凭“已受理”判断已出票。', '查询原申请，确认是否已经有订单ID及对应订单。文档不能保证具体等待秒数。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-002', 'KB-ORDERS', 2, '提交超时、网络断开或显示结果未知，我该重新下单吗？', 'UNKNOWN是什么意思；点提交没反应；请求超时是不是失败；断网后再抢。', '结果未知不等于失败。原申请可能已经被系统接收，只是响应没有回到页面。请优先在购票结果页查询原申请，或从确认页的“查看购票结果”入口恢复。不要在结果尚未确认时不断创建新的购买意图，以免把一次购买误当成多次操作。', '需要查询原申请才能判断已受理、被拒绝、已释放或仍未确认；不能只根据网络错误推断没有订单。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-003', 'KB-ORDERS', 2, '刷新或离开页面后，原购票申请还能找回来吗？', '关掉结果页了；刷新后订单没了；恢复原申请；换浏览器找订单。', '当前前端会按账号保留原购买意图，重新进入相关页面时可以继续查看原申请结果；已经建单的订单也可在本人订单页查询。请使用提交申请时的账号。清理浏览器存储、换浏览器或设备后，未建单申请的本地恢复信息可能不可用，不能承诺自动找回所有未建单申请；这也不等于服务端记录已被删除。', '是否仍有本地购买意图、申请是否建单、订单是否属于当前账号，分别核验。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-004', 'KB-ORDERS', 2, '付款期限是多久？刷新或重新打开支付页会延长吗？', '为什么没有完整三十分钟；重新付款能续时间吗；倒计时重新算；订单多久关闭。', '当前实现以首次库存预扣时间起算30分钟作为付款截止时间，不是从订单出现在页面或打开支付页时才开始。异步建单等待也会占用这段时间。刷新、查询、重新打开页面或复用原申请不会重新赠送30分钟。请以服务端返回的本笔订单截止时间为准。', '必须查询这笔申请或订单的实际截止时间。页面倒计时结束不等于取消已处理完成，取消状态仍需查询。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-005', 'KB-ORDERS', 2, '为什么订单金额和演出页的“起价”不同？付款金额以哪个为准？', '起价不是这个钱；总价怎么算；买多张怎么算；支付金额能改吗。', '演出起价不等于你所选场次、票档和数量对应的总金额。系统受理时记录所选票档的单价及数量，订单金额由该单价乘以数量计算；支付时读取这笔订单的金额，不能由用户或客服任意修改。确认页的“预计合计”不能代替已经创建订单的实际金额。', '查询订单金额及所选票档和数量。这里没有定义优惠券、折扣叠加、配送费或退改手续费，不能自行添加这些费用或优惠规则。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-006', 'KB-ORDERS', 2, '订单待支付、已支付、取消确认中、已取消分别说明什么？', '订单状态怎么理解；取消中算成功吗；已完成是不是付款成功。', '待支付表示订单等待付款；已支付表示系统已按支付确认流程更新订单；取消确认中表示取消和渠道确认尚未结束；已取消表示取消流程已经完成。系统还定义了“已完成”状态，但不能把该名称自行解释为已入场、已核销或演出已结束。订单、支付流水和退款记录是不同记录，不能将它们的状态数字互相套用。', '说明某一笔订单当前结果前，必须查询本人订单；判断付款或退款还需查看对应记录。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-007', 'KB-ORDERS', 2, '申请编号、订单号和支付流水号是同一个吗？', '申请ID；订单ID；支付单号；付款流水；拿申请号去付款。', '不是。申请编号用于查询这次购票处理；订单号和订单ID用于定位已经创建的订单；支付流水号用于核对关联付款。申请受理时可能已有订单号，但尚未生成订单ID，不能拿申请编号直接当作可支付订单。请从原购票结果进入已经创建的订单，再进入对应收银台，不要混用不同编号。', '编号对应的记录、当前归属及关联关系必须通过服务端查询。知道某个编号不代表获得访问别人记录的权限。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ORD-008', 'KB-ORDERS', 2, '为什么用另一个账号登录后看不到原来的订单？', '换号订单没了；查朋友订单；知道订单号能查吗；我的订单为空。', '订单和购票申请按提交时的账号隔离，当前账号只能查询自己的记录。请先确认是否使用了原购买账号；知道别人的订单号或申请编号，也不能获得访问权限。当前列表为空还可能与分页或筛选有关，不能直接推断订单已被删除。', '当前账号、订单归属、筛选与分页结果需要实际查询；客服不能跨账号披露订单信息。', 1);

INSERT INTO agent_knowledge_document(document_id, version, title, reviewed_at, source_path, content_hash, status)
VALUES('KB-PAYMENTS', 1, '支付与确认', '2026-10-27', 'docs/knowledge-base/articles/03-payments.md', '074ae9cd240e054df62a902ef553e21d919aee744585aeb77ed5629824f13e54', 'ACTIVE');

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PAY-001', 'KB-PAYMENTS', 1, '目前支持哪些支付方式？可以让客服帮我付款吗？', '微信支付；银行卡付款；让AI直接支付；扫码支付；支付宝收银台。', '当前后端支付创建流程只接受支付宝支付，页面会引导你前往相应支付页面。智能客服目前没有付款工具，也不会代替你确认付款。是否连接支付宝沙箱或其他实际运行环境，须以当前部署和页面说明为准，不能根据系统存在支付接口就认定支持真实资金交易。', '支付入口是否可用、订单是否可支付及当前渠道环境，需要实际页面或服务端结果确认。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PAY-002', 'KB-PAYMENTS', 1, '点了“前往支付宝支付”或生成了支付页面，是不是已经付款？', '打开付款页算付款吗；生成二维码就成功了吗；付款窗口打开了。', '不是。生成或打开支付页面，只表示准备进入支付渠道，不代表渠道已经收款，更不代表系统已经确认订单已支付。完成渠道上的付款步骤后，还要查看本站服务端确认的支付和订单状态。', '查询当前用户的支付流水和对应订单。不要仅依据按钮点击、页面跳转、图片或表单生成推断付款成功。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PAY-003', 'KB-PAYMENTS', 1, '支付宝已经显示成功，订单为什么还没更新？', '付过钱还是待支付；付款后没出票；扣款了没订单；支付结果延迟。', '渠道显示结果与本站完成确认不是同一个步骤。系统需要核对这笔付款与订单的对应关系、金额及渠道信息，再更新本地记录。请在收银台或付款返回页查询结果，再查看对应订单；暂未更新时不要急着再次付款。若查询持续失败，应保留订单号和支付流水号以便核查，不需要提供密码或验证码。', '付款是否确实成功、订单是否能接收这笔付款以及是否进入退款处理，必须分别查询。没有查询结果时不能承诺“已经支付成功”或“会在几秒内到账”。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PAY-004', 'KB-PAYMENTS', 1, '返回链接写着成功，或者没有自动跳回，我该相信哪个结果？', '支付回跳成功；修改URL状态；关闭支付宝窗口；没跳回是不是没付款。', '以服务端核验后的支付记录和订单状态为准。返回页面上的URL参数、浏览器跳转是否成功、支付窗口是否关闭，都不能单独证明付款结果。没有自动返回时，可以回到本站的收银台或本人订单页面查询，不必为了触发跳转再支付一次。', '查询本人支付和订单状态；返回页面本身不能替代查单。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PAY-005', 'KB-PAYMENTS', 1, '连续点击支付，会生成多笔付款吗？', '重复点击付款；支付页面打不开再点；两次付款；重复扣款。', '当前系统在为同一订单创建支付时会尝试复用尚未结束的支付流水，避免重复创建；但这不等于可以保证任何情况下都绝不会出现多笔渠道付款。结果不明时应先查询原支付和订单，不要用反复付款来确认是否成功。实际发生迟到或重复收款时，系统会按核验结果处理，可能产生退款记录。', '是否真的收款、收款几笔、哪笔入账以及退款进度，都要查实际记录，不能仅凭重复点击下结论。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-PAY-006', 'KB-PAYMENTS', 1, '为什么订单过期后不能继续支付？还能通过旧付款页面付款吗？', '订单过期怎么付；旧支付窗口；超时后继续付款；重新打开付款。', '创建支付时，系统会检查订单是否属于当前用户、是否仍为待支付及是否在付款期限内。订单已过期或已进入其他状态时，不能按原待支付订单继续创建支付。不要继续使用过期的旧付款页面；若此前已付款或渠道结果不明，应先查询原支付和订单。实际出现迟到付款时，需根据订单和库存状态核验处理，并不保证恢复原订单。', '实际截止、订单状态、渠道付款与退款记录；文档不能替你判断这笔订单是否还能付款。', 1);

INSERT INTO agent_knowledge_document(document_id, version, title, reviewed_at, source_path, content_hash, status)
VALUES('KB-CANCELLATION-REFUNDS', 2, '取消与退款', '2026-10-27', 'docs/knowledge-base/articles/04-cancellation-refunds.md', 'b80e9c4b8d1433bf4de74625bfb2ee430bdb5931a2ef4f520c80997a059998b7', 'ACTIVE');

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-001', 'KB-CANCELLATION-REFUNDS', 2, '哪些订单可以取消？已支付的订单也能直接取消吗？', '怎么取消订单；付过钱能取消吗；演出不想看了能退吗。', '当前用户取消流程面向本人待支付订单；已经进入取消确认中的订单可以继续按该流程确认结果。已支付、已完成或已取消的订单，不能通过这个入口重新取消。当前没有向用户开放已支付订单的主动退票申请规则，不能把“取消待支付订单”解释为“付款后可无条件退票”。', '先查询本人订单状态。是否可以取消，最终以服务端对本笔订单的校验为准。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-002', 'KB-CANCELLATION-REFUNDS', 2, '点了取消，为什么还显示取消确认中？', '取消中一直不变；取消失败但状态变了；取消后库存没回来。', '取消并不只是把页面状态改成“已取消”。系统还要确认关联支付没有形成有效收款，或确认渠道已经关闭。渠道暂时无法确认时，订单会保留在取消确认中，避免一边释放库存、一边又收到合法付款。请重新查询这笔订单，只有显示已取消时才能认定取消流程完成。', '查询订单和关联支付；一次取消请求提示失败或等待，不足以证明订单仍待支付或已取消。不能承诺固定等待时长。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-003', 'KB-CANCELLATION-REFUNDS', 2, '取消后，为什么页面库存没有立刻增加？我能马上重新购买吗？', '取消不回库存；取消后还限购；退回的票什么时候放出；重新买原票档。', '取消确认中尚不能按已取消处理；确认取消后，库存释放和页面展示还可能存在同步时间差。即使自己的占用已经释放，其他用户也可能先占用剩余票，因此不能保证你立刻能再次买到。请确认原订单已取消，再刷新演出信息并重新检查购买条件。', '原订单是否已取消、购买占用是否解除、当前实时库存与资格均需查询。不要根据“我取消了几张”推算当前一定还有几张。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-004', 'KB-CANCELLATION-REFUNDS', 2, '订单超时或正在取消时付款了，会怎样？', '超时后扣款；取消时刚好支付；订单取消却收到付款成功。', '系统会先核对实际付款，再判断订单是否仍能接收这笔付款。订单仍持有有效库存时，符合条件的付款可能被正常确认；若库存已释放或付款无法再归入原订单，系统会按异常收款流程生成退款请求。不能保证迟到付款一定恢复订单，也不能仅凭超时就断言已经退款。', '需要核对订单、支付及退款记录。不要重新付款来修复这类状态，也不要将一个页面结果当作所有记录的最终结果。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-005', 'KB-CANCELLATION-REFUNDS', 2, '为什么有退款记录，却找不到“申请退款”按钮？', '怎么申请退票；退款入口在哪；退款列表为空；AI帮我退款。', '当前退款页面是本人退款记录的查询入口，不是用户主动申请退款入口。现有退款处理主要针对迟到或重复付款等系统核实的异常收款情形。退款列表为空，只表示当前查询范围没有相应记录，不能据此判断某笔付款是否成功或是否应该退款；智能客服也没有代为提交退款的工具。', '当前接口只返回本人最近100条退款记录，页面筛选也不能代替全部历史查询。某笔交易需要核查时，应核对对应订单和支付记录，而不是补造用户退票政策。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-006', 'KB-CANCELLATION-REFUNDS', 2, '退款“结果确认中”“待人工”“成功”分别是什么意思？', '退款UNKNOWN；退款处理中；MANUAL_REQUIRED；退款成功但没看到钱。', '待退款表示请求已记录；处理中表示系统正在执行；结果确认中表示渠道结果尚未确认，不能当作退款成功；等待重试表示后续还需处理；待人工表示需要维护人员核查；成功表示系统已记录渠道确认的退款成功结果。页面出现不认识的新状态时，应说明状态待确认，不擅自解释成成功或失败。', '查询这笔退款的当前状态及关联付款。退款记录状态与用户在外部账户中看到余额变化不是同一份证据；当前没有一键人工转接入口，不能承诺已替用户转接。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-007', 'KB-CANCELLATION-REFUNDS', 2, '退款多久能到账？会收退票手续费吗？', '退款几天到；百分之几手续费；七天无理由退票；能全额退吗。', '当前项目没有定义面向用户的普通退票手续费、按演出时间分档的退票政策，也没有给出外部账户的固定到账天数。系统异常收款退款按已核实的付款记录处理，不能把它扩展成所有订单都享有无条件退票。客服应查询实际退款进度，不承诺当天、若干小时或若干工作日一定到账，也不套用其他平台收费规则。', '具体付款、退款金额及渠道结果必须查询记录；没有明确规则和记录时，应说明当前无法确认。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-REF-008', 'KB-CANCELLATION-REFUNDS', 2, '没有付款的订单取消后，为什么没有退款记录？', '取消后没退款；未支付订单退款；取消成功为什么退款列表为空。', '取消订单与退回已收款是两件事。尚未形成收款的待支付订单，取消主要是结束订单并释放对应购买占用，不会仅因点击取消就产生一笔退款。若实际上存在迟到或重复收款，则需核对付款和系统退款处理，不能只依据原先的待支付展示判断从未收款。', '需要确认订单、关联支付是否实际收款及是否存在退款记录；空退款列表本身不能证明款项结果。', 1);

INSERT INTO agent_knowledge_document(document_id, version, title, reviewed_at, source_path, content_hash, status)
VALUES('KB-ACCOUNT-SUPPORT', 1, '账户与智能客服', '2026-10-27', 'docs/knowledge-base/articles/05-account-support.md', '6aa7c534b3bf163b5d2562be394311bdf0ef361b4f58dc6fbda871a5b9be87ca', 'ACTIVE');

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-001', 'KB-ACCOUNT-SUPPORT', 1, '用户端怎么登录？未注册邮箱怎么办？', '手机号登录；在哪里注册；邮箱验证码；登录邮箱修改。', '当前用户端使用邮箱和密码登录，未注册邮箱会进入邮箱验证码验证流程。填写自己可使用的邮箱，并按页面完成验证；具体验证码有效期和是否允许重发以服务端结果为准，页面冷却倒计时不等于验证码有效期。个人资料中的登录邮箱目前是只读信息，没有用户端自助换绑邮箱入口。', '邮箱是否已注册、验证码是否有效、账号是否可用以及邮件是否实际收到，都不能由知识文档判断。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-002', 'KB-ACCOUNT-SUPPORT', 1, '下单前必须先填真实姓名、证件和收货地址吗？', '没实名能下单吗；必须上传身份证吗；为什么客服问个人信息；要先填地址吗。', '当前项目的用户购票确认流程没有实现“必须先完成实名、证件上传和收货地址才能提交”的统一门槛；不能仅因系统有资料或地址页面，就说这些信息已经是必填购票条件。也不能据此推断真实演出的入场实名要求。智能客服不应索取密码、验证码或完整证件号码；如需维护资料，请到个人中心操作。', '以当前业务页面和服务端校验为准。具体演出的实名入场或证件政策，当前知识中没有依据时应说明未知。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-003', 'KB-ACCOUNT-SUPPORT', 1, '怎么修改密码？可以把验证码或密码发给客服处理吗？', '让客服改密码；忘记密码；找回密码；修改登录密码。', '当前账户安全页的修改密码流程需要验证当前密码，再设置6至20位新密码。不要将当前密码、新密码、验证码或登录凭据发给客服。现有修改密码功能不等于已经实现了忘记密码找回或重置流程；无法提供当前密码时，不能编造一个不存在的找回入口。', '修改是否成功以页面反馈及后续登录结果为准，不让模型宣称已经修改，也不承诺修改密码会自动退出全部设备。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-004', 'KB-ACCOUNT-SUPPORT', 1, '为什么默认地址不能删除？怎么换默认地址？', '地址删不掉；删除默认地址；第一个地址自动默认；修改默认收货地址。', '当前系统不允许直接删除默认收货地址。需要更换时，可先新增或选择另一个本人地址并设置为默认，再删除原来的非默认地址。首个地址会被设为默认；编辑地址信息不会因为没有重新提交默认标记，就自动取消其默认身份。当前不应承诺可以直接删除最后一个默认地址。', '实际地址列表、当前默认地址和每次保存/删除结果，需要查询本人地址及页面反馈；客服目前只能查询经过裁剪的地址信息，不能代为修改。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-005', 'KB-ACCOUNT-SUPPORT', 1, '智能客服现在能做什么？可以代下单、取消或退款吗？', 'AI帮我买票；帮我取消订单；替我申请退款；修改收货地址。', '当前客服可以查询演出、场次、票档、实时库存、本人订单、本人购票申请及购买资格，也能提供购票指引。它没有代下单、付款、取消订单、申请退款、修改资料或地址的执行工具。相关操作仍需你在业务页面核对并确认。知道一条操作说明，不代表客服已经执行过该操作。', '个人订单、库存、资格、付款或退款都要实际查询；某类查询未提供给当前客服时，应引导到相应页面，不能假称已查到。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-006', 'KB-ACCOUNT-SUPPORT', 1, '刷新后还能看到客服历史吗？离开页面会停止查询吗？', '客服记录丢了；切换会话；刷新后继续；后台查询；AI忘了之前说的话。', '已启用新版客服时，会话与消息保存在当前账号下，可切换会话、读取历史并在刷新后恢复。模型每次使用最近20条成功消息作为上下文，这不等于只能保存20条历史，也不保证它记住所有旧问题。离开或切换页面只会断开浏览器连接，不代表已受理的后台查询停止；回到该会话可继续查看原运行结果。', '当前会话、历史、后台运行及是否超时，必须查询服务端。查询断开不等于模型一定停止，也不等于新的消息已经成功执行。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-007', 'KB-ACCOUNT-SUPPORT', 1, '清空或删除客服会话，会把订单、购票申请一起取消吗？', '清空聊天等于取消订单吗；删会话会退款吗；对话重新开始；删除历史。', '不会。清空会话针对这段客服历史和模型上下文，删除会话针对客服会话本身，都不是交易取消或退款操作。当前运行尚未结束时，系统会拒绝清空或删除，避免影响结果确认；清空操作本身不调用模型。需要取消订单时，请到本人订单页按实际可用入口处理。', '清空或删除是否成功、订单是否取消，需要分别核验，不能将一个操作结果套到另一个对象上。', 1);

INSERT INTO agent_knowledge_chunk(entry_id, document_id, document_version, question, aliases, answer, live_check, enabled)
VALUES('KB-ACC-008', 'KB-ACCOUNT-SUPPORT', 1, '客服显示维护中、查询超时或达到工具上限，是什么意思？', 'AI打不开；功能维护中；一直正在查询；没有回复；工具调用上限；恢复原请求。', '维护中表示当前环境没有开放客服聊天，不能据此判断购票和订单功能也已关闭。已开放的新版客服默认每次运行总时限60秒、最多8次工具调用，问题过大或服务异常时可能提前结束或失败；可以缩小问题范围。连接中断时，已知运行优先查询原结果，首事件前中断则按页面提供的“恢复原请求”操作，不应自动新建一遍咨询。', '当前开关、实际预算配置、运行是否成功及失败原因都以服务端为准。不能承诺客服何时恢复，也不能把超时当作“没有订单”或“库存售罄”。', 1);
