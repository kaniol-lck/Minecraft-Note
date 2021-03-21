void onRemove(blockState, level, blockPos, blockState2, bl) {
    //方块没有发生改变则返回
    if (blockState.is(blockState2.getBlock())) {
        return;
    }
    //非客户端，侦测器亮起，正处于计划刻
    if (!level.isClientSide && blockState.getValue(POWERED).booleanValue() && level.getBlockTicks().hasScheduledTick(blockPos, this)) {
        //向后方输出位置发出方块更新
        updateNeighborsInFront(level, blockPos, (BlockState)blockState.setValue(POWERED, false));
    }
}