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

