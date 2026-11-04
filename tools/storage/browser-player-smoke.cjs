#!/usr/bin/env node
'use strict';
// Real disposable HTTP/HTTPS journey. The optional certificate pin trusts only
// the isolated launcher's exact test key; blanket TLS bypass is never enabled.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const {chromium} = require('playwright');
const {observeStorageAction} = require('./browser-action.cjs');
const [output, acknowledgement, storageRoot] = process.argv.slice(2);
const resumeAfterRoster = acknowledgement === '--resume-after-roster';
assert.ok(process.argv.length === 5 && (acknowledgement === '--fresh-root' || resumeAfterRoster) && path.isAbsolute(output || '') && path.isAbsolute(storageRoot || ''),
  'Usage: node browser-player-smoke.cjs /absolute/empty/output --fresh-root|--resume-after-roster /absolute/disposable/storage-root');
const HOST = 'http://127.0.0.1:8080', PLAYER = 'https://127.0.0.1:8443', TIMEOUT = 120000;
const PC = '霜林旅者', OTHER = '同桌伙伴', SECRET = '暗室守卫不可见', NICKNAME = '小林';
const report = {schema:1,result:'FAILED',checks:[],screenshots:[],layouts:[],pageErrors:[],cspFailures:[],playerCommandPosts:0,receiptQueries:0,tls:'standard trust'};
const json = value => JSON.stringify(value,null,2)+'\n';
let browser, host, mobile, desktop, outputReady=false, phase='fresh root';
const within = (parent,child) => { const value=path.relative(parent,child);return value==='' || value!=='..'&&!value.startsWith('..'+path.sep)&&!path.isAbsolute(value); };
async function main(){
  const root=await fs.realpath(storageRoot);
  assert.equal((await fs.lstat(root)).isSymbolicLink(),false);
  assert.ok(!within(path.resolve(__dirname,'../..'),root),'Use disposable storage outside the repository');
  assert.ok(!within(root,path.resolve(output))&&!within(path.resolve(output),root));
  assert.deepEqual((await fs.readdir(root)).sort(),['instance.lock','runs','saves']);
  if(!resumeAfterRoster)for(const dir of ['runs','saves'])assert.deepEqual(await fs.readdir(path.join(root,dir)),[],'Fixture must be fresh');
  await fs.mkdir(output,{recursive:true});assert.deepEqual(await fs.readdir(output),[]);outputReady=true;
  const launch={headless:true};
  if(process.env.DND_BROWSER_EXECUTABLE){assert.ok(path.isAbsolute(process.env.DND_BROWSER_EXECUTABLE));launch.executablePath=process.env.DND_BROWSER_EXECUTABLE;}
  if(process.env.DND_PLAYER_TEST_SPKI){assert.match(process.env.DND_PLAYER_TEST_SPKI,/^[A-Za-z0-9+/]{43}=$/);launch.args=['--ignore-certificate-errors-spki-list='+process.env.DND_PLAYER_TEST_SPKI];report.tls='isolated test certificate SPKI only';}
  browser=await chromium.launch(launch);report.browserVersion=browser.version();
  function observe(page){
    page.setDefaultTimeout(30000);page.setDefaultNavigationTimeout(TIMEOUT);
    page.on('pageerror',error=>report.pageErrors.push(error.message));
    page.on('console',entry=>{if(/Content Security Policy|violates.*directive|Refused to (?:load|execute|apply|connect)/i.test(entry.text()))report.cspFailures.push(entry.text());});
    page.on('request',request=>{if(request.url().startsWith(PLAYER)){assert.ok(!request.url().includes('invite='),'Invitation must not enter a request URL');if(request.url()===PLAYER+'/api/player/command'&&request.method()==='POST')report.playerCommandPosts++;if(request.url()===PLAYER+'/api/player/receipt')report.receiptQueries++;}});
    page.on('response',async response=>{if(response.url().startsWith(PLAYER+'/api/player/')){try{const body=await response.text();if(body.includes(SECRET))report.pageErrors.push('Secret NPC leaked into player response');}catch(_){/* The deliberate dropped response has no browser body. */}}});
  }
  async function newPage(viewport){const context=await browser.newContext({viewport,locale:'zh-CN'}),page=await context.newPage();observe(page);return{context,page};}
  async function hostAction(action,trigger){const result=await observeStorageAction(host,action,trigger,{},TIMEOUT);return result.body;}
  async function playerSetting(action,trigger){const response=host.waitForResponse(response=>response.url()===HOST+'/api/host/storage/players/'+action&&response.request().method()==='POST');await trigger();const result=await response,body=await result.json();assert.equal(result.status(),200,body.code);assert.equal(body.status,'OK');await host.locator('#host-players[aria-busy="false"]').waitFor();return body;}
  const readHost=()=>host.evaluate(async()=>{const response=await fetch('/api/host/storage/state',{cache:'no-store'});return response.json();});
  const readPlayers=()=>host.evaluate(async()=>{const response=await fetch('/api/host/storage/players/state',{cache:'no-store'});return response.json();});
  const readPlayer=page=>page.evaluate(async()=>{const response=await fetch('/api/player/state',{cache:'no-store'});return response.json();});
  async function pane(name){await host.locator(`[data-pane="${name}"]`).click();await host.locator(`[data-panel="${name}"]`).waitFor();}
  async function rule(selector,key){const value=await host.locator(selector).locator('option').evaluateAll((options,key)=>options.find(option=>option.value.endsWith('/'+key))?.value,key);assert.ok(value);await host.locator(selector).selectOption(value);}
  async function screenshot(page,name,width,height){await page.setViewportSize({width,height});const layout=await page.evaluate(()=>({width:innerWidth,document:document.documentElement.scrollWidth,body:document.body.scrollWidth}));report.layouts.push({...layout,name});assert.ok(layout.document<=width&&layout.body<=width,'Horizontal overflow at '+name);await page.screenshot({path:path.join(output,name),fullPage:true});report.screenshots.push(name);}
  const hostTab=await newPage({width:1440,height:1050});host=hostTab.page;
  phase='create campaign and exact roster';
  const initial=host.waitForResponse(response=>response.url()===HOST+'/api/host/storage/state');await host.goto(HOST+'/host/storage');const initialState=await(await initial).json();
  await host.locator('#storage-workspace:not([disabled])').waitFor();
  if(!resumeAfterRoster){
  assert.equal(initialState.storage.state,'IDLE');await host.locator('#storage-create [name="name"]').fill('玩家入席验证战役');
  for(const key of ['srd51-healing@1','srd51-runtime@1'])await host.locator(`#storage-modules input[value="${key}"]`).check();
  await hostAction('create',()=>host.locator('#storage-create button').click());
  for(const [name,kind]of [[PC,'PC'],[OTHER,'PC'],[SECRET,'NPC']]){await host.locator('#domain-create [name="name"]').fill(name);await host.locator('#domain-create [name="kind"]').selectOption(kind);await rule('#domain-create [name="classKey"]','class.fighter');await hostAction('domain',()=>host.locator('#domain-create button').click());}
  }else{
    assert.equal(initialState.storage.state,'ACTIVE');assert.equal(initialState.campaign.name,'玩家入席验证战役');assert.equal(initialState.campaign.revision,4);
    assert.deepEqual(initialState.campaign.domain.characters.map(card=>[card.name,card.kind]).sort(),[[PC,'PC'],[OTHER,'PC'],[SECRET,'NPC']].sort());
    assert.equal(initialState.campaign.domain.roots.length,4);assert.deepEqual(initialState.campaign.domain.participation.members,[]);assert.deepEqual(initialState.campaign.domain.combats,[]);
    const equipment=initialState.campaign.domain.characters.find(card=>card.name===PC).state.equipment;assert.equal(equipment.length,1);assert.equal(equipment[0].item.ruleKey,'item.dagger');assert.equal(equipment[0].location,'CARRIED');report.resumeFrom='exact roster checkpoint';
  }
  let current=await readHost();const pcId=current.campaign.domain.characters.find(card=>card.name===PC).id,otherId=current.campaign.domain.characters.find(card=>card.name===OTHER).id;
  if(!resumeAfterRoster){
  await pane('actions');await host.locator('#domain-equipment [name="characterId"]').selectOption(pcId);await host.locator('#domain-equipment [name="kind"]').selectOption('ITEM_ACQUIRED');await rule('#domain-equipment [name="item"]','item.dagger');await hostAction('domain',()=>host.locator('#domain-equipment button').click());
  report.checks.push('Fresh disposable campaign, two rule PCs and a hidden NPC created by real Host forms');
  }else report.checks.push('Exact existing roster/equipment checkpoint verified; completed preparation was not replayed');
  phase='two independent same-name applications';
  await host.locator('#host-players-content').waitFor();await playerSetting('invite',()=>host.locator('#host-players-invite').click());const invitationUrl=await host.locator('#host-player-link').getAttribute('href');assert.ok(invitationUrl.startsWith(PLAYER+'/player#invite='));
  assert.equal(await host.locator('#host-player-qr svg').count(),1,'Local QR must render');
  const qr=await host.evaluate(url=>({matrix:window.LocalInviteQr.matrix(url).map(row=>row.map(Boolean)),version:6,level:'L',mask:0}),invitationUrl);
  assert.equal(qr.matrix.length,41);assert.ok(qr.matrix.every(row=>row.length===41));
  const qrImage=path.join(output,'00-actual-invitation-qr.png');await host.locator('#host-player-qr').screenshot({path:qrImage});
  const decoderPython=process.env.DND_QR_DECODER_PYTHON || '/usr/bin/python3',decoderModules=process.env.DND_QR_DECODER_PATH || '';
  assert.ok(path.isAbsolute(decoderPython),'DND_QR_DECODER_PYTHON must be an absolute native Python path');
  if(decoderModules)assert.ok(path.isAbsolute(decoderModules),'DND_QR_DECODER_PATH must be absolute');
  const decoded=spawnSync(decoderPython,['-c',
    'import sys,json\nif sys.argv[1]: sys.path.insert(0,sys.argv[1])\nfrom PIL import Image\nimport zxingcpp\nresults=zxingcpp.read_barcodes(Image.open(sys.argv[2]))\nexpected=sys.stdin.read()\nprint(json.dumps({"equal":len(results)==1 and results[0].text==expected}))',
    decoderModules,qrImage],{cwd:output,input:invitationUrl,encoding:'utf8',timeout:30000});
  assert.equal(decoded.status,0,'Independent PNG QR decoding requires Pillow and zxing-cpp; set DND_QR_DECODER_PATH for task-local modules');
  const qrResult=JSON.parse(decoded.stdout);assert.equal(qrResult.equal,true,'Rendered invitation QR must independently decode to the exact invitation URL');
  report.qr={size:41,localSvg:true,independentDecode:true,exactInvitationMatch:qrResult.equal,decoder:'zxing-cpp'};report.screenshots.push('00-actual-invitation-qr.png');
  const mobileTab=await newPage({width:390,height:844}),desktopTab=await newPage({width:1280,height:900});mobile=mobileTab.page;desktop=desktopTab.page;
  for(const page of [mobile,desktop]){await page.goto(invitationUrl);await page.locator('#player-join').waitFor();assert.equal(new URL(page.url()).hash,'');assert.ok(await page.locator('#player-invitation').inputValue());await page.locator('#player-nickname').fill(NICKNAME);await page.locator('#player-join-form button').click();await page.locator('#player-waiting').waitFor();assert.equal(await page.locator('#player-cards').innerText(),'');}
  await host.getByRole('button',{name:'刷新席位',exact:true}).click();await host.locator('#host-player-applications .player-application').nth(1).waitFor();
  for(let index=0;index<2;index++)await playerSetting('approve',()=>host.locator('#host-player-applications').getByRole('button',{name:'核对并批准',exact:true}).first().click());
  await mobile.locator('#player-table').waitFor();await desktop.locator('#player-table').waitFor();const identityA=await readPlayer(mobile),identityB=await readPlayer(desktop);assert.notEqual(identityA.session.playerKey,identityB.session.playerKey);
  report.checks.push('One fragment invitation supported two same-name applications; pending had no cards; DM approved distinct members separately');
  phase='assignment, explicit takeover and finite exploration permission';
  await host.getByText('角色控制与探索许可',{exact:true}).click();
  for(const [member,character]of [[identityA.session.playerKey,pcId],[identityB.session.playerKey,otherId]]){await host.locator('#host-player-control-form [name="memberId"]').selectOption(member);await host.locator('#host-player-control-form [name="characterId"]').selectOption(character);await playerSetting('control',()=>host.locator('#host-player-control-form button').click());}
  await mobile.getByRole('heading',{name:PC,exact:true}).waitFor();assert.ok(!(await mobile.locator('#player-cards').innerText()).includes(OTHER));assert.ok(!(await mobile.locator('body').innerText()).includes(SECRET));
  await mobile.locator('#player-takeover').click();await mobile.locator('#player-status-title').filter({hasText:'你的席位已就绪'}).waitFor();
  await host.locator('#host-player-exploration-form [name="characterId"]').selectOption(pcId);await playerSetting('exploration',()=>host.locator('#host-player-exploration-form button').click());
  await mobile.locator('#player-actions button').first().waitFor();await mobile.locator('#player-actions button:not([disabled])').first().waitFor();
  report.checks.push('Explicit PC assignment, own-only card projection and explicit single-page write takeover');
  phase='dropped command response and exact receipt';
  const before=await readHost(),cdp=await mobileTab.context.newCDPSession(mobile);let responseDropped=false;
  await cdp.send('Fetch.enable',{patterns:[{urlPattern:PLAYER+'/api/player/command',requestStage:'Response'}]});
  cdp.on('Fetch.requestPaused',async event=>{try{if(!responseDropped){responseDropped=true;await cdp.send('Fetch.failRequest',{requestId:event.requestId,errorReason:'Failed'});await cdp.send('Fetch.disable');}else await cdp.send('Fetch.continueRequest',{requestId:event.requestId});}catch(failure){report.pageErrors.push(failure.message);}});
  await mobile.locator('#player-actions button:not([disabled])').first().click();
  await mobile.locator('#player-receipt:not([hidden])').waitFor();assert.equal(responseDropped,true);assert.equal(report.playerCommandPosts,1);assert.ok(report.receiptQueries>=1);
  const after=await readHost();assert.equal(after.campaign.domain.roots.length,before.campaign.domain.roots.length+1);assert.equal(after.campaign.revision,before.campaign.revision+1);
  assert.equal(await mobile.evaluate(()=>sessionStorage.getItem('dnd.player.pending')),null);await cdp.detach();
  report.checks.push('Actual command response dropped after HTTPS delivery; full resync plus exact accepted receipt unlocked once, with one POST and one root');
  phase='typed adjudication and narrative distinction';
  await mobile.locator('#player-proposal-text').fill('我查看石门上的刻痕，寻找机关的线索。');await mobile.locator('#player-proposal-form button').click();await mobile.locator('#player-proposals .proposal-item').waitFor();
  await host.locator('#host-players-refresh').click();await host.locator('#host-player-proposals .player-proposal').waitFor();
  const proposal=host.locator('#host-player-proposals .player-proposal').first();await proposal.locator('[name="ability"]').selectOption('ability.wisdom');await proposal.locator('[name="dc"]').fill('12');await proposal.locator('[name="reason"]').fill('根据当前石门刻痕进行一次感知属性检定。');
  await playerSetting('proposal-execute',()=>proposal.getByRole('button',{name:'裁定并执行检定',exact:true}).click());
  await mobile.locator('#player-proposals').getByText('裁定已执行',{exact:true}).waitFor();assert.match(await mobile.locator('#player-proposals').innerText(),/业务结果：.*原骰.*总计/);assert.ok(!(await mobile.locator('#player-proposals').innerText()).includes('DC'));
  report.checks.push('A proposal consumed no game action until explicit bounded DM adjudication; player saw separate proposal and original die/result without secret DC');
  const beforeNarrative=await readHost();await mobile.locator('#player-proposal-text').fill('我提醒伙伴暂时不要触碰石门，等待大家靠近。');await mobile.locator('#player-proposal-form button').click();
  await host.locator('#host-players-refresh').click();await host.locator('#host-player-proposals .player-proposal').waitFor();const narrative=host.locator('#host-player-proposals .player-proposal').first();await narrative.locator('[name="text"]').fill('伙伴听到了提醒，大家在门前停下。');
  await playerSetting('proposal-narrative',()=>narrative.getByRole('button',{name:'回应叙事，不执行检定',exact:true}).click());await mobile.locator('#player-proposals').getByText('DM 已回应叙事',{exact:true}).waitFor();assert.equal((await readHost()).campaign.domain.roots.length,beforeNarrative.campaign.domain.roots.length);
  report.checks.push('Narrative-only response was visibly separate and added no game root or die');
  phase='share audience, withdrawal and pause';
  await host.getByText('共享给谁看',{exact:true}).click();await host.locator('#host-player-share-form [name="kind"]').selectOption('CHARACTER');await host.locator('#host-player-share-form [name="subjectId"]').selectOption(otherId);await playerSetting('share',()=>host.locator('#host-player-share-form button').click());
  await mobile.locator('#player-shared').getByRole('heading',{name:OTHER,exact:true}).waitFor();
  await host.locator('#host-player-share-form [name="audience"]').selectOption('NONE');await playerSetting('share',()=>host.locator('#host-player-share-form button').click());
  await mobile.locator('#player-shared-section').waitFor({state:'hidden'});assert.ok(!(await mobile.locator('#player-shared').innerText()).includes(OTHER));
  await playerSetting('pause',()=>host.locator('#host-players-pause').click());await mobile.locator('#player-status-title').filter({hasText:'DM 暂停了玩家操作'}).waitFor();assert.equal(await mobile.locator('#player-actions button:not([disabled])').count(),0);
  await screenshot(mobile,'01-player-paused-mobile.png',390,844);await playerSetting('pause',()=>host.locator('#host-players-pause').click());await mobile.locator('#player-actions button:not([disabled])').first().waitFor();
  report.checks.push('Persistent all-member sharing displayed only approved summary; withdrawal cleared it; explicit pause locked actions and explicit resume restored them');
  await mobileTab.context.setOffline(true);await mobile.locator('#player-status-title').filter({hasText:'连接中断'}).waitFor();assert.equal(await mobile.locator('#player-actions button:not([disabled])').count(),0);await mobileTab.context.setOffline(false);await mobile.locator('#player-status-title').filter({hasText:'你的席位已就绪'}).waitFor();assert.equal(report.playerCommandPosts,1);
  report.checks.push('Offline event immediately locked actions; reconnect synchronized without replaying a command');
  phase='second tab takeover and screenshots';
  const secondTab=await mobileTab.context.newPage();observe(secondTab);await secondTab.goto(PLAYER+'/player');await secondTab.locator('#player-table').waitFor();await secondTab.locator('#player-takeover').click();await secondTab.locator('#player-status-title').filter({hasText:'你的席位已就绪'}).waitFor();
  await mobile.locator('#player-status-title').filter({hasText:'此设备当前只读'}).waitFor();assert.equal(await mobile.locator('#player-actions button:not([disabled])').count(),0);
  await screenshot(secondTab,'02-player-ready-mobile.png',390,844);await screenshot(secondTab,'03-player-ready-wide.png',1440,1050);await screenshot(host,'04-dm-player-management-wide.png',1440,1050);
  await playerSetting('close',()=>host.locator('#host-players-close').click());assert.equal((await readPlayer(secondTab)).session.status,'APPROVED');
  report.checks.push('New page began read-only; explicit takeover disabled the former page; closing invitation preserved approved members');
  phase='end owned fixture';await hostAction('end',()=>host.locator('#storage-end').click());
  assert.equal((await readHost()).storage.state,'IDLE');assert.deepEqual(await fs.readdir(path.join(root,'runs')),[]);
  assert.deepEqual(report.pageErrors,[]);assert.deepEqual(report.cspFailures,[]);report.result='PASSED';
}
main().catch(async failure=>{report.error=failure.message;report.phase=phase;if(mobile&&!mobile.isClosed())await mobile.screenshot({path:path.join(output,'failure-player.png'),fullPage:true}).catch(()=>{});if(host&&!host.isClosed())await host.screenshot({path:path.join(output,'failure-host.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();if(outputReady)await fs.writeFile(path.join(output,'player-browser-report.json'),json(report),{flag:'wx'});console.log(JSON.stringify({result:report.result,checks:report.checks.length,phase:report.phase||'complete',evidence:path.join(output,'player-browser-report.json')}));});
