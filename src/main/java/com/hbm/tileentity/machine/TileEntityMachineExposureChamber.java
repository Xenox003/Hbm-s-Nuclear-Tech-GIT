package com.hbm.tileentity.machine;

import com.hbm.inventory.UpgradeManager;
import com.hbm.inventory.container.ContainerMachineExposureChamber;
import com.hbm.inventory.gui.GUIMachineExposureChamber;
import com.hbm.inventory.recipes.ExposureChamberRecipes;
import com.hbm.inventory.recipes.ExposureChamberRecipes.ExposureChamberRecipe;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energy.IEnergyUser;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineExposureChamber extends TileEntityMachineBase implements IGUIProvider, IEnergyUser {
	
	public long power;
	public static final long maxPower = 1_000_000;
	
	public int progress;
	public static final int processTimeBase = 200;
	public int processTime = processTimeBase;
	public static final int consumptionBase = 10_000;
	public int consumption = consumptionBase;
	public int savedParticles;
	public static final int maxParticles = 8;
	public boolean isOn = false;
	public float rotation;
	public float prevRotation;

	public TileEntityMachineExposureChamber() {
		/*
		 * 0: Particle
		 * 1: Particle internal
		 * 2: Particle container
		 * 3: Ingredient
		 * 4: Output
		 * 5: Battery
		 * 6-7: Upgrades
		 */
		super(8);
	}

	@Override
	public String getName() {
		return "container.exposureChamber";
	}

	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote) {
			
			this.isOn = false;
			this.power = Library.chargeTEFromItems(slots, 5, power, maxPower);
			
			if(worldObj.getTotalWorldTime() % 20 == 0) {
				for(DirPos pos : getConPos()) this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
			
			UpgradeManager.eval(slots, 6, 7);
			int speedLevel = Math.min(UpgradeManager.getLevel(UpgradeType.SPEED), 3);
			int powerLevel = Math.min(UpgradeManager.getLevel(UpgradeType.POWER), 3);
			int overdriveLevel = Math.min(UpgradeManager.getLevel(UpgradeType.OVERDRIVE), 3);
			
			this.consumption = this.consumptionBase;
			
			this.processTime = this.processTimeBase - this.processTimeBase / 4 * speedLevel;
			this.consumption *= (speedLevel / 2 + 1);
			this.processTime *= (powerLevel / 2 + 1);
			this.consumption /= (powerLevel + 1);
			this.processTime /= (overdriveLevel + 1);
			this.consumption *= (overdriveLevel * 2 + 1);
			
			if(slots[1] == null && slots[0] != null && slots[3] != null && this.savedParticles <= 0) {
				ExposureChamberRecipe recipe = this.getRecipe(slots[0], slots[3]);
				
				if(recipe != null) {
					
					ItemStack container = slots[0].getItem().getContainerItem(slots[0]);
					
					boolean canStore = false;
					
					if(container == null) {
						canStore = true;
					} else if(slots[2] == null) {
						slots[2] = container.copy();
						canStore = true;
					} else if(slots[2].getItem() == container.getItem() && slots[2].getItemDamage() == container.getItemDamage() && slots[2].stackSize < slots[2].getMaxStackSize()) {
						slots[2].stackSize++;
						canStore = true;
					}
					
					if(canStore) {
						slots[1] = slots[0].copy();
						slots[1].stackSize = 0;
						this.decrStackSize(0, 1);
						this.savedParticles = this.maxParticles;
					}
				}
			}
			
			if(slots[1] != null && this.savedParticles > 0 && this.power >= this.consumption) {
				ExposureChamberRecipe recipe = this.getRecipe(slots[1], slots[3]);
				
				if(recipe != null && (slots[4] == null || (slots[4].getItem() == recipe.output.getItem() && slots[4].getItemDamage() == recipe.output.getItemDamage() && slots[4].stackSize + recipe.output.stackSize <= slots[4].getMaxStackSize()))) {
					this.progress++;
					this.power -= this.consumption;
					this.isOn = true;
					
					if(this.progress >= this.processTime) {
						this.progress = 0;
						this.savedParticles--;
						this.decrStackSize(3, 1);
						
						if(slots[4] == null) {
							slots[4] = recipe.output.copy();
						} else {
							slots[4].stackSize += recipe.output.stackSize;
						}
					}
					
				} else {
					this.progress = 0;
				}
			} else {
				this.progress = 0;
			}
			
			if(this.savedParticles <= 0) {
				slots[1] = null;
			}
			
			this.networkPackNT(50);
		} else {
			
			this.prevRotation = this.rotation;
			
			if(this.isOn) {
				
				this.rotation += 10D;
				
				if(this.rotation >= 720D) {
					this.rotation -= 720D;
					this.prevRotation -= 720D;
				}
			}
		}
	}
	
	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP).getOpposite();
		return new DirPos[] {
				new DirPos(xCoord + rot.offsetX * 7 + dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 7 + dir.offsetZ * 2, dir),
				new DirPos(xCoord + rot.offsetX * 7 - dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 7 - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 8 + dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 8 + dir.offsetZ * 2, dir),
				new DirPos(xCoord + rot.offsetX * 8 - dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 8 - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 9, yCoord, zCoord + rot.offsetZ * 9, rot)
		};
	}
	
	public ExposureChamberRecipe getRecipe(ItemStack particle, ItemStack ingredient) {
		return ExposureChamberRecipes.getRecipe(particle, ingredient);
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeBoolean(this.isOn);
		buf.writeInt(this.progress);
		buf.writeInt(this.processTime);
		buf.writeInt(this.consumption);
		buf.writeLong(this.power);
		buf.writeByte((byte) this.savedParticles);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		this.isOn = buf.readBoolean();
		this.progress = buf.readInt();
		this.processTime = buf.readInt();
		this.consumption = buf.readInt();
		this.power = buf.readLong();
		this.savedParticles = buf.readByte();
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 8,
					yCoord,
					zCoord - 8,
					xCoord + 9,
					yCoord + 5,
					zCoord + 9
					);
		}
		
		return bb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineExposureChamber(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public GuiScreen provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineExposureChamber(player.inventory, this);
	}
}