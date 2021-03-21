//瞬间到位，将b36方块实体变回普通方块
void finalTick() {
    //存在世界且进度未满或客户端运算
    if (level != null && (progressO < 1.0f || level.isClientSide)) {
        //将进度设为完成
        progressO = progress = 1.0f;
        //移除方块实体
        level.removeBlockEntity(worldPosition);
        setRemoved();
        //如果此处为b36方块
        if (level.getBlockState(worldPosition).is(Blocks.MOVING_PISTON)) {
            //是否为产生移动的活塞？是则变为空气，否则在形状更新后到位
            blockState = isSourcePiston ? Blocks.AIR.defaultBlockState() : Block.updateFromNeighbourShapes(movedState, level, worldPosition);
            //设置方块 形状更新 寻路更新 方块更新
            level.setBlock(worldPosition, blockState, 3);
            //自身受到方块更新
            level.neighborChanged(worldPosition, blockState.getBlock(), worldPosition);
        }
    }
}

//运算方块实体
void tick() {
    //记录当前gt
    lastTicked = level.getGameTime();
    progressO = progress;
    //如果已经推动完成
    if (progressO >= 1.0f) {
        //如果是客户端且deathTicks没有超过5
        if (level.isClientSide && deathTicks < 5) {
            //增加deathTicks计数并返回
            ++deathTicks;
            return;
        }
        //移除方块实体
        level.removeBlockEntity(worldPosition);
        setRemoved();
        //如果存在方块状态且该方块为b36
        if (movedState != null && level.getBlockState(worldPosition).is(Blocks.MOVING_PISTON)) {
            //给出形状更新
            blockState = Block.updateFromNeighbourShapes(movedState, level, worldPosition);
            //如果为空气
            if (blockState.isAir()) {
                //设置为对应的方块
                level.setBlock(worldPosition, movedState, 84);
                //更新或破坏该方块
                Block.updateOrDestroy(movedState, blockState, level, worldPosition, 3);
            } else {//不为空气
                //如果为含水方块
                if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED).booleanValue()) {
                    //移除含水方块中的水
                    blockState = (BlockState)blockState.setValue(BlockStateProperties.WATERLOGGED, false);
                }
                //设置方块
                level.setBlock(worldPosition, blockState, 67);
                //给出方块更新
                level.neighborChanged(worldPosition, blockState.getBlock(), worldPosition);
            }
        }
        return;
    }//推动未完成
    //推动进度+0.5
    float f = progress + 0.5f;
    //移动碰撞影响的实体
    moveCollidedEntities(f);
    //移动粘住的实体
    moveStuckEntities(f);
    progress = f;
    //如果推动进度>=1,取为1
    if (progress >= 1.0f) {
        progress = 1.0f;
    }
}

VoxelShape getCollisionShape(blockGetter, blockPos) {
    //如果是正在收回的源活塞b36，则使用伸出活塞底座的的碰撞体积
    voxelShape = !extending && isSourcePiston
            ? ((BlockState) movedState.setValue(PistonBaseBlock.EXTENDED, true)).getCollisionShape(blockGetter,
                    blockPos)
            : Shapes.empty();
    direction = NOCLIP.get();
    if ((double) progress < 1.0 && direction == getMovementDirection()) {
        return voxelShape;
    }
    //如果是源活塞，则使用活塞头的碰撞体积，否则使用被移动方块的碰撞体积
     blockState = isSourcePiston()
            ? (BlockState) ((BlockState) Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING,
                    direction)).setValue(PistonHeadBlock.SHORT, extending != 1.0f - progress < 0.25f)
            : movedState;
    //根据进度移动碰撞体积
    f = getExtendedProgress(progress);
    d = (float) direction.getStepX() * f;
    d2 = (float) direction.getStepY() * f;
    d3 = (float) direction.getStepZ() * f;
    //结合上述两个碰撞体积
    return Shapes.or(voxelShape, blockState.getCollisionShape(blockGetter, blockPos).move(d, d2, d3));
}