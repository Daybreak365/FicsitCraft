#!/usr/bin/env python3
"""Generates en_us.json and ko_kr.json. Run from the project root."""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'ficsitcraft', 'lang')
os.makedirs(OUT, exist_ok=True)

# key: (english, korean)
L = {}


def add(key, en, ko):
    L[key] = (en, ko)


# ---------------------------------------------------------------- blocks
blocks = {
    'hub': ('The HUB', 'HUB', 'FICSIT headquarters. Submit parts here to complete milestones.', 'FICSIT 본부. 부품을 제출해 마일스톤을 달성합니다.'),
    'craft_bench': ('Craft Bench', '제작대', 'Hand-craft unlocked parts.', '해금된 부품을 손으로 제작합니다.'),
    'storage_container': ('Storage Container', '보관함', '27 slots. Accepts items from belts on every side.', '27칸. 모든 면에서 벨트 입력을 받습니다.'),
    'miner_mk1': ('Miner Mk.1', '채굴기 Mk.1', 'Place on a resource node. 30/60/120 items/min (impure/normal/pure). 5 MW.', '자원 노드 위에 설치. 분당 30/60/120개 (불순/보통/순수). 5 MW.'),
    'miner_mk2': ('Miner Mk.2', '채굴기 Mk.2', 'Place on a resource node. 60/120/240 items/min. 12 MW.', '자원 노드 위에 설치. 분당 60/120/240개. 12 MW.'),
    'smelter': ('Smelter', '제련기', 'Smelts ore into ingots. 4 MW.', '광석을 주괴로 제련합니다. 4 MW.'),
    'foundry': ('Foundry', '주조기', 'Combines two resources into alloys (steel). 16 MW.', '두 가지 자원을 합금(강철)으로 만듭니다. 16 MW.'),
    'constructor': ('Constructor', '제작기', 'Turns one input into one part. 4 MW.', '한 종류의 재료로 부품을 만듭니다. 4 MW.'),
    'assembler': ('Assembler', '조립기', 'Assembles two parts into a new one. 15 MW.', '두 부품을 조립합니다. 15 MW.'),
    'manufacturer': ('Manufacturer', '제조기', 'Builds complex parts from up to four inputs. 55 MW.', '최대 4종류의 재료로 복잡한 부품을 만듭니다. 55 MW.'),
    'biomass_burner': ('Biomass Burner', '바이오매스 버너', 'Burns leaves, wood, biomass or solid biofuel. 30 MW.', '나뭇잎, 원목, 바이오매스, 고체 바이오 연료를 태웁니다. 30 MW.'),
    'coal_generator': ('Coal Generator', '석탄 발전기', 'Burns coal. Needs 45 m³/min of water through a pipeline. 75 MW.', '석탄을 태웁니다. 파이프로 분당 45 m³의 물 공급 필요. 75 MW.'),
    'splitter': ('Conveyor Splitter', '컨베이어 분배기', 'Input at the back, splits evenly to left, front and right.', '뒤에서 입력받아 좌/앞/우로 균등 분배합니다.'),
    'merger': ('Conveyor Merger', '컨베이어 병합기', 'Inputs on back, left and right; outputs at the front.', '뒤/좌/우에서 입력받아 앞으로 출력합니다.'),
}
for k, (en, ko, den, dko) in blocks.items():
    add('block.ficsitcraft.' + k, en, ko)
    add('block.ficsitcraft.' + k + '.desc', den, dko)

simple_blocks = {
    'power_pole_mk1': ('Power Pole Mk.1', '전신주 Mk.1'),
    'power_pole_mk2': ('Power Pole Mk.2', '전신주 Mk.2'),
    'power_pole_mk3': ('Power Pole Mk.3', '전신주 Mk.3'),
    'conveyor_belt_mk1': ('Conveyor Belt Mk.1', '컨베이어 벨트 Mk.1'),
    'conveyor_belt_mk2': ('Conveyor Belt Mk.2', '컨베이어 벨트 Mk.2'),
    'conveyor_belt_mk3': ('Conveyor Belt Mk.3', '컨베이어 벨트 Mk.3'),
    'foundation': ('Foundation', '토대'),
    'concrete_wall': ('Concrete Wall', '콘크리트 벽'),
    'creative_generator': ('Creative Generator (Test)', '크리에이티브 발전기 (테스트)'),
}
for k, (en, ko) in simple_blocks.items():
    add('block.ficsitcraft.' + k, en, ko)

nodes = {
    'iron': ('Iron', '철'), 'copper': ('Copper', '구리'), 'limestone': ('Limestone', '석회석'),
    'coal': ('Coal', '석탄'), 'caterium': ('Caterium', '카테리움'), 'quartz': ('Raw Quartz', '원석 석영'),
}
for k, (en, ko) in nodes.items():
    add('block.ficsitcraft.%s_node' % k, '%s Resource Node' % en, '%s 자원 노드' % ko)
    add('node.ficsitcraft.' + k, en, ko)

add('purity.ficsitcraft.impure', 'Impure', '불순')
add('purity.ficsitcraft.normal', 'Normal', '보통')
add('purity.ficsitcraft.pure', 'Pure', '순수')

# ---------------------------------------------------------------- items
parts = {
    'limestone': ('Limestone', '석회석', 'Used for Concrete.', '콘크리트 재료.'),
    'caterium_ore': ('Caterium Ore', '카테리움 광석', 'Smelted into Caterium Ingots.', '카테리움 주괴로 제련됩니다.'),
    'raw_quartz': ('Raw Quartz', '원석 석영', 'Processed into Quartz Crystals and Silica.', '석영 결정과 실리카로 가공됩니다.'),
    'caterium_ingot': ('Caterium Ingot', '카테리움 주괴', 'Used for Quickwire.', '퀵와이어 재료.'),
    'steel_ingot': ('Steel Ingot', '강철 주괴', 'Iron and coal alloy.', '철과 석탄의 합금.'),
    'concrete': ('Concrete', '콘크리트', 'Building material.', '건축 자재.'),
    'iron_plate': ('Iron Plate', '철판', 'Basic building part.', '기본 부품.'),
    'iron_rod': ('Iron Rod', '철봉', 'Basic building part.', '기본 부품.'),
    'screw': ('Screw', '나사', 'Lots of them.', '엄청 많이 필요합니다.'),
    'wire': ('Wire', '전선', 'Copper wire.', '구리 전선.'),
    'copper_sheet': ('Copper Sheet', '구리판', 'Pliable copper part.', '유연한 구리 부품.'),
    'quickwire': ('Quickwire', '퀵와이어', 'Highly conductive caterium wire.', '전도성이 높은 카테리움 전선.'),
    'quartz_crystal': ('Quartz Crystal', '석영 결정', 'Oscillator material.', '발진기 재료.'),
    'silica': ('Silica', '실리카', 'Fine quartz powder.', '고운 석영 가루.'),
    'steel_beam': ('Steel Beam', '강철 빔', 'Structural steel.', '구조용 강철.'),
    'steel_pipe': ('Steel Pipe', '강철 파이프', 'Structural steel pipe.', '구조용 강철 파이프.'),
    'reinforced_iron_plate': ('Reinforced Iron Plate', '강화 철판', 'Sturdy multi-purpose plate.', '튼튼한 다목적 철판.'),
    'rotor': ('Rotor', '회전자', 'Moving part of motors.', '모터의 회전 부품.'),
    'modular_frame': ('Modular Frame', '모듈식 프레임', 'Multi-purpose frame.', '다목적 프레임.'),
    'encased_industrial_beam': ('Encased Industrial Beam', '콘크리트 강철 빔', 'Concrete-encased steel.', '콘크리트로 감싼 강철.'),
    'stator': ('Stator', '고정자', 'Stationary part of motors.', '모터의 고정 부품.'),
    'motor': ('Motor', '모터', 'Industrial motor.', '산업용 모터.'),
    'heavy_modular_frame': ('Heavy Modular Frame', '중량 모듈식 프레임', 'Extremely durable frame.', '매우 튼튼한 프레임.'),
    'smart_plating': ('Smart Plating', '스마트 플레이팅', 'Space Elevator part.', '우주 엘리베이터 부품.'),
    'versatile_framework': ('Versatile Framework', '다목적 뼈대', 'Space Elevator part.', '우주 엘리베이터 부품.'),
    'automated_wiring': ('Automated Wiring', '자동화 배선', 'Space Elevator part.', '우주 엘리베이터 부품.'),
    'biomass': ('Biomass', '바이오매스', 'Fuel: 180 MJ.', '연료: 180 MJ.'),
    'solid_biofuel': ('Solid Biofuel', '고체 바이오 연료', 'Fuel: 450 MJ.', '연료: 450 MJ.'),
}
for k, (en, ko, den, dko) in parts.items():
    add('item.ficsitcraft.' + k, en, ko)
    add('item.ficsitcraft.' + k + '.desc', den, dko)
add('item.ficsitcraft.cable', 'Cable', '케이블')
add('item.ficsitcraft.build_gun', 'Build Gun', '건설총')

add('itemGroup.ficsitcraft.buildings', 'FICSIT Buildings', 'FICSIT 건물')
add('itemGroup.ficsitcraft.parts', 'FICSIT Parts', 'FICSIT 부품')
add('ingredient.ficsitcraft.leaves', 'Leaves', '나뭇잎')
add('ingredient.ficsitcraft.wood', 'Wood (any log)', '원목 (아무 종류)')

# ---------------------------------------------------------------- tooltips
add('tooltip.ficsitcraft.node', 'Infinite. Right-click to hand-mine, or place a Miner on top.', '무한 자원. 우클릭으로 손 채굴하거나 위에 채굴기를 설치하세요.')
add('tooltip.ficsitcraft.power_pole', 'Up to %s power line connections. Height: %s blocks.', '전선 최대 %s개 연결. 높이: %s블록.')
add('message.ficsitcraft.pole_no_space', 'Not enough space! The pole needs %s free blocks upwards.', '공간이 부족합니다! 위로 %s블록의 빈 공간이 필요합니다.')
add('tooltip.ficsitcraft.creative_generator', 'Testing only: %s MW without fuel. Creative mode only.', '테스트 전용: 연료 없이 %s MW 생산. 크리에이티브 모드 전용.')
add('tooltip.ficsitcraft.creative_generator_use', 'Right-click: reset the fuse and show the grid state.', '우클릭: 퓨즈 리셋 및 전력망 상태 표시.')
add('message.ficsitcraft.creative_only', 'The Creative Generator can only be placed in creative mode!', '크리에이티브 발전기는 크리에이티브 모드에서만 설치할 수 있습니다!')
add('message.ficsitcraft.creative_generator_status', 'Grid: %s MW capacity, %s MW load - %s', '전력망: 용량 %s MW, 부하 %s MW - %s')
add('message.ficsitcraft.fuse_reset', 'fuse reset', '퓨즈 리셋됨')
add('message.ficsitcraft.fuse_ok', 'fuse OK', '퓨즈 정상')
add('tooltip.ficsitcraft.cable', 'Right-click two power connectors to string a power line (1 cable / 10 blocks).', '전력 연결점 두 개를 차례로 우클릭해 전선을 연결 (10블록당 케이블 1개).')
add('tooltip.ficsitcraft.cable2', 'Sneak + right-click: remove all lines of a connector.', '웅크리고 우클릭: 해당 연결점의 전선 모두 제거.')
add('tooltip.ficsitcraft.belt_speed', 'Throughput: %s items/min', '처리량: 분당 %s개')
add('tooltip.ficsitcraft.belt_lift', 'Curves and ramps connect automatically like rails. Sneak-place for a vertical lift.', '레일처럼 커브·경사로가 자동으로 연결됩니다. 웅크리고 설치하면 수직 리프트.')
add('tooltip.ficsitcraft.build_gun', 'Right-click to open the build menu and construct unlocked buildings.', '우클릭으로 건설 메뉴를 열어 해금된 건물을 만듭니다.')

# ---------------------------------------------------------------- messages
add('message.ficsitcraft.welcome', '[FICSIT] Welcome, Pioneer! Place your HUB, hand-mine resource nodes and complete milestones. Use the Build Gun to construct buildings.',
    '[FICSIT] 환영합니다, 개척자님! HUB를 설치하고, 자원 노드를 손으로 채굴해 마일스톤을 달성하세요. 건물은 건설총으로 만듭니다.')
add('message.ficsitcraft.node_info', '%s node (%s)', '%s 노드 (%s)')
add('message.ficsitcraft.miner_needs_node', 'Miners must be placed directly on a resource node!', '채굴기는 자원 노드 바로 위에 설치해야 합니다!')
add('message.ficsitcraft.pole_info', 'Lines: %s/%s   Grid: %s / %s MW', '전선: %s/%s   전력망: %s / %s MW')
add('message.ficsitcraft.lines_removed', 'Removed %s power line(s).', '전선 %s개를 제거했습니다.')
add('message.ficsitcraft.no_free_connection', 'No free power connection!', '남은 전력 연결 슬롯이 없습니다!')
add('message.ficsitcraft.line_start', 'Power line started. Right-click a second connector.', '전선 시작. 두 번째 연결점을 우클릭하세요.')
add('message.ficsitcraft.line_cancel', 'Power line cancelled.', '전선 연결 취소.')
add('message.ficsitcraft.line_invalid', 'The first connector no longer exists.', '첫 번째 연결점이 더 이상 존재하지 않습니다.')
add('message.ficsitcraft.line_exists', 'Already connected.', '이미 연결되어 있습니다.')
add('message.ficsitcraft.line_too_long', 'Too long! Maximum %s blocks.', '너무 깁니다! 최대 %s블록.')
add('message.ficsitcraft.line_need_cable', 'Needs %s cable.', '케이블 %s개가 필요합니다.')
add('message.ficsitcraft.line_done', 'Power line built (%s blocks, %s cable).', '전선 연결 완료 (%s블록, 케이블 %s개).')
add('message.ficsitcraft.milestone_done', '[FICSIT] Milestone completed: %s', '[FICSIT] 마일스톤 달성: %s')
add('message.ficsitcraft.tiers_1_2', '[FICSIT] Tier 1 and Tier 2 are now available in the HUB!', '[FICSIT] HUB에서 티어 1, 2가 열렸습니다!')
add('message.ficsitcraft.tiers_3_4', '[FICSIT] Space Elevator Phase 1 delivered! Tier 3 and Tier 4 are now available.', '[FICSIT] 우주 엘리베이터 1단계 납품 완료! 티어 3, 4가 열렸습니다.')
add('message.ficsitcraft.victory', '[FICSIT] Project Assembly Phase 2 delivered. FICSIT thanks you for your efficiency, Pioneer!', '[FICSIT] 프로젝트 어셈블리 2단계 납품 완료. 효율적인 작업에 감사드립니다, 개척자님!')
add('message.ficsitcraft.deposited', 'Items deposited into the milestone.', '마일스톤에 아이템을 납품했습니다.')
add('message.ficsitcraft.missing_items', 'Missing items!', '재료가 부족합니다!')

# ---------------------------------------------------------------- milestones
ms = {
    'hub_upgrade_1': ('HUB Upgrade 1', 'HUB 업그레이드 1', 'Screws, Storage Container.', '나사, 보관함.'),
    'hub_upgrade_2': ('HUB Upgrade 2', 'HUB 업그레이드 2', 'Smelter, Conveyor Belts, copper parts.', '제련기, 컨베이어 벨트, 구리 부품.'),
    'hub_upgrade_3': ('HUB Upgrade 3', 'HUB 업그레이드 3', 'Constructor, Biomass Burner, Power Poles, Concrete.', '제작기, 바이오매스 버너, 전신주, 콘크리트.'),
    'hub_upgrade_4': ('HUB Upgrade 4', 'HUB 업그레이드 4', 'Miner Mk.1.', '채굴기 Mk.1.'),
    'hub_upgrade_5': ('HUB Upgrade 5', 'HUB 업그레이드 5', 'Rotors and Solid Biofuel.', '회전자, 고체 바이오 연료.'),
    'hub_upgrade_6': ('HUB Upgrade 6', 'HUB 업그레이드 6', 'Unlocks Tier 1 and Tier 2 milestones.', '티어 1, 2 마일스톤을 해금합니다.'),
    'base_building': ('Base Building', '기지 건설', 'Foundations and walls.', '토대와 벽.'),
    'logistics': ('Logistics', '물류', 'Conveyor Splitter and Merger.', '컨베이어 분배기와 병합기.'),
    'part_assembly': ('Part Assembly', '부품 조립', 'Assembler, Modular Frames, Smart Plating.', '조립기, 모듈식 프레임, 스마트 플레이팅.'),
    'logistics_mk2': ('Logistics Mk.2', '물류 Mk.2', 'Conveyor Belt Mk.2, Power Pole Mk.2 and the Zipline.', '컨베이어 벨트 Mk.2, 전신주 Mk.2, 짚라인.'),
    'project_phase_1': ('Space Elevator: Phase 1', '우주 엘리베이터: 1단계', 'Deliver Smart Plating to unlock Tier 3 and Tier 4.', '스마트 플레이팅을 납품해 티어 3, 4를 해금합니다.'),
    'coal_power': ('Coal Power', '석탄 발전', 'Coal Generator, Water Extractor, Pipelines, Pipeline Pump, Fluid Buffer.', '석탄 발전기, 워터 추출기, 파이프라인, 파이프라인 펌프, 유체 저장고.'),
    'basic_steel': ('Basic Steel Production', '기초 강철 생산', 'Foundry, Steel Beams and Pipes, Versatile Framework.', '주조기, 강철 빔/파이프, 다목적 뼈대.'),
    'advanced_steel': ('Advanced Steel Production', '고급 강철 생산', 'Manufacturer, Motors, Heavy Modular Frames.', '제조기, 모터, 중량 모듈식 프레임.'),
    'improved_logistics': ('Improved Mining & Logistics', '향상된 채굴과 물류', 'Miner Mk.2, Belt Mk.3, Power Pole Mk.3.', '채굴기 Mk.2, 벨트 Mk.3, 전신주 Mk.3.'),
    'caterium_quartz': ('Caterium & Quartz', '카테리움과 석영', 'Caterium Ingots, Quickwire, Quartz Crystals, Silica.', '카테리움 주괴, 퀵와이어, 석영 결정, 실리카.'),
    'railway_tech': ('Railway Technology', '철도 기술', 'Railway, Train Station, Empty Platform, Locomotive, Freight Car, Block Signal.', '철로, 기차역, 빈 플랫폼, 기관차, 화물차, 블록 신호기.'),
    'train_logistics': ('Train Logistics', '열차 물류', 'Freight Platform, Fluid Freight Platform, Fluid Freight Car, Path Signal.', '화물 플랫폼, 유체 화물 플랫폼, 유체 화물차, 경로 신호기.'),
    'project_phase_2': ('Space Elevator: Phase 2', '우주 엘리베이터: 2단계', 'The final delivery. FICSIT is watching.', '최종 납품. FICSIT가 지켜보고 있습니다.'),
}
for k, (en, ko, den, dko) in ms.items():
    add('milestone.ficsitcraft.' + k, en, ko)
    add('milestone.ficsitcraft.' + k + '.desc', den, dko)


# ---------------------------------------------------------------- power notifications & pole GUI
add('toast.ficsitcraft.fuse_blown', 'FUSE BLOWN!', '퓨즈 끊어짐!')
add('toast.ficsitcraft.fuse_blown.detail', 'Load %s MW > capacity %s MW', '부하 %s MW > 용량 %s MW')
add('toast.ficsitcraft.power_restored', 'Power restored', '전력 복구됨')
add('toast.ficsitcraft.power_restored.detail', 'Load %s / %s MW', '부하 %s / %s MW')
add('gui.ficsitcraft.pole.capacity', 'Capacity', '발전 용량')
add('gui.ficsitcraft.pole.consumption', 'Consumption', '소비')
add('gui.ficsitcraft.pole.max_consumption', 'Max consumption', '최대 소비')
add('gui.ficsitcraft.pole.state_ok', 'Grid OK - load %s%%', '전력망 정상 - 부하 %s%%')
add('gui.ficsitcraft.pole.state_none', 'No power production', '발전 없음')
add('gui.ficsitcraft.pole.collecting', 'Collecting data...', '데이터 수집 중...')
add('gui.ficsitcraft.pole.lines', 'Lines %s/%s', '전선 %s/%s')
add('gui.ficsitcraft.pole.counts', 'Generators %s · Consumers %s · Nodes %s', '발전기 %s · 소비 건물 %s · 노드 %s')
add('gui.ficsitcraft.pole.axis', 'last 60 s', '최근 60초')
add('gui.ficsitcraft.pole.seconds_ago', '%s s ago', '%s초 전')

add('message.ficsitcraft.no_space', 'Not enough space for this building!', '건물을 지을 공간이 부족합니다!')
add('toast.ficsitcraft.milestone', 'MILESTONE COMPLETED', '마일스톤 달성')
add('toast.ficsitcraft.tiers_1_2', 'Tier 1 & 2 unlocked in the HUB!', 'HUB에서 티어 1, 2 해금!')
add('toast.ficsitcraft.tiers_3_4', 'Tier 3 & 4 unlocked!', '티어 3, 4 해금!')
add('toast.ficsitcraft.victory', 'FICSIT thanks you, Pioneer!', 'FICSIT가 감사드립니다, 개척자님!')

# ---------------------------------------------------------------- fluids & zipline
add('fluid.ficsitcraft.none', 'Empty', '비어 있음')
add('fluid.ficsitcraft.water', 'Water', '물')
add('fluid.ficsitcraft.lava', 'Lava', '용암')
add('block.ficsitcraft.pipeline_mk1', 'Pipeline Mk.1', '파이프라인 Mk.1')
add('block.ficsitcraft.pipeline_mk2', 'Pipeline Mk.2', '파이프라인 Mk.2')
add('block.ficsitcraft.pipeline_pump', 'Pipeline Pump Mk.1', '파이프라인 펌프 Mk.1')
add('block.ficsitcraft.pipeline_pump.desc', 'In-line pump: pushes fluid from back to front with 20 blocks of head lift. 4 MW.', '인라인 펌프: 뒤에서 앞으로 유체를 밀어 올림 (헤드 리프트 20블록). 4 MW.')
add('block.ficsitcraft.water_extractor', 'Water Extractor', '워터 추출기')
add('block.ficsitcraft.water_extractor.desc', 'Place over water (or lava). 120 m³/min into pipelines, 10 blocks head lift. 20 MW.', '물(또는 용암) 위에 설치. 파이프로 분당 120 m³, 헤드 리프트 10블록. 20 MW.')
add('item.ficsitcraft.zipline', 'Zipline', '짚라인')
add('tooltip.ficsitcraft.pipe', '%s m³/min, holds %s m³ per block', '분당 %s m³, 블록당 %s m³ 저장')
add('tooltip.ficsitcraft.pipe2', 'Right-click: info · Sneak + right-click: flush network', '우클릭: 정보 · 웅크리고 우클릭: 네트워크 비우기')
add('tooltip.ficsitcraft.zipline', 'Jump into a power line (or right-click one) to ride it. W/S to move along it.', '전선으로 점프(또는 우클릭)해서 매달리기. W/S로 이동.')
add('tooltip.ficsitcraft.zipline2', 'Transfers at poles automatically · Space: jump off · Sneak: let go', '전신주에서 자동 환승 · 스페이스: 뛰어내리기 · 웅크리기: 놓기')
add('message.ficsitcraft.pipe_flushed', 'Flushed %s pipe segments.', '파이프 %s칸을 비웠습니다.')
add('message.ficsitcraft.pipe_empty', 'Empty pipe', '빈 파이프')
add('message.ficsitcraft.pipe_info', '%s  %s / %s m³  ·  flow %s m³/min', '%s  %s / %s m³  ·  유량 분당 %s m³')
add('message.ficsitcraft.extractor_needs_water', 'The Water Extractor must be placed over water or lava!', '워터 추출기는 물이나 용암 위에 설치해야 합니다!')
add('gui.ficsitcraft.water_tank', 'Water %s / %s m³ (uses %s m³/min at full load)', '물 %s / %s m³ (최대 부하 시 분당 %s m³ 소비)')
add('gui.ficsitcraft.fluid.fluid', 'Fluid', '유체')
add('gui.ficsitcraft.fluid.flow', 'Flow: %s m³/min', '유량: 분당 %s m³')
add('gui.ficsitcraft.fluid.lift', 'Head lift: %s blocks', '헤드 리프트: %s블록')
add('gui.ficsitcraft.fluid.buffer', 'Buffer %s / %s m³', '버퍼 %s / %s m³')
add('gui.ficsitcraft.status.no_source', 'No water/lava source', '수원 없음')
add('gui.ficsitcraft.status.pumping', 'Pumping', '펌핑 중')

add('gui.ficsitcraft.pipe.flow', 'Flow', '유량')
add('gui.ficsitcraft.pipe.max_flow', 'Max flow', '최대 유량')
add('gui.ficsitcraft.pipe.min', 'min', '분')
add('gui.ficsitcraft.pipe.volume', 'Current pipe fluid volume', '현재 파이프 유체량')

add('block.ficsitcraft.fluid_buffer', 'Fluid Buffer', '유체 저장고')
add('block.ficsitcraft.fluid_buffer.desc', 'Stores 400 m³ of fluid. Connect pipelines to any side; it fills from above its level and drains below it.', '유체 400 m³ 저장. 어느 면에나 파이프 연결 가능. 수위보다 위에서 채워지고 아래로 배출됩니다.')
add('gui.ficsitcraft.buffer.flush', 'Flush', '비우기')
add('gui.ficsitcraft.buffer.stored', 'Stored fluid', '저장된 유체량')
add('gui.ficsitcraft.buffer.inflow', 'Inflow', '유입')
add('gui.ficsitcraft.buffer.outflow', 'Outflow', '유출')

add('key.ficsitcraft.scan', 'Resource Scanner (hold: choose)', '자원 스캐너 (길게: 선택)')
add('category.ficsitcraft', 'FICSIT Craft', 'FICSIT Craft')
add('gui.ficsitcraft.scan.title', 'Resource Scanner', '자원 스캐너')
add('gui.ficsitcraft.scan.all', 'All resources', '모든 자원')
add('gui.ficsitcraft.scan.pick', 'Choose a resource', '자원 선택')
add('gui.ficsitcraft.scan.hint', 'Point and release V (or click)', '가리킨 뒤 V에서 손을 떼거나 클릭')
add('gui.ficsitcraft.scan.scanning', 'Scanning: %s', '스캔 중: %s')
add('message.ficsitcraft.scan_none', 'No %s nodes within 100 m', '100m 이내에 %s 노드가 없습니다')

# ---------------------------------------------------------------- gui
g = {
    'hub': ('HUB Terminal', 'HUB 터미널'),
    'craft_bench': ('Craft Bench', '제작대'),
    'build_gun': ('Build Gun', '건설총'),
    'no_recipe': ('No recipe selected', '레시피 미선택'),
    'status.select_recipe': ('Select a recipe with < >', '< > 로 레시피를 선택하세요'),
    'status.fuse': ('FUSE BLOWN', '퓨즈 끊어짐'),
    'status.output_full': ('Output full', '출력 가득 참'),
    'status.no_input': ('Waiting for input', '재료 대기 중'),
    'status.no_power': ('No power', '전력 없음'),
    'status.working': ('Producing', '생산 중'),
    'status.no_node': ('No resource node', '자원 노드 없음'),
    'status.no_water': ('No water', '물 없음'),
    'status.no_fuel': ('No fuel', '연료 없음'),
    'status.generating': ('Load %s%%', '부하 %s%%'),
    'status.standby': ('Standby', '대기'),
    'power_usage': ('%s MW', '%s MW'),
    'per_cycle': ('%s per cycle (%ss)', '사이클당 %s개 (%s초)'),
    'miner.node': ('%s (%s)', '%s (%s)'),
    'miner.rate': ('%s items/min', '분당 %s개'),
    'miner.no_node': ('Not on a resource node!', '자원 노드 위가 아닙니다!'),
    'reset_fuse': ('Reset fuse', '퓨즈 리셋'),
    'grid': ('Power grid', '전력망'),
    'grid.production': ('Capacity: %s MW', '용량: %s MW'),
    'grid.consumption': ('Consumption: %s MW', '소비: %s MW'),
    'shift_hint': ('Shift-click: x5', 'Shift 클릭: x5'),
    'have_need': ('Have %s / need %s', '보유 %s / 필요 %s'),
    'nothing_unlocked': ('Nothing unlocked yet', '아직 해금된 항목 없음'),
    'nothing_found': ('No matching buildings', '일치하는 건물 없음'),
    'search': ('Search...', '검색...'),
    'cat.all': ('All', '전체'),
    'cat.basics': ('Basics', '기본'),
    'cat.production': ('Production', '생산'),
    'cat.power': ('Power', '전력'),
    'cat.logistics': ('Logistics', '물류'),
    'cat.fluids': ('Fluids', '유체'),
    'cat.structures': ('Structures', '건축'),
    'cat.trains': ('Trains', '철도'),
    'hub.tier': ('Tier %s', '티어 %s'),
    'hub.submit': ('Submit', '납품'),
    'hub.completed': ('Completed', '완료'),
    'hub.available': ('Available', '진행 가능'),
    'hub.locked_previous': ('Complete the previous upgrade', '이전 업그레이드를 완료하세요'),
    'hub.locked_hub6': ('Requires HUB Upgrade 6', 'HUB 업그레이드 6 필요'),
    'hub.locked_phase1': ('Requires Space Elevator Phase 1', '우주 엘리베이터 1단계 필요'),
    'hub.in_inventory': ('in inventory: %s', '인벤토리: %s'),
    'hub.unlocks': ('Unlocks:', '해금:'),
    'hub.unlock_hint': ('Unlocked by this milestone', '이 마일스톤으로 해금'),
}
for k, (en, ko) in g.items():
    add('gui.ficsitcraft.' + k, en, ko)

# ---------------------------------------------------------------- railway
rail_blocks = {
    'train_station': ('Train Station', '기차역', 'Named stop of the railway (5x9). Trains dock here; connect power to run locomotives. Right-click to rename. 20 MW.',
                      '철도의 정거장(5x9). 열차가 정차합니다. 전력을 연결해야 기관차가 달립니다. 우클릭으로 이름 변경. 20 MW.'),
    'freight_platform': ('Freight Platform', '화물 플랫폼', 'Loads / unloads docked freight cars (belts on both sides). Sneak + right-click: switch load / unload. 10 MW.',
                         '정차한 화물차에 싣고 내립니다 (양옆에 벨트 연결). 웅크리고 우클릭: 적재/하역 전환. 10 MW.'),
    'fluid_freight_platform': ('Fluid Freight Platform', '유체 화물 플랫폼', 'Pumps fluid between pipelines and docked fluid freight cars. Sneak + right-click: switch load / unload. 10 MW.',
                               '파이프라인과 정차한 유체 화물차 사이로 유체를 옮깁니다. 웅크리고 우클릭: 적재/하역 전환. 10 MW.'),
    'empty_platform': ('Empty Platform', '빈 플랫폼', 'A plain railway platform that continues the line between other platforms.',
                       '다른 플랫폼 사이를 이어 주는 기본 철도 플랫폼.'),
}
for k, (en, ko, den, dko) in rail_blocks.items():
    add('block.ficsitcraft.' + k, en, ko)
    add('block.ficsitcraft.' + k + '.desc', den, dko)
rail_items = {
    'railway': ('Railway', '철로', 'Lay tracks with two clicks. Curves bend by themselves.', '두 번 클릭해서 선로를 놓습니다. 곡선은 자동으로 휘어집니다.'),
    'locomotive': ('Electric Locomotive', '전기 기관차', 'Draws up to 110 MW from the railway power network.', '철도 전력망에서 최대 110 MW를 사용합니다.'),
    'freight_car': ('Freight Car', '화물차', '36 item slots. Sneak + right-click on the car to open it.', '36칸. 웅크리고 우클릭하면 인벤토리가 열립니다.'),
    'fluid_freight_car': ('Fluid Freight Car', '유체 화물차', 'Carries up to 800 m³ of fluid.', '유체를 최대 800 m³ 운반합니다.'),
    'rail_signal_block': ('Block Signal', '블록 신호기', 'Splits the line into blocks: a train only enters a block that is free.', '선로를 구간으로 나눕니다. 열차는 비어 있는 구간에만 진입합니다.'),
    'rail_signal_path': ('Path Signal', '경로 신호기', 'Reserves the whole route through a junction so crossing trains do not block each other.', '분기점을 지나는 경로 전체를 예약해 교차하는 열차가 서로 막지 않게 합니다.'),
}
for k, (en, ko, den, dko) in rail_items.items():
    add('item.ficsitcraft.' + k, en, ko)
    add('item.ficsitcraft.' + k + '.desc', den, dko)
add('tooltip.ficsitcraft.railway', 'Right-click the ground to start, right-click again to finish a track.', '땅을 우클릭해 시작하고, 다시 우클릭해 선로를 완성합니다.')
add('tooltip.ficsitcraft.railway2', 'Snaps to track ends, branches off tracks. 1 Railway per 6 blocks. X: dismantle.', '선로 끝에 붙고 선로 중간에서 분기합니다. 6블록당 1개. X: 해체.')
add('tooltip.ficsitcraft.locomotive', 'Electric locomotive. Needs a powered Train Station on the network.', '전기 기관차. 철도 망에 전력이 연결된 기차역이 필요합니다.')
add('tooltip.ficsitcraft.freight_car', 'Freight car for items.', '아이템용 화물차.')
add('tooltip.ficsitcraft.fluid_freight_car', 'Tank car for fluids.', '유체용 탱크차.')
add('tooltip.ficsitcraft.vehicle_place', 'Right-click next to a track to put it on the rails; next to a train it couples on.', '선로 옆을 우클릭해 올려놓습니다. 열차 옆이면 연결됩니다.')
add('tooltip.ficsitcraft.signal_block', 'A train only enters a block that no other train is in.', '다른 열차가 없는 구간에만 열차가 진입합니다.')
add('tooltip.ficsitcraft.signal_path', 'Reserves the route through junctions for one train at a time.', '분기점 경로를 한 번에 한 열차에게만 예약합니다.')
add('tooltip.ficsitcraft.signal_place', 'Right-click a track: guards trains running the way you look. X: dismantle.', '선로를 우클릭: 바라보는 방향으로 달리는 열차를 통제합니다. X: 해체.')

m = {
    'rail_cancel': ('Track placement cancelled.', '선로 설치를 취소했습니다.'),
    'rail_no_free_side': ('This track end has no free side.', '이 선로 끝에는 연결할 수 있는 면이 없습니다.'),
    'rail_start': ('Start set. Click where the track should end.', '시작점 지정. 선로가 끝날 곳을 클릭하세요.'),
    'rail_placed': ('Track built (%s blocks, %s Railway).', '선로 완성 (%s블록, 철로 %s개).'),
    'rail_need_items': ('Needs %s Railway.', '철로 %s개가 필요합니다.'),
    'rail_occupied': ('A train is on that track!', '그 선로 위에 열차가 있습니다!'),
    'rail_problem_too_short': ('Too short.', '너무 짧습니다.'),
    'rail_problem_too_long': ('Too long (max 64 blocks per piece).', '너무 깁니다 (한 번에 최대 64블록).'),
    'rail_problem_too_steep': ('Too steep.', '경사가 너무 급합니다.'),
    'rail_problem_too_sharp': ('Curve too sharp.', '커브가 너무 급합니다.'),
    'rail_problem_bad_angle': ('Angle not possible - the track would double back.', '각도가 맞지 않습니다 - 선로가 되돌아갑니다.'),
    'rail_problem_no_anchor': ('Nothing to connect.', '연결할 대상이 없습니다.'),
    'rail_problem_same_node': ('That is the same track end.', '같은 선로 끝입니다.'),
    'rail_problem_same_track': ('Both ends are on the same track.', '두 끝이 같은 선로 위에 있습니다.'),
    'rail_problem_exists': ('These two are already connected.', '이미 연결되어 있습니다.'),
    'rail_problem_no_free_side': ('No free connection there.', '연결할 자리가 없습니다.'),
    'vehicle_no_track_here': ('Aim next to a track.', '선로 옆을 조준하세요.'),
    'vehicle_no_room': ('There is not enough track here.', '선로 길이가 부족합니다.'),
    'vehicle_occupied': ('Something is already standing there.', '이미 다른 차량이 있습니다.'),
    'no_track_here': ('No track here.', '이곳에 선로가 없습니다.'),
    'cant_signal_here': ('Cannot put a signal here.', '여기에는 신호기를 놓을 수 없습니다.'),
    'signal_needs_track': ('There is no track on that side.', '그쪽에는 선로가 없습니다.'),
    'signal_exists': ('There already is a signal of this kind.', '이미 같은 신호기가 있습니다.'),
    'train_moving': ('Stop the train first.', '먼저 열차를 세우세요.'),
    'train_driven': ('Somebody is driving this train.', '누군가 이 열차를 운전 중입니다.'),
    'no_timetable': ('Add stops to the timetable first.', '먼저 시간표에 정거장을 추가하세요.'),
    'coupled': ('Trains coupled.', '열차를 연결했습니다.'),
    'nothing_to_couple': ('No train close enough to couple.', '연결할 열차가 가까이에 없습니다.'),
    'nothing_to_dismantle': ('Nothing to dismantle there.', '해체할 대상이 없습니다.'),
    'platform_load': ('Mode: LOADING the train', '모드: 열차에 적재'),
    'platform_unload': ('Mode: UNLOADING the train', '모드: 열차에서 하역'),
}
for k, (en, ko) in m.items():
    add('message.ficsitcraft.' + k, en, ko)
h = {
    'rail_snap_node': ('Connect to this track end', '이 선로 끝에 연결'),
    'rail_snap_track': ('Branch off this track', '이 선로에서 분기'),
    'rail_start': ('Start a new track here', '여기서 새 선로 시작'),
    'rail_preview': ('%s blocks - %s Railway', '%s블록 - 철로 %s개'),
    'train_controls': ('W/S throttle - Space brake - H horn - G menu - Shift leave', 'W/S 가속 - Space 제동 - H 경적 - G 메뉴 - Shift 내리기'),
    'no_power': ('NO POWER', '전력 없음'),
    'brake': ('BRAKE', '제동'),
    'autopilot_paused': ('autopilot paused', '자동 운행 일시정지'),
}
for k, (en, ko) in h.items():
    add('hud.ficsitcraft.' + k, en, ko)
gt = {
    'empty': ('Empty', '비어 있음'),
    'station.title': ('Train Station', '기차역'),
    'station.name': ('Station name', '정거장 이름'),
    'station.done': ('Done', '완료'),
    'station.hint': ('Timetables use this name.', '시간표에서 이 이름을 사용합니다.'),
    'train.title': ('Train', '열차'),
    'train.name': ('Train name', '열차 이름'),
    'train.cars': ('Vehicles: %s', '차량: %s'),
    'train.open': ('Open', '열기'),
    'train.split': ('Split', '분리'),
    'train.drive': ('Drive', '운전'),
    'train.autopilot_on': ('Autopilot: ON', '자동 운행: 켜짐'),
    'train.autopilot_off': ('Autopilot: OFF', '자동 운행: 꺼짐'),
    'train.horn': ('Horn', '경적'),
    'train.couple': ('Couple nearby', '주변 열차 연결'),
    'train.close': ('Close', '닫기'),
    'train.speed': ('%s km/h', '%s km/h'),
    'train.no_power': ('No power', '전력 없음'),
    'train.distance': ('%s m', '%s m'),
    'train.timetable': ('Timetable', '시간표'),
    'train.no_stops': ('No stops yet', '정거장 없음'),
    'train.no_stations': ('No stations', '정거장 없음'),
    'train.add_stop': ('Add stop', '정거장 추가'),
    'train.mode_loaded': ('Loaded', '적재 완료'),
    'train.mode_loaded_short': ('Loaded', '적재'),
    'train.mode_time_short': ('Time', '시간'),
    'train.seconds': ('Dwell time (seconds)', '정차 시간(초)'),
    'train.status_idle': ('Idle', '대기'),
    'train.status_docked': ('Docked', '정차 중'),
    'train.status_en_route': ('En route', '운행 중'),
    'train.status_no_route': ('No route!', '경로 없음!'),
    'train.status_no_station': ('Station missing!', '정거장 없음!'),
    'train.status_no_timetable': ('No timetable', '시간표 없음'),
}
for k, (en, ko) in gt.items():
    add('gui.ficsitcraft.' + k, en, ko)
add('key.ficsitcraft.dismantle', 'Dismantle rail / signal / train', '철로/신호기/열차 해체')
add('key.ficsitcraft.horn', 'Train horn', '기차 경적')
add('key.ficsitcraft.train_menu', 'Train menu (while driving)', '열차 메뉴 (운전 중)')

with open(os.path.join(OUT, 'en_us.json'), 'w', encoding='utf-8') as f:
    json.dump({k: v[0] for k, v in L.items()}, f, indent=2, ensure_ascii=False)
    f.write('\n')
with open(os.path.join(OUT, 'ko_kr.json'), 'w', encoding='utf-8') as f:
    json.dump({k: v[1] for k, v in L.items()}, f, indent=2, ensure_ascii=False)
    f.write('\n')
print('%d translation keys' % len(L))
