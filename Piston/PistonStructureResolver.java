//分析移动结构
boolean resolve() {
    //清空列表
    toPush.clear();
    toDestroy.clear();
    //如果起始点不能移动
    blockState = level.getBlockState(startPos);
    if (!PistonBaseBlock.isPushable(blockState, level, startPos, pushDirection, false, pistonDirection)) {
        //推动可破坏
        if (extending && blockState.getPistonPushReaction() == PushReaction.DESTROY) {
            //添加至破坏方块列表中
            toDestroy.add(startPos);
            //移动完成
            return true;
        }
        //移动失败
        return false;
    }
    //无法添加直线方块结构，则移动失败
    if (!addBlockLine(startPos, pushDirection)) return false;
    //对于每个推动方块列表中的方块
    for (int i = 0; i < toPush.size(); ++i) {
        blockPos = toPush.get(i);
        //如果是可粘方块，则尝试添加分支
        if (PistonStructureResolver.isSticky(level.getBlockState(blockPos).getBlock())) continue;
            if(!addBranchingBlocks(blockPos)) return false;
    }
    return true;
}

//是否为可粘方块
boolean isSticky(block) {
    //粘液块或蜂蜜块
    return block == Blocks.SLIME_BLOCK || block == Blocks.HONEY_BLOCK;
}

//能否互粘
boolean canStickToEachOther(block, block2) {
    //粘液块与蜂蜜块不互粘
    if (block == Blocks.HONEY_BLOCK && block2 == Blocks.SLIME_BLOCK) return false;
    if (block == Blocks.SLIME_BLOCK && block2 == Blocks.HONEY_BLOCK) return false;
    //其中有一个是可粘方块
    return PistonStructureResolver.isSticky(block) || PistonStructureResolver.isSticky(block2);
}

//添加一列方块
boolean addBlockLine(blockPos, direction) {
    //获取该方块
    blockState = level.getBlockState(blockPos);
    block = blockState.getBlock();
    //如果是空气
    if (blockState.isAir()) return true;
    //该方块不可被移动
    if (!PistonBaseBlock.isPushable(blockState, level, blockPos, pushDirection, false, direction)) return true;
    //为活塞自身的位置
    if (blockPos.equals(pistonPos)) return true;
    //移动方块列表已包含该位置
    if (toPush.contains(blockPos)) return true;
    //方块计数
    pullCount = 1;
    //移动列表+1大于12
    if (pullCount + toPush.size() > 12) return false;
    //该方块可粘
    while (PistonStructureResolver.isSticky(block)) {
        //移动方向往前一个位置
        blockPos2 = blockPos.relative(pushDirection.getOpposite(), pullCount);
        //换为这个方块
        block2 = block;
        blockState = level.getBlockState(blockPos2);
        block = blockState.getBlock();
        //该方块是空气或这两个方块不互粘或该方块或原先那个方块不可推动或该位置为活塞本身，退出循环
        if (blockState.isAir() || !PistonStructureResolver.canStickToEachOther(block2, block) || !PistonBaseBlock.isPushable(blockState, level, blockPos2, pushDirection, false, pushDirection.getOpposite()) || blockPos2.equals(pistonPos)) break;
        //如果加上该方块超过推动上限，结束循环
        if (++pullCount + toPush.size() > 12)
            return false;
    }
    int n3 = 0;
    for (n = n2 - 1; pullCount >= 0; --n) {
        //把方块反向n2-1格添加到移动方块列表中
        toPush.add(blockPos.relative(pushDirection.getOpposite(), n));
        ++n3;
    }
    n = 1;
    do {
        //如果该方块正向n格已存在于方块移动列表中 n4位置
        if ((n4 = toPush.indexOf(blockPos3 = blockPos.relative(pushDirection, n))) > -1) {
            //n3、n4重排列表
            reorderListAtCollision(n3, n4);
            //对于所有方块
            for (int i = 0; i <= n4 + n3; ++i) {
                blockPos4 = toPush.get(i);
                //如果不是可粘方块或成功添加分支方块则继续
                if (!PistonStructureResolver.isSticky(level.getBlockState(blockPos4).getBlock()) || addBranchingBlocks(blockPos4)) continue;
                return false;
            }
            return true;
        }
        blockState = level.getBlockState(blockPos3);
        //是空气，添加完成
        if (blockState.isAir()) return true;
        //不可移动或为活塞自身
        if (!PistonBaseBlock.isPushable(blockState, level, blockPos3, pushDirection, true, pushDirection) || blockPos3.equals(pistonPos)) return false;
        //该方块会被破坏
        if (blockState.getPistonPushReaction() == PushReaction.DESTROY) {
            //添加方块至破坏列表
            toDestroy.add(blockPos3);
            //添加完成
            return true;
        }
        //推动列表大于12，添加失败
        if (toPush.size() >= 12) return false;
        //添加该方块
        toPush.add(blockPos3);
        ++n3;
        ++n;
    } while (true);
}

//重排列表
void reorderListAtCollision(n, n2) {
    //[0, n2], [size-n, n2] [n2, size-n]
    //交换后n个的位置与中间部分
    arrayList.addAll(toPush.subList(0, n2));
    arrayList2.addAll(toPush.subList(toPush.size() - n, toPush.size()));
    arrayList3.addAll(toPush.subList(n2, toPush.size() - n));
    toPush.clear();
    toPush.addAll(arrayList);
    toPush.addAll(arrayList2);
    toPush.addAll(arrayList3);
}

//添加分支方块
boolean addBranchingBlocks(blockPos) {
    blockState = level.getBlockState(blockPos);
    //对于每个方向
    for (direction : Direction.values()) {
        //同轴移动或该方块与移动方块方块不互粘或能添加方块列，继续循环
        if (direction.getAxis() == pushDirection.getAxis() || !PistonStructureResolver.canStickToEachOther((blockState2 = level.getBlockState(blockPos2 = blockPos.relative(direction))).getBlock(), blockState.getBlock())) continue;
        if(!addBlockLine(blockPos2, direction)) return false;
    }
    return true;
}