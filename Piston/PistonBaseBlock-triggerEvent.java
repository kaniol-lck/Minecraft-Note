//执行方块事件
//n = 0, 伸出
//n = 1, 收回
//n = 2, 瞬推收回
boolean triggerEvent(blockState, level, blockPos, n, n2) {
    //活塞方向
    direction = blockState.getValue(FACING);
    //非客户端
    if (!level.isClientSide) {
        //检查激活情况
        bl = getNeighborSignal(level, blockPos, direction);
        //如果激活,而方块事件为收回或瞬推收回
        if (bl && (n == 1 || n == 2)) {
            //将活塞设置成伸出状态
            //flags:0b00000010 寻路更新
            level.setBlock(blockPos, blockState.setValue(EXTENDED, true), 2);
            return false;
        }
        //如果没有激活,而方块事件为伸出,方块事件执行失败
        if (!bl && n == 0) return false;
    }
    //伸出事件
    if (n == 0) {
        //移动前方方块，若无法移动，则方块事件执行失败
        if (!moveBlocks(level, blockPos, direction, true)) return false;
        //将活塞设置为伸出状态
        //flags:0b01000011 放置移除更新 寻路更新 方块更新
        level.setBlock(blockPos, blockState.setValue(EXTENDED, true), 67);
        //播放活塞伸出的声音
        level.playSound(null, blockPos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.25f + 0.6f);
        //方块事件执行成功
        return true;
    } else {//收回事件及瞬推收回事件
        //其他方块事件(?),返回
        if (n != 1 && n != 2) return true;
        //活塞头位置的方块实体
        blockEntity = level.getBlockEntity(blockPos.relative(direction));
        //如果是b36,则使其瞬间到位
        if (blockEntity instanceof PistonMovingBlockEntity)((PistonMovingBlockEntity)blockEntity).finalTick();
        //将活塞设置成b36
        blockState2 = (Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, direction)).setValue(MovingPistonBlock.TYPE, isSticky ? PistonType.STICKY : PistonType.DEFAULT);
        //flags:0b00010100 无形状更新 寻路更新
        level.setBlock(blockPos, blockState2, 20);
        //设置成b36方块实体
        level.setBlockEntity(blockPos, MovingPistonBlock.newMovingBlockEntity(defaultBlockState().setValue(FACING, Direction.from3DDataValue(n2 & 7)), direction, false, true));
        //发出方块更新
        level.blockUpdated(blockPos, blockState2.getBlock());
        //发出形状更新
        blockState2.updateNeighbourShapes(level, blockPos, 2);
        //如果是粘性活塞
        if (isSticky) {
            //伸出方向一格之外的位置
            blockPos2 = blockPos.offset(direction.getStepX() * 2, direction.getStepY() * 2, direction.getStepZ() * 2);
            blockState3 = level.getBlockState(blockPos2);
            bl = false;
            //如果这个方块是b36且且与活塞同向推出
            if (blockState3.is(Blocks.MOVING_PISTON) && (blockEntity2 = level.getBlockEntity(blockPos2)) instanceof PistonMovingBlockEntity && (pistonMovingBlockEntity = (PistonMovingBlockEntity)blockEntity2).getDirection() == direction && pistonMovingBlockEntity.isExtending()) {
                //让该方块瞬间到位
                pistonMovingBlockEntity.finalTick();
                bl = true;
            }
            //如果不是上面的情况
            if (!bl) {
                //如果是收回事件
                //且该方块不是空气
                //且活塞可以移动前方一格的方块
                //且该方块能被正常移动或该方块是活塞/粘性活塞
                if (n == 1 && !blockState3.isAir() && PistonBaseBlock.isPushable(blockState3, level, blockPos2, direction.getOpposite(), false, direction) && (blockState3.getPistonPushReaction() == PushReaction.NORMAL || blockState3.is(Blocks.PISTON) || blockState3.is(Blocks.STICKY_PISTON))) {
                    //移动前方方块
                    moveBlocks(level, blockPos, direction, false);
                } else {//前方一格的方块不能被拉动
                    //删除活塞臂位置的方块
                    level.removeBlock(blockPos.relative(direction), false);
                }
            }
        } else {//是普通活塞
            //删除活塞臂位置的方块
            level.removeBlock(blockPos.relative(direction), false);
        }
        //播放活塞收回的声音
        level.playSound(null, blockPos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.15f + 0.6f);
    }
    return true;
}

//bl = 是否为伸出
boolean moveBlocks(level, blockPos, direction, bl) {
    //活塞头的位置
    blockPos2 = blockPos.relative(direction);
    //如果是收回且活塞头的位置是活塞头
    if (!bl && level.getBlockState(blockPos2).is(Blocks.PISTON_HEAD)) {
        //将活塞头的位置设为空气
        //flags:0b00010100 无形状更新 寻路更新 客户端
        level.setBlock(blockPos2, Blocks.AIR.defaultBlockState(), 20);
    }
    //分析移动结构,如果不能移动就不移动
    if (!(pistonStructureResolver = new PistonStructureResolver(level, blockPos, direction, bl)).resolve()) {
        return false;
    }
    hashMap = Maps.newHashMap();
    //创建移动方块列表
    pushList = pistonStructureResolver.getToPush();
    arrayList = Lists.newArrayList();
    //移动方块列表中的所有方块
    for (int i = 0; i < pushList.size(); ++i) {
        arrblockState = pushList.get(i);
        object2 = level.getBlockState((BlockPos)arrblockState);
        //添加到列表中
        arrayList.add(object2);
        //添加到哈希表中
        hashMap.put(arrblockState, object2);
    }
    //创建破坏方块列表
    destroyList = pistonStructureResolver.getToDestroy();
    //创建一个移动方块+破坏方块的列表
    arrblockState = new BlockState[pushList.size() + destroyList.size()];
    //确定移动方向
    moveDirection = bl ? direction : direction.getOpposite();
    int n3 = 0;
    //破坏方块列表 从后到前
    for (n = destroyList.size() - 1; n >= 0; --n) {
        object3 = destroyList.get(n);
        object22 = level.getBlockState((BlockPos)object3);
        object = object22.getBlock().isEntityBlock() ? level.getBlockEntity((BlockPos)object3) : null;
        //掉落方块实体的物品
        PistonBaseBlock.dropResources(object22, level, (BlockPos)object3, (BlockEntity)object);
        //将该方块设置成空气
        //flags:0b00010010 无形状更新 寻路更新
        level.setBlock((BlockPos)object3, Blocks.AIR.defaultBlockState(), 18);
        //添加到列表中
        arrblockState[n3++] = object22;
    }
    //移动方块列表 从后到前
    for (n = pushList.size() - 1; n >= 0; --n) {
        object3 = pushList.get(n);
        blockState = level.getBlockState((BlockPos)object3);
        //移动目的地的方块
        object3 = ((BlockPos)object3).relative(oveDirection);
        //删除哈希表中方块
        hashMap.remove(object3);
        //将该方块设置成b36
        //flags: 0b01101000 放置移除更新
        level.setBlock((BlockPos)object3, Blocks.MOVING_PISTON.defaultBlockState().setValue(FACING, direction), 68);
        //将该方块设置成b36方块实体
        level.setBlockEntity((BlockPos)object3, MovingPistonBlock.newMovingBlockEntity(arrayList.get(n), direction, bl, false));
        //添加到列表中
        arrblockState[n3++] = blockState;
    }
    //如果是伸出
    if (bl) {
        //准备活塞头b36
        pistonType = isSticky ? PistonType.STICKY : PistonType.DEFAULT;
        object3 = (Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, direction)).setValue(PistonHeadBlock.TYPE, pistonType);
        blockState = (Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, direction)).setValue(MovingPistonBlock.TYPE, isSticky ? PistonType.STICKY : PistonType.DEFAULT);
        //从哈希表中删除
        hashMap.remove(blockPos2);
        //将活塞头的位置设置成b36
        //flags: 0b01101000 放置移除更新
        level.setBlock(blockPos2, blockState, 68);
        //将活塞头的位置设置成b36方块实体
        level.setBlockEntity(blockPos2, MovingPistonBlock.newMovingBlockEntity(object3, direction, true, true));
    }
    blockState = Blocks.AIR.defaultBlockState();
    //对于哈希表中剩下的方块位置
    for (blockPos3 : hashMap.keySet()) {
        //设置成空气
        //flags: 0b01010010 放置移除更新 无形状更新 寻路更新
        level.setBlock(blockPos3, blockState, 82);
    }
    //对于哈希表中的所有方块
    for (entry : hashMap.entrySet()) {
        object = (BlockPos)entry.getKey();
        blockState2 = entry.getValue();
        //红石粉间接位置更新
        blockState2.updateIndirectNeighbourShapes(level, (BlockPos)object, 2);
        //形状更新
        blockState.updateNeighbourShapes(level, (BlockPos)object, 2);
        //红石粉间接位置更新
        blockState.updateIndirectNeighbourShapes(level, (BlockPos)object, 2);
    }
    n3 = 0;
    //对于破坏方块列表中的所有方块
    for (n2 = destroyList.size() - 1; n2 >= 0; --n2) {
        blockState3 = arrblockState[n3++];
        object = destroyList.get(n2);
        //红石粉间接位置更新
        blockState3.updateIndirectNeighbourShapes(level, (BlockPos)object, 2);
        //给出方块更新
        level.updateNeighborsAt((BlockPos)object, blockState3.getBlock());
    }
    //对于移动方块列表中的所有方块
    for (n2 = pushList.size() - 1; n2 >= 0; --n2) {
        //给出方块更新
        level.updateNeighborsAt(pushList.get(n2), arrblockState[n3++].getBlock());
    }
    //如果是伸出
    if (bl) {
        //活塞头给出方块更新
        level.updateNeighborsAt(blockPos2, Blocks.PISTON_HEAD);
    }
    return true;
}
