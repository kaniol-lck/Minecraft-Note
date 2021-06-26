//三种会检查伸出的情况

//被手动放置
void setPlacedBy(level, blockPos, blockState, livingEntity, itemStack) {
    //非客户端
    if (!level.isClientSide) checkIfExtend(level, blockPos, blockState);
}

//受到更新
void neighborChanged(blockState, level, blockPos, block, blockPos2, bl) {
    //非客户端
    if (!level.isClientSide) checkIfExtend(level, blockPos, blockState);
}

//其他方式放置的方块，如推动
void onPlace(blockState, level, blockPos, blockState2, bl) {
    //如果方块没变则跳过
    if (blockState2.is(blockState.getBlock())) return;
    //非客户端且此处不为方块实体
    if (!level.isClientSide && level.getBlockEntity(blockPos) == null) checkIfExtend(level, blockPos, blockState);
}

//检查是否需要伸出
//n = 0, 伸出
//n = 1, 收回
//n = 2, 瞬推收回
void checkIfExtend(level, blockPos, blockState) {
    //活塞的朝向
    direction = blockState.getValue(FACING);
    //检查激活情况
    bl = getNeighborSignal(level, blockPos, direction);
    //如果活塞会被激活而没有伸出
    if (bl && !blockState.getValue(EXTENDED).booleanValue()) {
        //分析方块结构检查能否伸出
        if (new PistonStructureResolver(level, blockPos, direction, true).resolve()) {
            //将本次伸出添加到方块事件
            level.blockEvent(blockPos, this, 0, direction.get3DDataValue());
        }
        //如果活塞没有被激活而伸出了
    } else if (!bl && blockState.getValue(EXTENDED).booleanValue()) {
        //伸出一格之外的位置,获取此处方块状态
        blockPos2 = blockPos.relative(direction, 2);
        blockState2 = level.getBlockState(blockPos2);
        n = 1;
        //如果这个方块是朝向与当前活塞同向推出的b36方块
        //且这次更新不为b36方块的到位更新
        if (blockState2.is(Blocks.MOVING_PISTON) && blockState2.getValue(FACING) == direction && (blockEntity = level.getBlockEntity(blockPos2)) instanceof PistonMovingBlockEntity && (pistonMovingBlockEntity = (PistonMovingBlockEntity)blockEntity).isExtending() && (pistonMovingBlockEntity.getProgress(0.0f) < 0.5f || level.getGameTime() == pistonMovingBlockEntity.getLastTicked() || ((ServerLevel)level).isHandlingTick())) {
            //设置n
            n = 2;
        }
        //添加方块事件
        level.blockEvent(blockPos, this, n, direction.get3DDataValue());
    }
}

//检查是否被激活
boolean getNeighborSignal(level, blockPos, direction) {
    //朝各个方向检查激活情况
    for (direction2 : Direction.values()) {
        //不检查伸出的方向
        if (direction2 == direction || !level.hasSignal(blockPos.relative(direction2), direction2)) continue;
        return true;
    }
    //如果此处有向上激活的信号则激活(?迷惑操作)
    if (level.hasSignal(blockPos, Direction.DOWN)) {
        return true;
    }
    //检查活塞上方的位置(引发qc特性的上位激活)
    blockPos2 = blockPos.above();
    //朝各个方向检查激活情况
    for (direction3 : Direction.values()) {
        //不检查下方
        if (direction3 == Direction.DOWN || !level.hasSignal(blockPos2.relative(direction3), direction3)) continue;
        return true;
    }
    return false;
}

//检查方块是否能够被活塞移动
//direction:移动方向
//direction2:作用方向
//bl:包含破坏
boolean isPushable(blockState, level, blockPos, direction, bl, direction2) {
    //如果该位置在世界之外,不能推动
    if (blockPos.getY() < 0 || blockPos.getY() > level.getMaxBuildHeight() - 1 || !level.getWorldBorder().isWithinBounds(blockPos)) {
        return false;
    }
    //如果该方块是空气,可以推动
    if (blockState.isAir()) {
        return true;
    }
    //如果该方块是黑曜石或哭泣的黑曜石或重生锚,不能推动
    if (blockState.is(Blocks.OBSIDIAN) || blockState.is(Blocks.CRYING_OBSIDIAN) || blockState.is(Blocks.RESPAWN_ANCHOR)) {
        return false;
    }
    //如果在y0向下,不能推动
    if (direction == Direction.DOWN && blockPos.getY() == 0) {
        return false;
    }
    //如果在世界顶部向上,不能推动
    if (direction == Direction.UP && blockPos.getY() == level.getMaxBuildHeight() - 1) {
        return false;
    }
    //如果该方块是伸出的活塞底座,不能伸出
    if (blockState.is(Blocks.PISTON) || blockState.is(Blocks.STICKY_PISTON)) {
        if (blockState.getValue(EXTENDED).booleanValue()) {
            return false;
        }
    } else {//该方块不是活塞底座
        //如果该方块无法挖掘,不能移动
        if (blockState.getDestroySpeed(level, blockPos) == -1.0f) {
            return false;
        }
        //推动情况
        switch (blockState.getPistonPushReaction()) {
            //阻碍,不能推动
            case BLOCK: {
                return false;
            }
            //破坏,返回bl
            case DESTROY: {
                return bl;
            }
            //仅推动,如果是推动则可以
            case PUSH_ONLY: {
                return direction == direction2;
            }
        }
    }
    //如果是方块实体,不能推动
    return !blockState.getBlock().isEntityBlock();
}