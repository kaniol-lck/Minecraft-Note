

```java
//RaidStatus
//ONGOING 正在进行
//VICTORY 胜利
//LOSS 失败
//STOPPED 终止

public void tick() {
    //是否已经中止
    if (this.isStopped()) {
        return;
    }
    //如果正在进行
    if (this.status == RaidStatus.ONGOING) {
        int n;
        boolean bl;
        boolean bl2 = this.active;
        //
        this.active = this.level.hasChunkAt(this.center);
        //如果是和平模式，则中止袭击
        if (this.level.getDifficulty() == Difficulty.PEACEFUL) {
            this.stop();
            return;
        }
        //切换为非活跃状态时隐藏Boss条
        if (bl2 != this.active) {
            this.raidEvent.setVisible(this.active);
        }
        //非活跃状态则结束
        if (!this.active) {
            return;
        }
        //如果袭击中心没有村庄的话则尝试迁移
        if (!this.level.isVillage(this.center)) {
            this.moveRaidCenterToNearbyVillageSection();
        }
        //如果袭击中心还是没有村庄
        if (!this.level.isVillage(this.center)) {
            //如果生成过袭击生物则失败
            if (this.groupsSpawned > 0) {
                this.status = RaidStatus.LOSS;
            } else {
                //如果没生成过就算中止
                this.stop();
            }
        }
        //增加计时器
        ++this.ticksActive;
        //如果超时（4800gt，40分钟），则中止袭击
        if (this.ticksActive >= 48000L) {
            this.stop();
            return;
        }
        //获取存活袭击者的数量
        int totalRaidersAlive = this.getTotalRaidersAlive();
        //如果没有袭击者且还有更多波次
        if (totalRaidersAlive == 0 && this.hasMoreWaves()) {
            //如果没过冷却时间
            if (this.raidCooldownTicks > 0) {
                bl = this.waveSpawnPos.isPresent();
                int n3 = n = !bl && this.raidCooldownTicks % 5 == 0 ? 1 : 0;
                if (bl && !this.level.getChunkSource().isEntityTickingChunk(new ChunkPos(this.waveSpawnPos.get()))) {
                    n = 1;
                }
                if (n != 0) {
                    int n4 = 0;
                    if (this.raidCooldownTicks < 100) {
                        n4 = 1;
                    } else if (this.raidCooldownTicks < 40) {
                        n4 = 2;
                    }
                    this.waveSpawnPos = this.getValidSpawnPos(n4);
                }
                if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                    this.updatePlayers();
                }
                --this.raidCooldownTicks;
                this.raidEvent.setPercent(Mth.clamp((float)(300 - this.raidCooldownTicks) / 300.0f, 0.0f, 1.0f));
            } else if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                this.raidCooldownTicks = 300;
                this.raidEvent.setName(RAID_NAME_COMPONENT);
                return;
            }
        }
        //每20gt/1s
        if (this.ticksActive % 20L == 0L) {
            //更新玩家与袭击者
            this.updatePlayers();
            this.updateRaiders();
            //如果还有存活的袭击者
            if (totalRaidersAlive > 0) {
                //小于等于2则在Boss条上显示
                if (totalRaidersAlive <= 2) {
                    this.raidEvent.setName(RAID_NAME_COMPONENT.copy().append(" - ").append(new TranslatableComponent("event.minecraft.raid.raiders_remaining", totalRaidersAlive)));
                } else {
                    this.raidEvent.setName(RAID_NAME_COMPONENT);
                }
            } else {
                this.raidEvent.setName(RAID_NAME_COMPONENT);
            }
        }
        bl = false;
        n = 0;
        //如果还需要生成一组袭击者
        while (this.shouldSpawnGroup()) {
            BlockPos blockPos;
            //进行20次随机选点
            BlockPos blockPos2 = blockPos = this.waveSpawnPos.isPresent() ? this.waveSpawnPos.get() : this.findRandomSpawnPos(n, 20);
            //如果确定了生成点
            if (blockPos != null) {
                //开始袭击
                this.started = true;
                //在此处生成一组袭击者
                this.spawnGroup(blockPos);
                if (!bl) {
                    this.playSound(blockPos);
                    bl = true;
                }
            } else {
                ++n;
            }
            //若需要三次选点尝试，则中止袭击
            //（此处可吐槽麻将神秘的代码逻辑）
            if (n <= 3) continue;
            this.stop();
            break;
        }
        //如果袭击已经开始、没有更多的波次和存活的袭击者
        if (this.isStarted() && !this.hasMoreWaves() && totalRaidersAlive == 0) {
            //袭击后冷却40gt/1s
            if (this.postRaidTicks < 40) {
                ++this.postRaidTicks;
            } else {
                //赢咯
                this.status = RaidStatus.VICTORY;
                //村庄英雄！
                for (UUID uUID : this.heroesOfTheVillage) {
                    Entity entity = this.level.getEntity(uUID);
                    //排除旁观者
                    if (!(entity instanceof LivingEntity) || entity.isSpectator()) continue;
                    //给予实体不祥之兆-1等级的村庄英雄效果40分钟
                    LivingEntity livingEntity = (LivingEntity)entity;
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.badOmenLevel - 1, false, false, true));
                    //下面就是玩家限定了
                    if (!(livingEntity instanceof ServerPlayer)) continue;
                    ServerPlayer serverPlayer = (ServerPlayer)livingEntity;
                    //添加到统计信息
                    serverPlayer.awardStat(Stats.RAID_WIN);
                    //检查有没有取得进度
                    CriteriaTriggers.RAID_WIN.trigger(serverPlayer);
                }
            }
        }
        this.setDirty();
    }
    //如果已经结束了
    else if (this.isOver()) {
        //庆祝计时
        ++this.celebrationTicks;
        //计时30s后中止
        if (this.celebrationTicks >= 600) {
            this.stop();
            return;
        }
        //每计时20gt/1s
        if (this.celebrationTicks % 20 == 0) {
            //更新玩家
            this.updatePlayers();
            //显示Boss条
            this.raidEvent.setVisible(true);
            //如果胜利了
            if (this.isVictory()) {
                this.raidEvent.setPercent(0.0f);
                this.raidEvent.setName(RAID_BAR_VICTORY_COMPONENT);
            } else {
                this.raidEvent.setName(RAID_BAR_DEFEAT_COMPONENT);
            }
        }
    }
}
```

```java
//袭击迁移
private void moveRaidCenterToNearbyVillageSection() {
        Stream<SectionPos> stream = SectionPos.cube(SectionPos.of(this.center), 2);
        stream.filter(this.level::isVillage).map(SectionPos::center).min(Comparator.comparingDouble(blockPos -> blockPos.distSqr(this.center))).ifPresent(this::setCenter);
}
```

```java
//获取有效生成位置
private Optional<BlockPos> getValidSpawnPos(int n) {
    for (int i = 0; i < 3; ++i) {
        BlockPos blockPos = this.findRandomSpawnPos(n, 1);
        if (blockPos == null) continue;
        return Optional.of(blockPos);
    }
    return Optional.empty();
}
```

```java
//更新袭击者
private void updateRaiders() {
    Iterator<Set<Raider>> iterator = this.groupRaiderMap.values().iterator();
    HashSet<Raider> hashSet = Sets.newHashSet();
    while (iterator.hasNext()) {
        Set<Raider> set = iterator.next();
        Iterator object = set.iterator();
        while (object.hasNext()) {
            Raider raider = (Raider)object.next();
            BlockPos blockPos = raider.blockPosition();
            if (raider.isRemoved() || raider.level.dimension() != this.level.dimension() || this.center.distSqr(blockPos) >= 12544.0) {
                hashSet.add(raider);
                continue;
            }
            if (raider.tickCount <= 600) continue;
            if (this.level.getEntity(raider.getUUID()) == null) {
                hashSet.add(raider);
            }
            if (!this.level.isVillage(blockPos) && raider.getNoActionTime() > 2400) {
                raider.setTicksOutsideRaid(raider.getTicksOutsideRaid() + 1);
            }
            if (raider.getTicksOutsideRaid() < 30) continue;
            hashSet.add(raider);
        }
    }
    for (Raider raider : hashSet) {
        this.removeFromRaid(raider, true);
    }
}
```

```java
//生成一组袭击者
private void spawnGroup(BlockPos blockPos) {
    boolean bl = false;
    int n = this.groupsSpawned + 1;
    this.totalHealth = 0.0f;
    DifficultyInstance difficultyInstance = this.level.getCurrentDifficultyAt(blockPos);
    boolean bl2 = this.shouldSpawnBonusGroup();
    for (RaiderType raiderType : RaiderType.VALUES) {
        int n2 = this.getDefaultNumSpawns(raiderType, n, bl2) + this.getPotentialBonusSpawns(raiderType, this.random, n, difficultyInstance, bl2);
        int n3 = 0;
        for (int i = 0; i < n2; ++i) {
            Raider raider = (Raider)raiderType.entityType.create(this.level);
            if (!bl && raider.canBeLeader()) {
                raider.setPatrolLeader(true);
                this.setLeader(n, raider);
                bl = true;
            }
            this.joinRaid(n, raider, blockPos, false);
            if (raiderType.entityType != EntityType.RAVAGER) continue;
            Raider raider2 = null;
            if (n == this.getNumGroups(Difficulty.NORMAL)) {
                raider2 = EntityType.PILLAGER.create(this.level);
            } else if (n >= this.getNumGroups(Difficulty.HARD)) {
                raider2 = n3 == 0 ? (Raider)EntityType.EVOKER.create(this.level) : (Raider)EntityType.VINDICATOR.create(this.level);
            }
            ++n3;
            if (raider2 == null) continue;
            this.joinRaid(n, raider2, blockPos, false);
            raider2.moveTo(blockPos, 0.0f, 0.0f);
            raider2.startRiding(raider);
        }
    }
    this.waveSpawnPos = Optional.empty();
    ++this.groupsSpawned;
    this.updateBossbar();
    this.setDirty();
}
```

```java
//加入袭击
public void joinRaid(int n, Raider raider, @Nullable BlockPos blockPos, boolean bl) {
    boolean bl2 = this.addWaveMob(n, raider);
    if (bl2) {
        raider.setCurrentRaid(this);
        raider.setWave(n);
        raider.setCanJoinRaid(true);
        raider.setTicksOutsideRaid(0);
        if (!bl && blockPos != null) {
            raider.setPos((double)blockPos.getX() + 0.5, (double)blockPos.getY() + 1.0, (double)blockPos.getZ() + 0.5);
            raider.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(blockPos), MobSpawnType.EVENT, null, null);
            raider.applyRaidBuffs(n, false);
            raider.setOnGround(true);
            this.level.addFreshEntityWithPassengers(raider);
        }
    }
}
```

```java
public void removeFromRaid(Raider raider, boolean bl) {
    boolean bl2;
    Set<Raider> set = this.groupRaiderMap.get(raider.getWave());
    if (set != null && (bl2 = set.remove(raider))) {
        if (bl) {
            this.totalHealth -= raider.getHealth();
        }
        raider.setCurrentRaid(null);
        this.updateBossbar();
        this.setDirty();
    }
}
```

```java
//获取随机生成位置
private BlockPos findRandomSpawnPos(int n, int n2) {
    int n3 = n == 0 ? 2 : 2 - n;
    BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
    for (int i = 0; i < n2; ++i) {
        float f = this.level.random.nextFloat() * 6.2831855f;
        int n4 = this.center.getX() + Mth.floor(Mth.cos(f) * 32.0f * (float)n3) + this.level.random.nextInt(5);
        int n5 = this.center.getZ() + Mth.floor(Mth.sin(f) * 32.0f * (float)n3) + this.level.random.nextInt(5);
        int n6 = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, n4, n5);
        mutableBlockPos.set(n4, n6, n5);
        if (this.level.isVillage(mutableBlockPos) && n < 2 || !this.level.hasChunksAt(mutableBlockPos.getX() - 10, mutableBlockPos.getY() - 10, mutableBlockPos.getZ() - 10, mutableBlockPos.getX() + 10, mutableBlockPos.getY() + 10, mutableBlockPos.getZ() + 10) || !this.level.getChunkSource().isEntityTickingChunk(new ChunkPos(mutableBlockPos)) || !NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, this.level, mutableBlockPos, EntityType.RAVAGER) && (!this.level.getBlockState((BlockPos)mutableBlockPos.below()).is(Blocks.SNOW) || !this.level.getBlockState(mutableBlockPos).isAir())) continue;
        return mutableBlockPos;
    }
    return null;
}
```

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

