package twilightforest.entity.boss;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import twilightforest.TFAchievementPage;
import twilightforest.TFFeature;
import twilightforest.TFSounds;
import twilightforest.block.BlockTFBossSpawner;
import twilightforest.block.TFBlocks;
import twilightforest.block.enums.SpawnerVariant;
import twilightforest.item.TFItems;
import twilightforest.world.ChunkGeneratorTwilightForest;
import twilightforest.world.TFBiomeProvider;
import twilightforest.world.WorldProviderTwilightForest;


public class EntityTFNaga extends EntityMob implements IEntityMultiPart {
	private static final int TICKS_BEFORE_HEALING = 600;
	private static final int MAX_SEGMENTS = 12;
	private static final int LEASH_X = 46;
	private static final int LEASH_Y = 7;
	private static final int LEASH_Z = 46;

	private int currentSegmentCount = 0; // not including head
	private final float healthPerSegment;
	private final EntityTFNagaSegment[] body = new EntityTFNagaSegment[MAX_SEGMENTS];
	private final AIMovementPattern movementAI = new AIMovementPattern(this);
	private int ticksSinceDamaged = 0;

	public EntityTFNaga(World world) {
		super(world);
		
		this.setSize(1.75f, 3.0f);
		this.stepHeight = 2;

		this.healthPerSegment = getMaxHealth() / 10;
		setSegmentsPerHealth();
        
        this.experienceValue = 217;
		this.ignoreFrustumCheck = true;

		this.goNormal();
	}
	
	private float getMaxHealthPerDifficulty() {
		switch (world.getDifficulty()) {
			case EASY: return 120;
			default:
			case NORMAL: return 200;
			case HARD: return 250;
		}
	}

	@Override
    protected boolean canDespawn()
    {
        return false;
    }

	@Override
	protected void initEntityAI() {
		this.tasks.addTask(1, new EntityAISwimming(this));
		this.tasks.addTask(2, new AIAttack(this));
		this.tasks.addTask(3, new AISmash(this));
		this.tasks.addTask(4, movementAI);
		this.tasks.addTask(8, new EntityAIWander(this, 1) {
			@Override
			public void startExecuting() {
				EntityTFNaga.this.goNormal();
				super.startExecuting();
			}
		});
		this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
		this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, false));
	}

	// Similar to EntityAIAttackMelee but simpler (no pathfinding)
	static class AIAttack extends EntityAIBase {
		private final EntityTFNaga taskOwner;
		private int attackTick = 20;

		AIAttack(EntityTFNaga taskOwner) {
			this.taskOwner = taskOwner;
		}

		@Override
		public boolean shouldExecute() {
			EntityLivingBase target = taskOwner.getAttackTarget();

			return target != null
					&& target.getEntityBoundingBox().maxY > taskOwner.getEntityBoundingBox().minY - 2.5
					&& target.getEntityBoundingBox().minY < taskOwner.getEntityBoundingBox().maxY + 2.5
					&& taskOwner.getDistanceSqToEntity(target) <= 16.0D
					&& taskOwner.getEntitySenses().canSee(target);

		}

		@Override
		public void updateTask() {
			if (attackTick > 0) {
				attackTick--;
			}
		}

		@Override
		public void resetTask() {
			attackTick = 20;
		}

		@Override
		public void startExecuting() {
			taskOwner.attackEntityAsMob(taskOwner.getAttackTarget());
			attackTick = 20;
		}
	}

	static class AISmash extends EntityAIBase {
		private final EntityTFNaga taskOwner;

		AISmash(EntityTFNaga taskOwner) {
			this.taskOwner = taskOwner;
		}

		@Override
		public boolean shouldExecute() {
			return taskOwner.getAttackTarget() != null && taskOwner.isCollidedHorizontally;
		}

		@Override
		public void startExecuting() {
			// NAGA SMASH!
			AxisAlignedBB bb = taskOwner.getEntityBoundingBox();
			int minx = MathHelper.floor(bb.minX - 0.5D);
			int miny = MathHelper.floor(bb.minY + 1.01D);
			int minz = MathHelper.floor(bb.minZ - 0.5D);
			int maxx = MathHelper.floor(bb.maxX + 0.5D);
			int maxy = MathHelper.floor(bb.maxY + 0.001D);
			int maxz = MathHelper.floor(bb.maxZ + 0.5D);
			if(taskOwner.getWorld().isAreaLoaded(new BlockPos(minx, miny, minz), new BlockPos(maxx, maxy, maxz)))
			{
				for(int dx = minx; dx <= maxx; dx++)
				{
					for(int dy = miny; dy <= maxy; dy++)
					{
						for(int dz = minz; dz <= maxz; dz++)
						{
							// todo limit what can be broken
							taskOwner.getWorld().destroyBlock(new BlockPos(dx, dy, dz), true);
						}
					}
				}
			}
		}
	}

	enum MovementState {
		INTIMIDATE,
		CRUMBLE,
		CHARGE,
		CIRCLE
	}

	static class AIMovementPattern extends EntityAIBase {
		private final EntityTFNaga taskOwner;
		private MovementState movementState;
		private int stateCounter;
		private boolean clockwise;

		AIMovementPattern(EntityTFNaga taskOwner) {
			this.taskOwner = taskOwner;
			setMutexBits(3);
			resetTask();
		}

		@Override
		public boolean shouldExecute() {
			return taskOwner.getAttackTarget() != null;
		}

		@Override
		public void resetTask() {
			movementState = MovementState.CIRCLE;
			stateCounter = 15;
			clockwise = false;
		}

		@Override
		public void updateTask() {
			if (!taskOwner.getNavigator().noPath()) {
				// If we still have an uncompleted path don't run yet
				// This isn't in shouldExecute/continueExecuting because we don't want to reset the task
				// todo 1.10 there's a better way to do this I think
				return;
			}

			switch (movementState) {
				case INTIMIDATE: {
					taskOwner.getNavigator().clearPathEntity();
					taskOwner.faceEntity(taskOwner.getAttackTarget(), 30F, 30F);
					taskOwner.moveForward = 0.1f;
					break;
				}
				case CRUMBLE: {
					taskOwner.getNavigator().clearPathEntity();
					taskOwner.crumbleBelowTarget(2);
					taskOwner.crumbleBelowTarget(3);
					break;
				}
				case CHARGE: {
					Vec3d tpoint = taskOwner.findCirclePoint(clockwise, 14, Math.PI);
					taskOwner.getNavigator().tryMoveToXYZ(tpoint.xCoord, tpoint.yCoord, tpoint.zCoord, 1); // todo 1.10 check speed
					break;
				}
				case CIRCLE: {
					// normal radius is 13
					double radius = stateCounter % 2 == 0 ? 12.0 : 14.0;
					double rotation = 1; // in radians

					// hook out slightly before circling
					if (stateCounter > 1 && stateCounter < 3) {
						radius = 16;
					}

					// head almost straight at the player at the end
					if (stateCounter == 1) {
						rotation = 0.1;
					}

					Vec3d tpoint = taskOwner.findCirclePoint(clockwise, radius, rotation);
					taskOwner.getNavigator().tryMoveToXYZ(tpoint.xCoord, tpoint.yCoord, tpoint.zCoord, 1); // todo 1.10 check speed
					break;
				}
			}

			stateCounter--;
			if (stateCounter <= 0) {
				transitionState();
			}
		}

		private void transitionState() {
			switch (movementState) {
				case INTIMIDATE: {
					clockwise = !clockwise;

					if (taskOwner.getAttackTarget().getEntityBoundingBox().minY > taskOwner.getEntityBoundingBox().maxY) {
						doCrumblePlayer();
					} else {
						doCharge();
					}

					break;
				}
				case CRUMBLE: doCharge(); break;
				case CHARGE: doCircle(); break;
				case CIRCLE: doIntimidate(); break;
			}
		}

		private void doCircle() {
			movementState = MovementState.CIRCLE;
			stateCounter += 10 + taskOwner.rand.nextInt(10);
			taskOwner.goNormal();
		}

		private void doCrumblePlayer() {
			movementState = MovementState.CRUMBLE;
			stateCounter = 20 + taskOwner.rand.nextInt(20);
			taskOwner.goSlow();
		}

		/**
		 * Charge the player.  Although the count is 4, we actually charge only 3 times.
		 */
		private void doCharge() {
			movementState = MovementState.CHARGE;
			stateCounter = 4;
			taskOwner.goFast();
		}

		private void doIntimidate() {
			movementState = MovementState.INTIMIDATE;
			stateCounter += 15 + taskOwner.rand.nextInt(10);
			taskOwner.goSlow();
		}
	}

	@Override
    protected void applyEntityAttributes()
    {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(getMaxHealthPerDifficulty());
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(2.0D);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
    }
	
	/**
	 * Determine how many segments, from 2-12, the naga should have, dependent on its current health
	 */
	private void setSegmentsPerHealth()
	{
		int oldSegments = this.currentSegmentCount;
		int newSegments = MathHelper.clamp((int) ((this.getHealth() / healthPerSegment) + (getHealth() > 0 ? 2 : 0)), 0, MAX_SEGMENTS);

		if (newSegments != oldSegments) {
			if (newSegments < oldSegments) {
				for (int i = newSegments; i < oldSegments; i++) {
					if (body[i] != null) {
						body[i].selfDestruct();
						body[i] = null;
					}
				}
			} else {
				this.spawnBodySegments();
			}
		}

		this.currentSegmentCount = newSegments;
	}
	
    @Override
    public boolean canTriggerWalking()
    {
        return false;
    }
    
    @Override
	public boolean isInLava()
    {
        return false;
    }

    @Override
	public void onUpdate() {
		despawnIfPeaceful();
		
		if (deathTime > 0) {
            for(int k = 0; k < 5; k++)
            {
                double d = rand.nextGaussian() * 0.02D;
                double d1 = rand.nextGaussian() * 0.02D;
                double d2 = rand.nextGaussian() * 0.02D;
                EnumParticleTypes explosionType = rand.nextBoolean() ?  EnumParticleTypes.EXPLOSION_HUGE : EnumParticleTypes.EXPLOSION_NORMAL;
                
                world.spawnParticle(explosionType, (posX + rand.nextFloat() * width * 2.0F) - width, posY + rand.nextFloat() * height, (posZ + rand.nextFloat() * width * 2.0F) - width, d, d1, d2);
            }
		}
		
		// update health
        this.ticksSinceDamaged++;
        
        if (!this.world.isRemote && this.ticksSinceDamaged > TICKS_BEFORE_HEALING && this.ticksSinceDamaged % 20 == 0)
        {
        	this.heal(1);
        }
        
    	setSegmentsPerHealth();

		super.onUpdate();
		
		moveSegments();
	}

    @Override
    protected void updateAITasks()
    {
        super.updateAITasks();

        if (getAttackTarget() != null &&
				(getDistanceSqToEntity(getAttackTarget()) > 80 * 80 || !this.isEntityWithinHomeArea(getAttackTarget())))
        {
        	setAttackTarget(null);
        }

        // if we are very close to the path point, go to the next point, unless the path is finished
		// TODO 1.10 this runs after the path navigator runs, is that okay?
		double d = width * 4.0F;
		Vec3d vec3d = hasPath() ? getNavigator().getPath().getPosition(this) : null;

		while (vec3d != null && vec3d.squareDistanceTo(posX, vec3d.yCoord, posZ) < d * d) {
			getNavigator().getPath().incrementPathIndex();

			if (getNavigator().getPath().isFinished()) {
				vec3d = null;
			} else {
				vec3d = getNavigator().getPath().getPosition(this);
			}
		}
	}

    static class NagaMoveHelper extends EntityMoveHelper {
		public NagaMoveHelper(EntityLiving naga) {
			super(naga);
		}

		@Override
		public void onUpdateMoveHelper() {
			if (action == Action.MOVE_TO) {
				// [VanillaCopy]? Like superclass. TODO recheck
				this.action = EntityMoveHelper.Action.WAIT;
				double d0 = this.posX - this.entity.posX;
				double d1 = this.posZ - this.entity.posZ;
				double d2 = this.posY - this.entity.posY;
				double d3 = d0 * d0 + d2 * d2 + d1 * d1;

				if (d3 < 2.500000277905201E-7D)
				{
					this.entity.setMoveForward(0.0F);
					return;
				}

				float f9 = (float)(MathHelper.atan2(d1, d0) * (180D / Math.PI)) - 90.0F;
				this.entity.rotationYaw = this.limitAngle(this.entity.rotationYaw, f9, 30.0F); // TF - 90 -> 30
				this.entity.setAIMoveSpeed((float)(this.speed * this.entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue()));

				// TF - old lines. todo are either still needed?
				entity.setMoveForward(((EntityTFNaga) entity).getMoveSpeed());
				entity.setAIMoveSpeed(0.5f);

				// TF - slither!
				if (d3 > 4 && ((EntityTFNaga) entity).movementAI.movementState != MovementState.CHARGE) {
					this.entity.moveStrafing = MathHelper.cos(this.entity.ticksExisted * 0.3F) * ((EntityTFNaga) this.entity).getMoveSpeed() * 0.6F;
				}

				if (d2 > (double)this.entity.stepHeight && d0 * d0 + d1 * d1 < (double)Math.max(1.0F, this.entity.width))
				{
					this.entity.getJumpHelper().setJumping();
				}
			} else {
				super.onUpdateMoveHelper();
			}
		}
	}
    
    private float getMoveSpeed() {
		return 0.5F;
	}

	@Override
    protected SoundEvent getAmbientSound()
    {
        return rand.nextInt(3) != 0 ? TFSounds.NAGA_HISS : TFSounds.NAGA_RATTLE;
    }
 
	@Override
    protected SoundEvent getHurtSound()
    {
		return TFSounds.NAGA_HURT;
    }

	@Override
    protected SoundEvent getDeathSound()
    {
		return TFSounds.NAGA_HURT;
    }

    private void crumbleBelowTarget(int range) {
		int floor = (int) getEntityBoundingBox().minY;
		int targetY = (int) getAttackTarget().getEntityBoundingBox().minY;
		
		if (targetY > floor)
		{
			int dx = (int) getAttackTarget().posX + rand.nextInt(range) - rand.nextInt(range);
			int dz = (int) getAttackTarget().posZ + rand.nextInt(range) - rand.nextInt(range);
			int dy = targetY - rand.nextInt(range) + rand.nextInt(range > 1 ? range - 1 : range);
			
			if (dy <= floor) {
				dy = targetY;
			}

//			System.out.println("Crumbling block at " + dx + ", " + dy + ", " + dz);
			BlockPos pos = new BlockPos(dx, dy, dz);

			if (!world.isAirBlock(pos))
			{
				// todo limit what can be broken
				world.destroyBlock(pos, true);
				
				// sparkle!!
	            for(int k = 0; k < 20; k++)
	            {
	                double d = rand.nextGaussian() * 0.02D;
	                double d1 = rand.nextGaussian() * 0.02D;
	                double d2 = rand.nextGaussian() * 0.02D;
	                
	                world.spawnParticle(EnumParticleTypes.CRIT, (posX + rand.nextFloat() * width * 2.0F) - width, posY + rand.nextFloat() * height, (posZ + rand.nextFloat() * width * 2.0F) - width, d, d1, d2);
	            }
			}
		}
	}

    // todo 1.10: these setAIMoveSpeeds likely should be attribute changes, only move helper should care about AIMoveSpeed

    /**
     * Sets the naga to move slowly, such as when he is intimidating the player
     */
	private void goSlow() {
//		moveForward = 0f;
		moveStrafing = 0;
		this.setAIMoveSpeed(0.1f);
    }

    /**
     * Normal speed, like when he is circling
     */
	private void goNormal() {
		this.setAIMoveSpeed(0.6F);
	}

	/**
     * Fast, like when he is charging
     */
	private void goFast() {
		this.setAIMoveSpeed(1.0F);
	}

    @Override
	public boolean canBePushed() {
		return false;
	}

	/**
     * Finds a point that allows us to circle the target clockwise.
     */
	private Vec3d findCirclePoint(boolean clockwise, double radius, double rotation)
    {
    	EntityLivingBase toCircle = getAttackTarget();

    	// compute angle
        double vecx = posX - toCircle.posX;
        double vecz = posZ - toCircle.posZ;
        float rangle = (float)(Math.atan2(vecz, vecx));

        // add a little, so he circles (clockwise)
        rangle += clockwise ? rotation : -rotation;

        // figure out where we're headed from the target angle
        double dx = MathHelper.cos(rangle) * radius;
        double dz = MathHelper.sin(rangle) * radius;
        
        double dy = Math.min(getEntityBoundingBox().minY, toCircle.posY);

        // add that to the target entity's position, and we have our destination
    	return new Vec3d(toCircle.posX + dx, dy, toCircle.posZ + dz);
    }

	@Override
	public boolean attackEntityFrom(DamageSource damagesource, float i)
    {
    	// reject damage from outside of our home radius
        if (damagesource.getSourceOfDamage() != null && !this.isEntityWithinHomeArea(damagesource.getSourceOfDamage())
				|| damagesource.getEntity() != null && !this.isEntityWithinHomeArea(damagesource.getEntity()))
        {
			return false;
		}

    	if (super.attackEntityFrom(damagesource, i))
    	{
    		setSegmentsPerHealth();
    		this.ticksSinceDamaged = 0;
    		return true;
    	} else {
    		return false;
    	}
    }

    @Override
	public boolean attackEntityAsMob(Entity toAttack) {
		boolean result = super.attackEntityAsMob(toAttack);

		if (result && getMoveSpeed() > 0.8) {
			// charging, apply extra pushback
			toAttack.addVelocity(-MathHelper.sin((rotationYaw * 3.141593F) / 180F) * 1.0F, 0.10000000000000001D, MathHelper.cos((rotationYaw * 3.141593F) / 180F) * 1.0F);
		}

		return result;
	}

    @Override
	public float getBlockPathWeight(BlockPos pos)
    {
		if (!this.isWithinHomeDistanceFromPosition(pos))
		{
			return Float.MIN_VALUE;
		}
		else
		{
			return 0.0F;
		}
    }

    @Override
	protected Item getDropItem()
    {
        return TFItems.nagaScale;
    }

    @Override
    protected void dropFewItems(boolean flag, int z) {
    	Item i = getDropItem();
    	if(i != null)
    	{
    		int j = 6 + rand.nextInt(6);
    		for(int k = 0; k < j; k++)
    		{
    			this.dropItem(i, 1);
    		}

    	}
    	
        // trophy
        this.entityDropItem(new ItemStack(TFItems.trophy, 1, 1), 0);
    }

	private void despawnIfPeaceful() {
        if(!world.isRemote && world.getDifficulty() == EnumDifficulty.PEACEFUL) {
			if (hasHome()) {
                BlockPos home = this.getHomePosition();
                world.setBlockState(home, TFBlocks.bossSpawner.getDefaultState().withProperty(BlockTFBossSpawner.VARIANT, SpawnerVariant.NAGA));
            }

			setDead();
		}
	}

	@Override
	public boolean isWithinHomeDistanceFromPosition(BlockPos pos)
    {
		if (this.getMaximumHomeDistance() == -1)
		{
			return true;
		}
		else
		{
			int distX = Math.abs(this.getHomePosition().getX() - pos.getX());
			int distY = Math.abs(this.getHomePosition().getY() - pos.getY());
			int distZ = Math.abs(this.getHomePosition().getZ() - pos.getZ());

			return distX <= LEASH_X && distY <= LEASH_Y && distZ <= LEASH_Z;
		}
    }

	private boolean isEntityWithinHomeArea(Entity entity)
	{
		return isWithinHomeDistanceFromPosition(new BlockPos(entity));
	}

	private void spawnBodySegments()
	{
		if (!world.isRemote)
		{
			for (int i = 0; i < currentSegmentCount; i++)
			{
				if (body[i] == null || body[i].isDead)
				{
					body[i] = new EntityTFNagaSegment(this, i);
					body[i].setLocationAndAngles(posX + 0.1 * i, posY + 0.5D, posZ + 0.1 * i, rand.nextFloat() * 360F, 0.0F);
					world.spawnEntity(body[i]);
				}
			}
		}
	}
	
	/**
	 * Sets the heading (ha ha) of the body segments
	 */
	private void moveSegments() {
		for (int i = 0; i < this.currentSegmentCount; i++)
		{
			Entity leader = i == 0 ? this : this.body[i - 1];
			double followX = leader.posX;
			double followY = leader.posY;
			double followZ = leader.posZ;

			// also weight the position so that the segments straighten out a little bit, and the front ones straighten more
	    	float angle = (((leader.rotationYaw + 180) * 3.141593F) / 180F);

			
			double straightenForce = 0.05D + (1.0 / (float)(i + 1)) * 0.5D;
			
	    	double idealX = -MathHelper.sin(angle) * straightenForce;
	    	double idealZ = MathHelper.cos(angle) * straightenForce;
			
			
			Vec3d diff = new Vec3d(body[i].posX - followX, body[i].posY - followY, body[i].posZ - followZ);
			diff = diff.normalize();

			// weight so segments drift towards their ideal position
			diff = diff.addVector(idealX, 0, idealZ).normalize();

			double f = 2.0D;

            double destX = followX + f * diff.xCoord;
            double destY = followY + f * diff.yCoord;
            double destZ = followZ + f * diff.zCoord;

            body[i].setPosition(destX, destY, destZ);
            
            body[i].motionX = f * diff.xCoord;
            body[i].motionY = f * diff.yCoord;
            body[i].motionZ = f * diff.zCoord;
            
            double distance = (double)MathHelper.sqrt(diff.xCoord * diff.xCoord + diff.zCoord * diff.zCoord);
            
            if (i == 0)
            {
				diff = diff.addVector(0, -0.15, 0);
            }
            
            body[i].setRotation((float) (Math.atan2(diff.zCoord, diff.xCoord) * 180.0D / Math.PI) + 90.0F, -(float)(Math.atan2(diff.yCoord, distance) * 180.0D / Math.PI));
		}
	}
	
    @Override
	public void writeEntityToNBT(NBTTagCompound nbttagcompound)
    {
    	if (hasHome()) {
			BlockPos home = this.getHomePosition();
			nbttagcompound.setTag("Home", new NBTTagIntArray(new int[] { home.getX(), home.getY(), home.getZ() }));
		}

        super.writeEntityToNBT(nbttagcompound);
    }

    @Override
	public void readEntityFromNBT(NBTTagCompound nbttagcompound)
    {
        super.readEntityFromNBT(nbttagcompound);

        if (nbttagcompound.hasKey("Home", Constants.NBT.TAG_INT_ARRAY)) {
            int[] home = nbttagcompound.getIntArray("Home");
            this.setHomePosAndDistance(new BlockPos(home[0], home[1], home[2]), 20);
        } else {
        	this.detachHome();
		}

        setSegmentsPerHealth();
    }

	@Override
	public void onDeath(DamageSource par1DamageSource) {
		super.onDeath(par1DamageSource);
		if (par1DamageSource.getEntity() instanceof EntityPlayer) {
			((EntityPlayer)par1DamageSource.getEntity()).addStat(TFAchievementPage.twilightHunter);
			((EntityPlayer)par1DamageSource.getEntity()).addStat(TFAchievementPage.twilightKillNaga);
		}
		
		// mark the courtyard as defeated
		if (!world.isRemote && world.provider instanceof WorldProviderTwilightForest) {
			int dx = MathHelper.floor(this.posX);
			int dy = MathHelper.floor(this.posY);
			int dz = MathHelper.floor(this.posZ);
			
			ChunkGeneratorTwilightForest chunkProvider = ((WorldProviderTwilightForest)world.provider).getChunkProvider();
			TFFeature nearbyFeature = ((TFBiomeProvider)world.provider.getBiomeProvider()).getFeatureAt(dx, dz, world);
			
			if (nearbyFeature == TFFeature.nagaCourtyard) {
				chunkProvider.setStructureConquered(dx, dy, dz, true);
			}
		}

	}

	@Override
	public World getWorld() {
		return this.world;
	}

	@Override
	public boolean attackEntityFromPart(EntityDragonPart entitydragonpart, DamageSource damagesource, float i) {
		return false;
	}

    @Override
    public Entity[] getParts()
    {
        return body;
    }
}
