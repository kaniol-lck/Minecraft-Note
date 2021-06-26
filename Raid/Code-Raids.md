```java
Raids.tick() {
    ++tick;
    //袭击迭代器
    iterator = raidMap.values().iterator();
    //迭代每一次袭击
    while (iterator.hasNext()) {
        raid = iterator.next();
        //如果游戏规则禁用袭击，则终止袭击
        if (getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
            raid.stop();
        }
        //如果袭击已终止，则移除该袭击
        if (raid.isStopped()) {
            iterator.remove();
            setDirty();
            continue;
        }
        //计算每一个袭击
        raid.tick();
    }
    //每10秒设置已改变
    if (tick % 200 == 0) {
        setDirty();
    }
    DebugPackets.sendRaids(raidMap.values());
}
```

```java
canJoinRaid(raider, raid) {
    if (raider != null && raid != null && raid.getLevel() != null) {
        //需要袭击者存活、可加入袭击、发呆不超过两分钟。以及袭击者和袭击处于同一维度
        return raider.isAlive() && raider.canJoinRaid() && raider.getNoActionTime() <= 2400 && raider.level.dimensionType() == raid.getLevel().dimensionType();
    }
    return false;
}
```



```java
//net/minecraft/world/entity/raid/Raids.java:L92
//创建或扩大袭击
Raids.createOrExtendRaid(serverPlayer) {
    //非旁观者玩家
    if (serverPlayer.isSpectator()) return null;
    //游戏规则没有禁用袭击
    if (getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) return null;
    //该维度允许生成袭击
    if (!serverPlayer.level.dimensionType().hasRaids()) return null;
    //获取玩家位置
    playerPos = serverPlayer.blockPosition();
    //在poi管理器中寻找玩家64格内已占用的职业方块
    list = getPoiManager().getInRange(PoiType.ALL, playerPos, 64, PoiManager.Occupancy.IS_OCCUPIED).collect(Collectors.toList());
    //取这些职业方块确定的中心位置（XYZ平均）
    n = 0;
    vec3 = Vec3.ZERO;
    for (poiRecord : list) {
        blockPos3 = poiRecord.getPos();
        vec3 = vec3.add(blockPos3.getX(), blockPos3.getY(), blockPos3.getZ());
        ++n;
    }
    if (n > 0) {
        vec3 = vec3.scale(1.0 / (double)n);
        raidPos = new BlockPos(vec3);
    } else {
        //如果没有职业方块则使用玩家位置
        raidPos = playerPos;
    }
    //获取或创建袭击
    raid = getOrCreateRaid(raidPos);
    canAbsorbBadOmen = false;
    //如果袭击没有开始
    if (!raid.isStarted()) {
        //将袭击添加到列表中
        if (!raidMap.containsKey(raid.getId())) {
            raidMap.put(raid.getId(), raid);
        }
        canAbsorbBadOmen = true;
    } 
    //如果不祥之兆没有加满
    else if (raid.getBadOmenLevel() < raid.getMaxBadOmenLevel()) {
        canAbsorbBadOmen = true;
    } else {
        //袭击已经开始且不祥之兆等级已满，则移除玩家身上的不祥之兆
        serverPlayer.removeEffect(MobEffects.BAD_OMEN);
        serverPlayer.connection.send(new ClientboundEntityEventPacket(serverPlayer, 43));
    }
    if (canAbsorbBadOmen) {
        //袭击吸收不祥之兆等级
        raid.absorbBadOmen(serverPlayer);
        serverPlayer.connection.send(new ClientboundEntityEventPacket(serverPlayer, 43));
        //检查玩家的进度
        if (!raid.hasFirstWaveSpawned()) {
            serverPlayer.awardStat(Stats.RAID_TRIGGER);
            CriteriaTriggers.BAD_OMEN.trigger(serverPlayer);
        }
    }
    //设置已更新
    setDirty();
    return raid;
}
```

```java
//net/minecraft/world/entity/raid/Raids.java:L144
//获取或创建袭击
getOrCreateRaid(blockPos) {
    raid = getRaidAt(blockPos);
    return raid != null ? raid : new Raid(getUniqueId(), blockPos);
}
```

