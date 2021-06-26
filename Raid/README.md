袭击（Raid）是在Minecraft村庄与掠夺更新中加入的一种事件，在该事件中会按波次生成掠夺者、卫道士、唤魔师、劫掠兽与女巫，在此之前，卫道士与唤魔者只能作为林地府邸结构的一部分生成，所以不死图腾是非常珍贵的资源，想要大量使用只能利用游戏BUG进行物品复制，但在唤魔师可以在袭击中生成之后，不死图腾便成了玩家副手的标配。而利用袭击机制来进行堆叠袭击制作的袭击农场则可以让玩家大量击杀袭击中生成的生物，不仅可以获得巨量的绿宝石用于交易，还可以让不死图腾这种昔日的珍宝变得让人恨不得直接扔进仙人掌。

BV1kt4y1D7cp

## 触发袭击

当玩家获得不祥之兆效果之后，该效果会一直检查你是否进入村庄的范围，一旦玩家进入村庄的3×3×3的子区块范围内，就会触发袭击事件。

```java
//net/minecraft/world/effect/MobEffects.java:L63
applyEffectTick(livingEntity, n) {
    //非旁观者玩家
    if (livingEntity instanceof ServerPlayer && !livingEntity.isSpectator()) return;
    serverPlayer = (ServerPlayer)livingEntity;
    //不为和平模式
    if (getDifficulty() == Difficulty.PEACEFUL) return;
    //当前位置处于村庄范围内
    if (isVillage(livingEntity.blockPosition())) {
        //创建或扩大袭击
        getRaids().createOrExtendRaid(serverPlayer);
    }
}
```

在这一步，游戏会再次检查玩家是否为旁观者，并检查游戏规则是否禁止了袭击生成，然后开始确定袭击的中心位置。

游戏会查找玩家64格内的所有的已占用的村民职业方块，并将它们的坐标值取平均值作为袭击的位置，如果不存在已占用的职业方块，那么将以玩家的坐标作为袭击的位置。游戏会在这个位置上查找袭击，如果这个位置上存在袭击，那么就尝试扩大袭击的规模，否则创建一个新的袭击。

当袭击还没有开始或者袭击的不祥之兆等级还没有加满，那么袭击会吸收玩家的不祥之兆等级。而如果袭击的等级已经达到满级，那么会直接消除玩家身上的不祥之兆效果。

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

所以，当第一次触发袭击的时候，袭击的等级就等于玩家身上不祥之兆的等级，两个2级不祥之兆的玩家就会触发4级的袭击。使用过`/effect`指令的人应该知道，指令中的效果等级是从0开始计数的，所以在此处代码中是效果等级+1（说是效果等级实际上是效果加强等级，也就是1级是标准的状态效果，而二级就是一级效果等级的一次加强，所以看起来从0开始计数其实并没有什么问题）。

```java
//net/minecraft/world/entity/raid/Raid.java:L215
//吸收不祥之兆
Raid.absorbBadOmen(player) {
    //当玩家身上带有不祥之兆效果时
    if (player.hasEffect(MobEffects.BAD_OMEN)) {
        //袭击的等级会加上玩家的不祥之兆等级
        badOmenLevel += player.getEffect(MobEffects.BAD_OMEN).getAmplifier() + 1;
        //将袭击等级调整至0至最大等级之间
        badOmenLevel = Mth.clamp(this.badOmenLevel, 0, this.getMaxBadOmenLevel());
    }
    //移除玩家的不祥之兆效果
    player.removeEffect(MobEffects.BAD_OMEN);
}
```

创建袭击之后

袭击事件会在每gt的游戏循环中计算每个单独的袭击事件，

袭击分为几种状态：正在进行、胜利、失败和终止。

